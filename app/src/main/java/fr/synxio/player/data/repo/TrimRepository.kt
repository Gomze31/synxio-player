package fr.synxio.player.data.repo

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Découpe audio avec le binaire FFmpeg fourni par youtubedl-android.
 *
 * La bibliothèque n'expose pas d'API pour lancer FFmpeg avec nos propres arguments :
 * on exécute donc le binaire directement. Il vit dans `nativeLibraryDir` (d'où
 * `useLegacyPackaging`), et ses bibliothèques partagées sont extraites par `FFmpeg.init`
 * dans `noBackupFilesDir/youtubedl-android/packages/ffmpeg/usr/lib`.
 */
@Singleton
class TrimRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Découpe la chanson `song` de `startMs` à `endMs` et crée un nouveau fichier.
     * @return Le chemin du nouveau fichier, ou null en cas d'échec.
     */
    suspend fun trimAudio(song: Song, startMs: Long, endMs: Long): String? = withContext(Dispatchers.IO) {
        if (endMs <= startMs) return@withContext null
        val inputFile = File(song.path)
        if (!inputFile.exists()) return@withContext null

        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val ok = runCatching { FFmpeg.getInstance().init(context) }
            .onFailure { Log.w(TAG, "Initialisation FFmpeg impossible", it) }
            .isSuccess
        if (!ok || !ffmpeg.exists()) return@withContext null

        val extension = inputFile.extension.ifEmpty { "mp3" }
        val baseName = "${inputFile.nameWithoutExtension}_trim"

        // Le dossier d'origine d'abord ; s'il est hors des dossiers média accessibles
        // (stockage restreint), on se rabat sur Musique/Synxio.
        val fallbackDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Synxio",
        )
        for (dir in listOfNotNull(inputFile.parentFile, fallbackDir)) {
            dir.mkdirs()
            val output = uniqueFile(dir, baseName, extension)
            if (runFfmpeg(ffmpeg, inputFile, output, startMs, endMs)) {
                scan(output)
                return@withContext output.absolutePath
            }
            output.delete()
        }
        null
    }

    private fun runFfmpeg(ffmpeg: File, input: File, output: File, startMs: Long, endMs: Long): Boolean {
        val command = listOf(
            ffmpeg.absolutePath,
            "-y",
            "-ss", formatTime(startMs),
            "-i", input.absolutePath,
            "-t", formatTime(endMs - startMs),
            "-map", "0",
            "-map_metadata", "0",
            "-c", "copy",
            output.absolutePath,
        )
        val libDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { environment()["LD_LIBRARY_PATH"] = libDir.absolutePath }
                .start()
            // Vider la sortie, sinon FFmpeg bloque une fois le tampon du pipe plein.
            val log = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroy()
                return false
            }
            val success = process.exitValue() == 0 && output.length() > 0
            if (!success) Log.w(TAG, "FFmpeg a échoué :\n${log.takeLast(2000)}")
            success
        }.getOrElse {
            Log.w(TAG, "Lancement de FFmpeg impossible", it)
            false
        }
    }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        var candidate = File(dir, "$baseName.$extension")
        var n = 2
        while (candidate.exists()) candidate = File(dir, "${baseName}_${n++}.$extension")
        return candidate
    }

    /** Indexe le fichier dans MediaStore avant que la bibliothèque ne soit relue. */
    private suspend fun scan(file: File) = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, _ ->
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = ms % 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    private companion object {
        const val TAG = "TrimRepository"
    }
}
