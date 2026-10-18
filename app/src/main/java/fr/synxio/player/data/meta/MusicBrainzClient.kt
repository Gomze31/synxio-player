package fr.synxio.player.data.meta

import android.util.Log
import fr.synxio.player.BuildConfig
import fr.synxio.player.core.util.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Une fiche MusicBrainz retenue pour un morceau. */
data class OnlineMatch(
    val metadata: TrackMetadata,
    val releaseId: String?,
    val releaseGroupId: String?,
    /** Score de 0 à 100 : recoupement titre, artiste et durée. */
    val confidence: Int,
)

/**
 * Espace les requêtes vers un même service.
 *
 * MusicBrainz bannit les clients qui dépassent une requête par seconde : la limite est
 * tenue ici, pour tous les appelants à la fois.
 */
class RateLimiter(
    private val minIntervalMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var last = Long.MIN_VALUE / 2

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = last + minIntervalMs - clock()
        if (wait > 0) sleep(wait)
        try {
            block()
        } finally {
            last = clock()
        }
    }
}

/**
 * Recherche de métadonnées sur MusicBrainz et de pochettes sur le Cover Art Archive.
 *
 * Deux services ouverts, sans clé, qui tolèrent les applications à condition d'être
 * identifiées (User-Agent) et de respecter une requête par seconde.
 */
