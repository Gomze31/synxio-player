package fr.synxio.player.data.repo

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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
            val inputPath = song.data
            if (inputPath.isNullOrEmpty() || !File(inputPath).exists()) {
                return@withContext null
            }

            val inputFile = File(inputPath)
            val extension = inputFile.extension
            val parentDir = inputFile.parentFile ?: context.getExternalFilesDir(null)
            val outputFile = File(parentDir, "${inputFile.nameWithoutExtension}_trimmed.$extension")

            // Format des temps : HH:MM:SS.mmm
            val startTime = formatTime(startMs)
            val durationTime = formatTime(endMs - startMs)

            // ffmpeg -ss startTime -t duration -i inputPath -c copy outputPath
            val command = "-y -ss $startTime -t $durationTime -i \"$inputPath\" -c copy \"${outputFile.absolutePath}\""
            
            val session = FFmpegKit.execute(command)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                return@withContext outputFile.absolutePath
            } else {
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
