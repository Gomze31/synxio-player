package fr.synxio.player.data.repo

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) {

    suspend fun downloadAudio(url: String, onProgress: (Float) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Synxio"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val request = YoutubeDLRequest(url)
            request.addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
            request.addOption("--extract-audio")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
            request.addOption("--embed-thumbnail")
            request.addOption("--add-metadata")

            val response = YoutubeDL.getInstance().execute(request, "DownloadTask") { progress, _, _ ->
                onProgress(progress)
            }
            
            if (response.exitCode == 0) {
                Result.success("Téléchargement terminé !")
            } else {
                Result.failure(Exception("Erreur: Code ${response.exitCode}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
