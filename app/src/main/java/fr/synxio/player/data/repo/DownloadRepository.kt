package fr.synxio.player.data.repo

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
        // Seul un lien de vidéo YouTube validé et reconstruit en https:// atteint yt-dlp :
        // un texte commençant par « - » serait interprété comme une option de ligne de commande.
        val safeUrl = YoutubeUrls.normalizeVideo(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Lien YouTube invalide"))

        // Throwable et non Exception : un échec d'initialisation de la bibliothèque remonte
        // en Error (ExceptionInInitializerError) et faisait planter toute l'application.
        runCatching {
            YoutubeDL.getInstance().init(context)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
        }.onFailure {
            Log.w(TAG, "Initialisation de yt-dlp impossible", it)
            return@withContext Result.failure(
                IllegalStateException("Le téléchargeur n'a pas pu démarrer (${it.javaClass.simpleName})")
            )
        }

        // yt-dlp embarqué dans l'APK vieillit vite : YouTube change régulièrement et une
        // version de quelques semaines échoue. On le met à jour au plus une fois par jour.
        updateYtDlp(force = false)

        // Téléchargement dans le cache privé, puis publication du seul MP3 final.
        //
        // yt-dlp écrivait directement dans Musique/Synxio ses fichiers de travail (.webm,
        // .part, .ytdl) : le stockage limité d'Android refuse les fichiers non audio dans
        // ce dossier, et le MP3 obtenu n'était jamais indexé par MediaStore.
        val workDir = File(context.cacheDir, WORK_DIR).apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val request = YoutubeDLRequest(safeUrl)
            // Aucun fichier de configuration externe (ex. /sdcard/yt-dlp.conf) n'est chargé,
            // et aucune commande --exec ne peut être héritée : on n'en passe jamais.
            request.addOption("--ignore-config")
            request.addOption("--no-exec")
            request.addOption("--no-playlist")
            request.addOption("-o", "${workDir.absolutePath}/%(title)s.%(ext)s")
            request.addOption("--extract-audio")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
            request.addOption("--embed-thumbnail")
            request.addOption("--add-metadata")

            var response = YoutubeDL.getInstance().execute(request, "DownloadTask") { progress, _, _ ->
                onProgress(progress)
            }

            // Échec : un yt-dlp périmé en est la cause la plus fréquente. On force la mise
            // à jour et on réessaie une fois.
            if (response.exitCode != 0 && updateYtDlp(force = true)) {
                Log.w(TAG, "Nouvel essai après mise à jour de yt-dlp : ${response.err.takeLast(300)}")
                workDir.listFiles()?.forEach { it.deleteRecursively() }
                response = YoutubeDL.getInstance().execute(request, "DownloadTask") { progress, _, _ ->
                    onProgress(progress)
                }
            }

            if (response.exitCode != 0) {
                Log.w(TAG, "yt-dlp a échoué (${response.exitCode}) : ${response.err.takeLast(600)}")
                val reason = response.err.lineSequence().lastOrNull { it.isNotBlank() }?.take(200)
                return@withContext Result.failure(
                    Exception(reason ?: "yt-dlp s'est arrêté (code ${response.exitCode})")
                )
            }

            val mp3 = workDir.listFiles()
                ?.filter { it.extension.equals("mp3", ignoreCase = true) }
                ?.maxByOrNull { it.lastModified() }
                ?: return@withContext Result.failure(Exception("Aucun fichier MP3 produit"))

            retagFromTitle(mp3)
            publishToMusic(mp3)
            musicRepository.refresh()
            Result.success("« ${mp3.nameWithoutExtension} » ajouté à ta bibliothèque")
        } catch (e: Throwable) {
            Log.w(TAG, "Téléchargement impossible", e)
            Result.failure(e)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Remplace l'artiste « chaîne YouTube » par celui que porte le titre de la vidéo.
     *
     * yt-dlp met le nom de la chaîne dans le tag artiste quand YouTube ne fournit pas de
     * fiche musicale : « Eurovision France », « 7clouds »… alors que le titre dit
     * « Laura - Plus de problèmes (Clip officiel) ». Le fichier est encore dans le cache
     * privé : on peut l'écrire sans demander d'autorisation.
     */
    private fun retagFromTitle(file: File) {
        runCatching {
            configureTagWriting()
            val audio = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            val title = tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE).orEmpty().ifBlank { file.nameWithoutExtension }
            val known = musicRepository.library.value.artists.mapTo(HashSet()) { it.name.lowercase() }
            val parsed = fr.synxio.player.data.meta.FileNameInference.parseArtistTitle(title) { it.lowercase() in known }
            val artist = parsed.artist ?: return
            val cleanTitle = parsed.title ?: return
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, cleanTitle)
            org.jaudiotagger.audio.AudioFileIO.write(audio)
            Log.i(TAG, "Tags déduits du titre : $artist — $cleanTitle")
        }.onFailure { Log.w(TAG, "Tags du téléchargement laissés tels quels", it) }
    }

    /**
     * Met yt-dlp à jour depuis ses publications officielles.
     *
     * Sans [force], au plus une fois par jour. Renvoie vrai si une nouvelle version a été
     * installée. Une absence de réseau n'empêche pas d'essayer avec la version en place.
     */
    private fun updateYtDlp(force: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_UPDATE, 0L) < UPDATE_INTERVAL_MS) return false
        return runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
        }.onSuccess {
            prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
            Log.i(TAG, "yt-dlp : $it, version ${YoutubeDL.getInstance().versionName(context)}")
        }.onFailure {
            Log.w(TAG, "Mise à jour de yt-dlp impossible", it)
        }.getOrNull() == YoutubeDL.UpdateStatus.DONE
    }

    /**
     * Copie le MP3 dans Musique/Synxio et l'indexe.
     *
     * Android 10+ : insertion par MediaStore (marquée « en attente » le temps de la copie,
     * pour que le scanner ne voie jamais un fichier à moitié écrit). Avant : copie directe
     * dans le dossier public puis scan.
     */
    private suspend fun publishToMusic(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Synxio")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("Impossible de créer le fichier dans Musique")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Flux d'écriture indisponible")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Synxio")
                .apply { mkdirs() }
            val target = File(dir, file.name)
            file.copyTo(target, overwrite = true)
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(target.path), null) { _, _ ->
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
            }
        }
    }

    private companion object {
        const val TAG = "DownloadRepository"
        const val WORK_DIR = "yt-download"
        const val PREFS = "yt_dlp"
        const val KEY_LAST_UPDATE = "last_update"
        const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