@Singleton
class MusicBrainzClient @Inject constructor(
    private val http: OkHttpClient,
) {
    private val musicBrainzLimiter = RateLimiter(MB_INTERVAL_MS)
    private val coverArtLimiter = RateLimiter(CAA_INTERVAL_MS)

    /**
     * Cherche [title] de [artist] (facultatif). [durationMs] restreint la recherche quand
     * l'artiste est inconnu : sans lui, « Liberté » renverrait cent morceaux homonymes.
     */
    suspend fun search(title: String, artist: String?, durationMs: Long?): Result<OnlineMatch?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = buildQuery(title, artist, durationMs) ?: return@runCatching null
                val url = "$MB_BASE/recording".toHttpUrl().newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("fmt", "json")
                    .addQueryParameter("limit", "8")
                    .build()
                // 503 : MusicBrainz est saturé ou nous trouve trop pressés. On patiente et
                // on réessaie, plutôt que de conclure que le morceau n'existe pas.
                var attempt = 0
                while (true) {
                    val (code, body) = musicBrainzLimiter.run {
                        http.newCall(request(url.toString())).execute().use { response ->
                            response.code to response.body?.string().orEmpty()
                        }
                    }
                    if (code == 503 && attempt < MAX_RETRIES) {
                        attempt++
                        delay(RETRY_DELAY_MS * attempt)
                        continue
                    }
                    if (code !in 200..299) error("MusicBrainz a répondu HTTP $code")
                    return@runCatching bestMatch(parseRecordings(body), title, artist, durationMs)
                }
                @Suppress("UNREACHABLE_CODE") null
            }.onFailure { Log.w(TAG, "Recherche MusicBrainz échouée : ${it.message}") }
        }

    /** Pochette en 500 px : celle de la sortie, sinon celle du groupe de sorties. */
    suspend fun frontCover(releaseId: String?, releaseGroupId: String?): ByteArray? = withContext(Dispatchers.IO) {
        val candidates = listOfNotNull(
            releaseId?.let { "$CAA_BASE/release/$it/front-500" },
            releaseGroupId?.let { "$CAA_BASE/release-group/$it/front-500" },
        )
        for (url in candidates) {
            val bytes = runCatching {
                coverArtLimiter.run {
                    http.newCall(request(url)).execute().use { response ->
                        if (response.isSuccessful) response.body?.bytes() else null
                    }
                }
            }.onFailure { Log.w(TAG, "Cover Art Archive injoignable", it) }.getOrNull()
            if (bytes != null && bytes.size > MIN_IMAGE_BYTES) return@withContext bytes
        }
        null
    }

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .build()

    companion object {
        private const val TAG = "MusicBrainzClient"
        private const val MB_BASE = "https://musicbrainz.org/ws/2"
        private const val CAA_BASE = "https://coverartarchive.org"
        /** Un peu plus d'une seconde : la limite est d'une requête par seconde en moyenne. */
        private const val MB_INTERVAL_MS = 1_250L
        private const val CAA_INTERVAL_MS = 500L
        private const val MIN_IMAGE_BYTES = 1_000
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 3_000L
        /** Seuil de confiance en dessous duquel une fiche est ignorée. */
        const val MIN_CONFIDENCE = 70

        val USER_AGENT = "SynxioPlayer/${BuildConfig.VERSION_NAME} ( https://github.com/${BuildConfig.UPDATE_REPO} )"

        private val JSON = Json { ignoreUnknownKeys = true }
        private val LUCENE_SPECIAL = Regex("""([+\-!(){}\[\]^"~*?:\\/]|&&|\|\|)""")

        internal fun escape(text: String): String = LUCENE_SPECIAL.replace(text) { "\\" + it.value }

        internal fun buildQuery(title: String, artist: String?, durationMs: Long?): String? {
            val t = title.trim().takeIf { it.isNotEmpty() } ?: return null
            return buildString {
                append("recording:\"").append(escape(t)).append('"')
                if (!artist.isNullOrBlank()) {
                    append(" AND artist:\"").append(escape(artist.trim())).append('"')
                } else if (durationMs != null && durationMs > 0) {
                    append(" AND dur:[").append(durationMs - 4_000).append(" TO ").append(durationMs + 4_000).append(']')
                }
            }
        }

        /** Une fiche « recording » de la réponse, avec la sortie la plus représentative. */
        internal data class Recording(
            val title: String,
            val artist: String,
            val lengthMs: Long?,
            val score: Int,
            val firstReleaseYear: Int?,
            val genre: String?,
            val release: Release?,
        )

        internal data class Release(
            val id: String,
            val title: String,
            val artist: String?,
            val groupId: String?,
            val year: Int?,
            val track: Int?,
            val disc: Int?,
            /** Préférence : plus c'est bas, plus la sortie est l'album d'origine. */
            val rank: Int,
        )

        internal fun parseRecordings(body: String): List<Recording> {
            val root = JSON.parseToJsonElement(body).jsonObject
            val recordings = root["recordings"] as? JsonArray ?: return emptyList()
            return recordings.mapNotNull { (it as? JsonObject)?.toRecording() }
        }

        private fun JsonObject.str(key: String): String? = this[key]
            ?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

        private fun JsonObject.credit(): String? = (this["artist-credit"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.joinToString("") { (it.str("name") ?: "") + (it.str("joinphrase") ?: "") }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        private fun JsonObject.toRecording(): Recording? {
            val title = str("title") ?: return null
            val releases = (this["releases"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toRelease() }.orEmpty()
            val genre = (this["tags"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.maxByOrNull { runCatching { it["count"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: 0 }
                ?.str("name")
                ?.replaceFirstChar { it.titlecase() }
            return Recording(
                title = title,
                artist = credit().orEmpty(),
                lengthMs = runCatching { this["length"]?.jsonPrimitive?.longOrNull }.getOrNull(),
                score = runCatching { this["score"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: 0,
                firstReleaseYear = str("first-release-date")?.let(FileTagReader::parseYear),
                genre = genre,
                release = pickRelease(releases),
            )
        }

        private fun JsonObject.toRelease(): Release? {
            val id = str("id") ?: return null
            val group = this["release-group"] as? JsonObject
            val primaryType = group?.str("primary-type")
            val secondary = (group?.get("secondary-types") as? JsonArray)?.mapNotNull {
                runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
            }.orEmpty()
            val medium = (this["media"] as? JsonArray)?.firstOrNull() as? JsonObject
            val track = (medium?.get("track") as? JsonArray)?.firstOrNull() as? JsonObject
            return Release(
                id = id,
                title = str("title") ?: return null,
                artist = credit(),
                groupId = group?.str("id"),
                year = str("date")?.let(FileTagReader::parseYear),
                track = track?.str("number")?.let(FileTagReader::parseNumber),
                disc = runCatching { medium?.get("position")?.jsonPrimitive?.intOrNull }.getOrNull(),
                rank = rankOf(str("status"), primaryType, secondary),
            )
        }

        private fun rankOf(status: String?, primaryType: String?, secondary: List<String>): Int {
            var rank = 0
            if (status != "Official") rank += 4
            if (secondary.any { it.equals("Compilation", true) || it.equals("Live", true) }) rank += 2
            rank += when (primaryType) {
                "Album" -> 0
                "Single", "EP" -> 1
                else -> 3
            }
            return rank
        }

        /** L'album d'origine plutôt qu'une compilation « Hits 2024 » ou un bootleg. */
        private fun pickRelease(releases: List<Release>): Release? =
            releases.minWithOrNull(compareBy<Release>({ it.rank }, { it.year ?: 9999 }))

        /**
         * Retient la fiche la plus plausible, ou aucune.
         *
         * Le score de MusicBrainz ne mesure que la ressemblance du texte : on recoupe avec
         * le titre et l'artiste normalisés, et surtout la durée, qui départage deux
         * homonymes. Sans artiste connu, la durée est exigée.
         */
        internal fun bestMatch(
            recordings: List<Recording>,
            title: String,
            artist: String?,
            durationMs: Long?,
        ): OnlineMatch? {
            val wantedTitle = title.matchKey()
            val wantedArtist = artist?.matchKey()?.takeIf { it.isNotEmpty() }

            return recordings.mapNotNull { rec ->
                val titleSim = similarity(rec.title.matchKey(), wantedTitle)
                if (titleSim < 0.6) return@mapNotNull null
                val artistSim = wantedArtist?.let { similarity(rec.artist.matchKey(), it) }
                if (artistSim != null && artistSim < 0.5) return@mapNotNull null

                val delta = if (durationMs != null && durationMs > 0 && rec.lengthMs != null) {
                    abs(rec.lengthMs - durationMs)
                } else null
                if (wantedArtist == null && (delta == null || delta > 4_000)) return@mapNotNull null
                if (delta != null && delta > 20_000) return@mapNotNull null

                var confidence = (rec.score * 0.3 + titleSim * 40 + (artistSim ?: 0.6) * 30).toInt()
                confidence += when {
                    delta == null -> 0
                    delta <= 3_000 -> 10
                    delta <= 8_000 -> 0
                    else -> -15
                }
                rec to confidence.coerceIn(0, 100)
            }
                .filter { it.second >= MIN_CONFIDENCE }
                .maxByOrNull { it.second }
                ?.let { (rec, confidence) ->
                    val release = rec.release
                    OnlineMatch(
                        metadata = TrackMetadata(
                            title = rec.title,
                            artist = rec.artist.takeIf { it.isNotBlank() },
                            album = release?.title,
                            albumArtist = release?.artist,
                            year = rec.firstReleaseYear ?: release?.year,
                            genre = rec.genre,
                            track = release?.track,
                            disc = release?.disc,
                        ),
                        releaseId = release?.id,
                        releaseGroupId = release?.groupId,
                        confidence = confidence,
                    )
                }
        }

        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

        /** Forme de comparaison : sans accents, casse ni ponctuation. */
        internal fun String.matchKey(): String = normalizeForSearch().replace(NON_ALNUM, " ").trim()

        /** Ressemblance de 0 à 1 entre deux textes normalisés (inclusion ou mots communs). */
        internal fun similarity(a: String, b: String): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a == b) return 1.0
            if (a.contains(b) || b.contains(a)) {
                return 0.75 + 0.25 * minOf(a.length, b.length) / maxOf(a.length, b.length)
            }
            val wa = a.split(' ').filter { it.isNotEmpty() }.toSet()
            val wb = b.split(' ').filter { it.isNotEmpty() }.toSet()
            val common = wa.intersect(wb).size
            return if (common == 0) 0.0 else common.toDouble() / maxOf(wa.size, wb.size)
        }
    }
}
