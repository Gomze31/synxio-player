package fr.synxio.player.data.repo

import android.util.Log
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Une correspondance trouvée en ligne pour un morceau. */
data class MetadataMatch(
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationSec: Int,
    val artworkUrl: String?,
    /** Écart de durée avec le fichier local, en secondes. Plus c'est bas, plus c'est sûr. */
    val durationDeltaSec: Int,
) {
    /**
     * Confiance indicative affichée à l'utilisateur.
     * La durée est le signal le plus fiable : deux morceaux homonymes ont rarement
     * la même longueur à la seconde près.
     */
    val confidence: Int
        get() = when {
            durationDeltaSec <= 2 -> 95
            durationDeltaSec <= 5 -> 80
            durationDeltaSec <= 15 -> 60
            else -> 35
        }
}

/**
 * Recherche de métadonnées via l'API publique Deezer.
 *
 * Choisie parce qu'elle ne demande aucune clé, corrige la casse et les accents
 * (« Benabar » → « Bénabar »), et expose des pochettes en 1000×1000 — ce qui règle
 * le cas des fichiers rippés dont les tags sont approximatifs.
 */
@Singleton
class MetadataRepository @Inject constructor(
    private val http: OkHttpClient,
) {

    /** Cherche des correspondances pour [song], triées par plausibilité. */
    suspend fun findMatches(song: Song, limit: Int = 12): Result<List<MetadataMatch>> =
        search(buildQuery(song), song.durationMs / 1000, limit)

    /** Recherche libre, pour quand la suggestion automatique tombe à côté. */
    suspend fun search(
        query: String,
        expectedDurationSec: Long = 0,
        limit: Int = 12,
    ): Result<List<MetadataMatch>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())

        runCatching {
            val url = "https://api.deezer.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", limit.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }

            val json = JSONObject(body)
            json.optJSONObject("error")?.let { error("Deezer: ${it.optString("message")}") }

            val data = json.optJSONArray("data") ?: return@runCatching emptyList()
            (0 until data.length())
                .mapNotNull { data.optJSONObject(it) }
                .mapNotNull { it.toMatch(expectedDurationSec) }
                // La durée d'abord : c'est le discriminant le plus fiable.
                .sortedBy { it.durationDeltaSec }
        }.onFailure { Log.w(TAG, "Recherche de métadonnées échouée", it) }
    }

    /**
     * Complète une correspondance avec l'année et le genre, absents de la recherche.
     * Non bloquant : en cas d'échec on garde ce qu'on a déjà.
     */
    suspend fun albumDetails(albumId: Long): AlbumDetails? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.deezer.com/album/$albumId")
                .header("User-Agent", USER_AGENT)
                .build()

            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.string().orEmpty()
            }

            val json = JSONObject(body)
            AlbumDetails(
                year = json.optString("release_date").take(4).toIntOrNull(),
                genre = json.optJSONObject("genres")
                    ?.optJSONArray("data")
                    ?.optJSONObject(0)
                    ?.optString("name")
                    ?.takeIf { it.isNotBlank() },
                trackCount = json.optInt("nb_tracks").takeIf { it > 0 },
            )
        }.getOrNull()
    }

    /** Télécharge la pochette pour l'embarquer dans le fichier. */
    suspend fun downloadArtwork(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        }.getOrElse {
            Log.w(TAG, "Téléchargement de pochette échoué", it)
            null
        }
    }

    private fun JSONObject.toMatch(expectedDurationSec: Long): MetadataMatch? {
        val title = optString("title").takeIf { it.isNotBlank() } ?: return null
        val artist = optJSONObject("artist")?.optString("name").orEmpty()
        val albumJson = optJSONObject("album")
        val duration = optInt("duration")

        return MetadataMatch(
            title = title,
            artist = artist,
            album = albumJson?.optString("title").orEmpty(),
            albumId = albumJson?.optLong("id") ?: 0L,
            durationSec = duration,
            artworkUrl = albumJson?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                ?: albumJson?.optString("cover_big")?.takeIf { it.isNotBlank() },
            durationDeltaSec = if (expectedDurationSec > 0) {
                abs(duration - expectedDurationSec).toInt()
            } else {
                Int.MAX_VALUE / 2
            },
        )
    }

    /**
     * Construit la requête à partir des tags locaux, en retirant le bruit des rips
     * (« (Lyrics) », « [Official Video] ») et les artistes de chaîne YouTube.
     */
    private fun buildQuery(song: Song): String {
        val title = song.title.replace(NOISE_REGEX, " ").replace(MULTISPACE, " ").trim()
        val artist = song.displayArtist
            .takeUnless { it == Song.UNKNOWN_ARTIST || it in CHANNEL_NOISE }
            .orEmpty()

        // Certains titres contiennent déjà « Artiste - Titre » : inutile de doubler.
        return if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) {
            "$artist $title"
        } else {
            title
        }.trim()
    }

    data class AlbumDetails(val year: Int?, val genre: String?, val trackCount: Int?)

    private companion object {
        const val TAG = "MetadataRepository"
        const val USER_AGENT = "Synxio Player v1.0 (lecteur local Android)"
        val MULTISPACE = Regex("\\s{2,}")
        val NOISE_REGEX = Regex(
            """\((?:official\s*)?(?:lyrics?|audio|video|music\s*video|visualizer|hd|hq|4k)[^)]*\)""" +
                """|\[[^\]]*(?:lyrics?|official|audio|video|hd|hq|4k)[^\]]*]""" +
                """|\b(?:official\s+(?:music\s+)?video|lyrics?\s+video|audio\s+officiel)\b""",
            RegexOption.IGNORE_CASE,
        )
        /** Chaînes de re-upload qui polluent le champ artiste. */
        val CHANNEL_NOISE = setOf("7clouds", "Various Artists", "Topic", "Unknown", "VA")
    }
}
