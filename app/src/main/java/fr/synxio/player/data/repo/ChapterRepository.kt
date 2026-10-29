package fr.synxio.player.data.repo

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.media.ByteSource
import fr.synxio.player.data.media.Chapter
import fr.synxio.player.data.media.EmbeddedChapters
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chapitres intégrés aux fichiers de livres audio, lus une fois par version de fichier.
 *
 * Le cache est en mémoire : la lecture ne touche que les en-têtes, la refaire à chaque
 * démarrage coûte quelques millisecondes par fichier.
 */
@Singleton
class ChapterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, List<Chapter>>()

    suspend fun chaptersOf(song: Song): List<Chapter> {
        val key = "${song.id}:${song.dateModifiedSec}:${song.sizeBytes}"
        cache[key]?.let { return it }
        val chapters = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(song.uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                        EmbeddedChapters.read(ChannelSource(channel), song.durationMs)
                    }
                }.orEmpty()
            }.onFailure { Log.w(TAG, "Chapitres illisibles : ${song.path}", it) }
                .getOrDefault(emptyList())
        }
        cache[key] = chapters
        return chapters
    }

    /** Chapitres de chaque fichier qui en a, par identifiant de morceau. */
    suspend fun chaptersOf(songs: List<Song>): Map<Long, List<Chapter>> =
        songs.mapNotNull { song -> chaptersOf(song).takeIf { it.isNotEmpty() }?.let { song.id to it } }.toMap()

    private class ChannelSource(private val channel: FileChannel) : ByteSource {
        override val size: Long = channel.size()
        override fun read(position: Long, length: Int): ByteArray {
            if (position < 0 || position >= size || length <= 0) return ByteArray(0)
            val buffer = ByteBuffer.allocate(minOf(length.toLong(), size - position).toInt())
            while (buffer.hasRemaining()) {
                if (channel.read(buffer, position + buffer.position()) < 0) break
            }
            return buffer.array().copyOf(buffer.position())
        }
    }

    private companion object {
        const val TAG = "ChapterRepository"
    }
}
