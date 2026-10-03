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

/**
 * Sert les fichiers locaux au Chromecast.
 *
 * Un récepteur Cast télécharge lui-même le média : une URI `content://` ne lui dit rien.
 * Ce serveur expose chaque morceau sur le réseau local, derrière un jeton aléatoire
 * régénéré à chaque session pour ne pas ouvrir la bibliothèque à tout le réseau.
 *
 * Les requêtes `Range` sont gérées : le récepteur s'en sert pour avancer dans la piste.
 */
class CastMediaServer(
    private val context: Context,
    private val songById: (Long) -> Song?,
) : NanoHTTPD(0) {

    private val token = UUID.randomUUID().toString().replace("-", "")

    /** Adresse joignable par le Chromecast, ou null hors Wi-Fi. */
    private val host: String? = localIpv4()

    val isReachable: Boolean get() = host != null && wasStarted()

    /** Remplace l'URI locale par une URL de ce serveur. Les flux réseau restent tels quels. */
    fun toCastItem(item: MediaItem): MediaItem {
        val scheme = item.localConfiguration?.uri?.scheme
        if (scheme == "http" || scheme == "https") return item
        val song = songById(item.songId) ?: return item
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
        val song = parts[2].toLongOrNull()?.let(songById) ?: return notFound()

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
        val stream: InputStream = context.contentResolver.openInputStream(song.uri) ?: return notFound()

        val bounds = range?.removePrefix("bytes=")?.split('-')
        val start = bounds?.getOrNull(0)?.toLongOrNull()
        if (start == null || start >= length) {
            return newFixedLengthResponse(Response.Status.OK, mime, stream, length).apply {
                addHeader("Accept-Ranges", "bytes")
            }
        }
        val end = bounds.getOrNull(1)?.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
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

    private companion object {
        const val TAG = "CastMediaServer"

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
