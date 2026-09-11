package fr.synxio.player.data.repo

import android.util.Log
import fr.synxio.player.data.db.LyricsCacheEntity
import fr.synxio.player.data.db.LyricsDao
import fr.synxio.player.data.model.LyricLine
import fr.synxio.player.data.model.Lyrics
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Pourquoi on n'a pas de paroles — ce qui détermine quoi proposer à l'utilisateur. */
enum class LyricsOutcome {
    FOUND,
    /** La recherche en ligne est désactivée dans les réglages. */
    ONLINE_DISABLED,
    /** LRCLIB a répondu, mais ne connaît pas ce morceau (ou les tags sont trop faux). */
    NOT_FOUND,
    /** LRCLIB est injoignable : pas de réseau, ou filtrage DNS de l'opérateur. */
    UNREACHABLE,
}

data class LyricsResult(val lyrics: Lyrics, val outcome: LyricsOutcome)

/**
 * Paroles, par ordre de priorité :
 *  1. fichier `.lrc` à côté du morceau (synchronisées, ce que l'utilisateur a choisi) ;
 *  2. tag `LYRICS` embarqué dans le fichier audio ;
 *  3. cache local d'une récupération en ligne précédente ;
 *  4. LRCLIB (API publique, sans clé) si l'utilisateur a activé la recherche en ligne.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val dao: LyricsDao,
    private val http: OkHttpClient,
) {

    suspend fun lyricsFor(song: Song, allowOnline: Boolean): LyricsResult =
        withContext(Dispatchers.IO) {
            val local = sidecarLrc(song) ?: embeddedLyrics(song) ?: cached(song)
            if (local != null) return@withContext LyricsResult(local, LyricsOutcome.FOUND)

            if (!allowOnline) {
                return@withContext LyricsResult(Lyrics.EMPTY, LyricsOutcome.ONLINE_DISABLED)
            }

            // On distingue « rien trouvé » de « serveur injoignable » : le premier cas
            // invite à corriger les tags, le second à changer de réseau. Un message
            // unique enverrait chercher au mauvais endroit.
            networkFailed = false
            val online = fetchOnline(song)
            when {
                online != null -> LyricsResult(online, LyricsOutcome.FOUND)
                networkFailed -> LyricsResult(Lyrics.EMPTY, LyricsOutcome.UNREACHABLE)
                else -> LyricsResult(Lyrics.EMPTY, LyricsOutcome.NOT_FOUND)
            }
        }

    /**
     * Vrai si le dernier appel réseau a échoué au niveau transport.
     *
     * Porté par le dépôt plutôt que remonté par chaque fonction : elles renvoient déjà
     * `null` pour « pas de résultat », et les trois niveaux d'appel auraient tous dû
     * changer de signature pour transporter la nuance.
     */
    private var networkFailed = false

    /** Paroles saisies à la main : écrites en `.lrc` à côté du fichier si possible. */
    suspend fun saveManual(song: Song, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(song.path.substringBeforeLast('.') + ".lrc")
            if (target.parentFile?.canWrite() == true) {
                target.writeText(content)
            } else {
                // Stockage interne inaccessible en écriture : on retombe sur le cache Room.
                dao.put(
                    LyricsCacheEntity(
                        songPath = song.path,
                        content = content,
                        synced = content.contains(TIMESTAMP_REGEX),
                        source = SOURCE_MANUAL,
                        fetchedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    suspend fun clearCache(song: Song) = dao.delete(song.path)

    // --- Sources -------------------------------------------------------------------------

    private fun sidecarLrc(song: Song): Lyrics? {
        val base = song.path.substringBeforeLast('.')
        for (ext in listOf(".lrc", ".LRC", ".txt")) {
            val file = File(base + ext)
            if (file.isFile && file.canRead()) {
                val text = runCatching { file.readText() }.getOrNull() ?: continue
                if (text.isNotBlank()) return parse(text, "Fichier ${file.name}")
            }
        }
        return null
    }

    private fun embeddedLyrics(song: Song): Lyrics? = runCatching {
        val file = File(song.path)
        if (!file.canRead()) return null
        val tag = AudioFileIO.read(file).tag ?: return null
        val text = tag.getFirst(FieldKey.LYRICS)
        if (text.isNullOrBlank()) null else parse(text, "Tag du fichier")
    }.getOrElse {
        Log.d(TAG, "Pas de paroles embarquées pour ${song.title}")
        null
    }

    private suspend fun cached(song: Song): Lyrics? =
        dao.get(song.path)?.let { parse(it.content, it.source) }

    /**
     * Interroge LRCLIB en deux temps : correspondance exacte, puis recherche libre.
     *
     * Les fichiers rippés depuis YouTube ont des tags approximatifs
     * (« Dua Lipa - Love Again (Lyrics) » par « 7clouds ») sur lesquels `/api/get`
     * échoue systématiquement ; `/api/search` retrouve le morceau malgré ça.
     */
    private suspend fun fetchOnline(song: Song): Lyrics? {
        val title = song.title.cleanedTrackTitle()
        val artist = song.displayArtist

        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .addQueryParameter("album_name", song.displayAlbum)
            .addQueryParameter("duration", (song.durationMs / 1000).toString())
            .build()

        val content = requestJson(exact)?.let(::extractLyrics)
            ?: searchOnline(title, artist, song.durationMs)
            ?: return null

        dao.put(
            LyricsCacheEntity(
                songPath = song.path,
                content = content.first,
                synced = content.second,
                source = SOURCE_LRCLIB,
                fetchedAt = System.currentTimeMillis(),
            )
        )
        return parse(content.first, SOURCE_LRCLIB)
    }

    /** Recherche libre : on retient le résultat dont la durée colle le mieux. */
    private fun searchOnline(title: String, artist: String, durationMs: Long): Pair<String, Boolean>? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$artist $title".trim())
            .build()

        val body = runCatching {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrElse {
            // Échec de transport (DNS filtré, pas de réseau, TLS refusé) : à distinguer
            // d'une réponse vide, qui signifierait seulement « morceau inconnu ».
            Log.w(TAG, "LRCLIB injoignable sur /api/search", it)
            networkFailed = true
            null
        } ?: return null

        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        if (array.length() == 0) return null

        val targetSec = durationMs / 1000
        var best: JSONObject? = null
        var bestDelta = Long.MAX_VALUE
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val delta = kotlin.math.abs(item.optLong("duration") - targetSec)
            if (delta < bestDelta) {
                bestDelta = delta
                best = item
            }
        }

        // Plus de 15 s d'écart : ce n'est probablement pas la même version.
        if (best == null || bestDelta > 15) return null
        return extractLyrics(best)
    }

    private fun requestJson(url: okhttp3.HttpUrl): JSONObject? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null
            else JSONObject(response.body?.string().orEmpty())
        }
    }.getOrElse {
        Log.w(TAG, "LRCLIB injoignable sur /api/get", it)
        networkFailed = true
        null
    }

    /** Retourne le contenu et un booléen « synchronisé ». */
    private fun extractLyrics(json: JSONObject): Pair<String, Boolean>? {
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        if (synced != null) return synced to true
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
        return plain?.let { it to false }
    }

    /** Retire le bruit des titres rippés : « (Lyrics) », « [Official Video] », « HD »… */
    private fun String.cleanedTrackTitle(): String = this
        .replace(NOISE_REGEX, " ")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
        .ifEmpty { this }

    // --- Parsing LRC ---------------------------------------------------------------------

    /**
     * Gère le LRC standard, y compris les tags multiples sur une même ligne
     * (`[00:12.00][01:45.00]refrain`) et les centièmes ou millièmes de seconde.
     */
    fun parse(raw: String, source: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        var anySynced = false

        raw.lineSequence().forEach { rawLine ->
            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            val text = rawLine.replace(TIMESTAMP_REGEX, "").trim()
            if (matches.isEmpty()) {
                if (text.isNotBlank() && !METADATA_REGEX.matches(rawLine.trim())) {
                    lines += LyricLine(null, text)
                }
            } else {
                anySynced = true
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val fraction = match.groupValues[3]
                    val fractionMs = when (fraction.length) {
                        0 -> 0L
                        1 -> fraction.toLong() * 100
                        2 -> fraction.toLong() * 10
                        else -> fraction.take(3).toLong()
                    }
                    lines += LyricLine(minutes * 60_000 + seconds * 1_000 + fractionMs, text)
                }
            }
        }

        val ordered = if (anySynced) lines.sortedBy { it.timeMs ?: Long.MAX_VALUE } else lines
        return Lyrics(ordered, synced = anySynced, source = source)
    }

    private companion object {
        const val TAG = "LyricsRepository"
        const val SOURCE_LRCLIB = "LRCLIB"
        const val SOURCE_MANUAL = "Manuel"
        const val USER_AGENT = "Synxio Player v1.0 (lecteur local Android)"
        val NOISE_REGEX = Regex(
            """\((?:official\s*)?(?:lyrics?|audio|video|music\s*video|visualizer|hd|hq|4k)[^)]*\)""" +
                """|\[[^\]]*(?:lyrics?|official|audio|video|hd|hq|4k)[^\]]*]""" +
                """|\b(?:official\s+(?:music\s+)?video|lyrics?\s+video|audio\s+officiel)\b""",
            RegexOption.IGNORE_CASE,
        )
        val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val METADATA_REGEX = Regex("""\[[a-zA-Z]+:.*]""")
    }
}
