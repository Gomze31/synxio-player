package fr.synxio.player.data.repo

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrimRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Découpe la chanson `song` de `startMs` à `endMs` et crée un nouveau fichier.
     * @return Le chemin du nouveau fichier, ou null en cas d'échec.
     */
    suspend fun trimAudio(song: Song, startMs: Long, endMs: Long): String? = withContext(Dispatchers.IO) {
        try {
            val inputFile = File(song.path)
            if (!inputFile.exists()) return@withContext null

            val extension = inputFile.extension.ifEmpty { "mp3" }
            val outputFile = File(
                inputFile.parentFile,
                "${inputFile.nameWithoutExtension}_trim.$extension"
            )

            // s = start, t = duration
            val startStr = formatTime(startMs)
            val durationStr = formatTime(endMs - startMs)

            // ffmpeg -i input.mp3 -ss 00:01:00.000 -t 00:00:30.000 -c copy output.mp3
            val command = arrayOf(
                "-i", inputFile.absolutePath,
                "-ss", startStr,
                "-t", durationStr,
                "-c", "copy",
                outputFile.absolutePath
            )
            
            // com.yausername.ffmpeg.FFmpeg instance is already initialized along with YoutubeDL
            val response = com.yausername.ffmpeg.FFmpeg.getInstance().execute(command)
            
            if (response.exitCode == 0 && outputFile.exists()) {
                // Return path to the new file so we can scan it
                return@withContext outputFile.absolutePath
            } else {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                return@withContext null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
