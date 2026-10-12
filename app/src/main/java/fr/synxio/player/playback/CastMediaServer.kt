package fr.synxio.player.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import fi.iki.elonen.NanoHTTPD
import fr.synxio.player.data.model.Song
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sert les fichiers locaux au Chromecast.
 *
 * Un récepteur Cast télécharge lui-même le média : une URI `content://` ne lui dit rien.
 * Ce serveur expose chaque morceau sur le réseau local, derrière un jeton aléatoire
 * régénéré à chaque session pour ne pas ouvrir la bibliothèque à tout le réseau.
 *
 * Les requêtes `Range` sont gérées : le récepteur s'en sert pour avancer dans la piste.
 *
 * Le serveur se lie uniquement à l'adresse Wi-Fi trouvée à la construction (jamais à
 * toutes les interfaces), et ne sert que les morceaux effectivement envoyés au récepteur
 * via [toCastItem].
 */
class CastMediaServer private constructor(
    private val context: Context,
    private val songById: (Long) -> Song?,
    /** Adresse joignable par le Chromecast, ou null hors Wi-Fi. */
    private val host: String?,
) : NanoHTTPD(host ?: LOOPBACK, 0) {

    constructor(context: Context, songById: (Long) -> Song?) : this(context, songById, localIpv4())

    private val token = UUID.randomUUID().toString().replace("-", "")

    /** Morceaux confiés au récepteur : seuls ceux-ci sont servis. */
    private val castIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val isReachable: Boolean get() = host != null && wasStarted()

    /** Remplace l'URI locale par une URL de ce serveur. Les flux réseau restent tels quels. */
    fun toCastItem(item: MediaItem): MediaItem {
        val scheme = item.localConfiguration?.uri?.scheme
        if (scheme == "http" || scheme == "https") return item
        val song = songById(item.songId) ?: return item
        castIds += song.id
        val base = "http://$host:$listeningPort/$token"
        return item.buildUpon()
            .setUri("$base/song/${song.id}")
            .setMimeType(song.mimeType.ifBlank { "audio/mpeg" })
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setArtworkUri(android.net.Uri.parse("$base/art/${song.id}"))
                    .build()
            )
            .build()
    }

    override fun serve(session: IHTTPSession): Response {
        val parts = session.uri.trim('/').split('/')
        if (parts.size != 3 || parts[0] != token) return notFound()
        val id = parts[2].toLongOrNull()?.takeIf { it in castIds } ?: return notFound()
        val song = songById(id) ?: return notFound()

        return runCatching {
            when (parts[1]) {
                "song" -> serveSong(song, session.headers["range"])
                "art" -> context.contentResolver.openInputStream(song.artworkUri)
                    ?.let { newChunkedResponse(Response.Status.OK, "image/jpeg", it) }
                    ?: notFound()
                else -> notFound()
            }
        }.getOrElse {
            Log.w(TAG, "Requête Cast impossible", it)
            notFound()
        }
    }

    private fun serveSong(song: Song, range: String?): Response {
        val length = context.contentResolver.openAssetFileDescriptor(song.uri, "r")
            ?.use { it.length }
            ?.takeIf { it > 0 }
            ?: song.sizeBytes
        val mime = song.mimeType.ifBlank { "audio/mpeg" }
        val byteRange = when (val parsed = parseRange(range, length)) {
            RangeResult.Unsatisfiable ->
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes */$length")
                }
            RangeResult.Full -> null
            is RangeResult.Partial -> parsed
        }
        val stream: InputStream = context.contentResolver.openInputStream(song.uri) ?: return notFound()

        if (byteRange == null) {
            return newFixedLengthResponse(Response.Status.OK, mime, stream, length).apply {
                addHeader("Accept-Ranges", "bytes")
            }
        }
        val start = byteRange.start
        val end = byteRange.end
        stream.skipFully(start)
        return newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, stream, end - start + 1).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes $start-$end/$length")
        }
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")

    /** Résultat de l'analyse d'un en-tête `Range`. */
    internal sealed interface RangeResult {
        /** Pas de plage exploitable : réponse 200 complète. */
        data object Full : RangeResult
        /** Plage invalide (fin avant début, début hors fichier) : 416. */
        data object Unsatisfiable : RangeResult
        data class Partial(val start: Long, val end: Long) : RangeResult
    }

    internal companion object {
        const val TAG = "CastMediaServer"
        const val LOOPBACK = "127.0.0.1"

        /**
         * Analyse `bytes=début-fin` / `bytes=début-` / `bytes=-suffixe` pour un fichier de
         * [length] octets. Une plage incohérente (fin < début, début ≥ taille) donne 416.
         */
        fun parseRange(header: String?, length: Long): RangeResult {
            if (header.isNullOrBlank() || length <= 0) return RangeResult.Full
            val spec = header.trim()
            if (!spec.startsWith("bytes=", ignoreCase = true)) return RangeResult.Full
            val first = spec.substring(6).substringBefore(',').trim()
            if ('-' !in first) return RangeResult.Unsatisfiable
            val startText = first.substringBefore('-').trim()
            val endText = first.substringAfter('-').trim()
            if (startText.isEmpty()) {
                // Suffixe : les N derniers octets.
                val suffix = endText.toLongOrNull() ?: return RangeResult.Unsatisfiable
                if (suffix <= 0) return RangeResult.Unsatisfiable
                return RangeResult.Partial((length - suffix).coerceAtLeast(0), length - 1)
            }
            val start = startText.toLongOrNull() ?: return RangeResult.Unsatisfiable
            if (start < 0 || start >= length) return RangeResult.Unsatisfiable
            val end = if (endText.isEmpty()) {
                length - 1
            } else {
                val parsedEnd = endText.toLongOrNull() ?: return RangeResult.Unsatisfiable
                if (parsedEnd < start) return RangeResult.Unsatisfiable
                parsedEnd.coerceAtMost(length - 1)
            }
            return RangeResult.Partial(start, end)
        }

        fun localIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
