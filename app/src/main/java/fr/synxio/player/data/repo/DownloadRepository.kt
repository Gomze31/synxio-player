package fr.synxio.player.data.repo

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.diag.Diagnostics
import fr.synxio.player.data.repo.ytdlp.YtDlpOutdated
import fr.synxio.player.data.repo.ytdlp.YtDlpUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Échec d'un téléchargement, pour que l'écran distingue un yt-dlp périmé. */
sealed class DownloadError(message: String) : Exception(message) {
    class YtDlpOutdated(message: String) : DownloadError(message)
    class Failed(message: String) : DownloadError(message)
}

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val http: OkHttpClient,
    private val diagnostics: Diagnostics,
) {
    /** Jamais une commande yt-dlp pendant une mise à jour, ni l'inverse. */
    private val ytDlpLock = Mutex()

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

            // La bibliothèque lève YoutubeDLException (message = stderr de yt-dlp) dès que
            // yt-dlp sort avec un code non nul : c'est le cas normal d'échec, classé ici.
            // CanceledException et l'annulation de la coroutine ne sont pas interceptées.
            val response = ytDlpLock.withLock {
                recoverInterruptedUpdate()
                try {
                    YoutubeDL.getInstance().execute(request, "DownloadTask") { progress, _, _ ->
                        onProgress(progress)
                    }
                } catch (e: YoutubeDLException) {
                    throw downloadError(e.message.orEmpty(), exitCode = null)
                }
            }

            // Code non nul sans exception : seulement quand la bibliothèque ignore les erreurs.
            if (response.exitCode != 0) {
                return@withContext Result.failure(downloadError(response.err, response.exitCode))
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
            // Un DownloadError est rendu tel quel : l'écran en tire l'alerte « périmé ».
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

    /** Classe un échec de yt-dlp d'après son stderr : périmé ou autre, et le journalise. */
    private fun downloadError(stderr: String, exitCode: Int?): DownloadError {
        val code = exitCode?.toString() ?: "non nul"
        diagnostics.log(TAG, Diagnostics.Level.W, "yt-dlp a échoué ($code) : ${stderr.takeLast(600)}")
        val reason = stderr.lineSequence().lastOrNull { it.isNotBlank() }?.take(200)
            ?: "yt-dlp s'est arrêté (code $code)"
        return if (YtDlpOutdated.looksOutdated(stderr)) DownloadError.YtDlpOutdated(reason)
        else DownloadError.Failed(reason)
    }

    /** Version de yt-dlp installée (tag de release), ou `null` si la bibliothèque n'est pas initialisée. */
    suspend fun ytDlpVersion(): String? = withContext(Dispatchers.IO) {
        runCatching {
            YoutubeDL.getInstance().init(context)
            YoutubeDL.getInstance().version(context)
        }.onFailure { diagnostics.log(TAG, Diagnostics.Level.W, "Version de yt-dlp illisible", it) }.getOrNull()
    }

    /**
     * Mise à jour manuelle et vérifiée de yt-dlp. Voir [YtDlpUpdater] pour la règle ; ici
     * seulement le câblage : chemin du binaire de la bibliothèque, appel de sa mise à
     * jour, téléchargement des sommes de la release.
     *
     * La sauvegarde vit à côté du dossier `yt-dlp` de la bibliothèque, jamais dedans : la
     * mise à jour de youtubedl-android supprime ce dossier entier. Les préférences de la
     * bibliothèque (`dlpVersion`, `dlpVersionName`) sont réécrites par sa mise à jour ;
     * après un refus, elles reprennent leur valeur d'avant pour que la version affichée
     * soit celle du binaire réellement sur le disque.
     */
    suspend fun updateYtDlp(): YtDlpUpdater.Result = withContext(Dispatchers.IO) {
        runCatching { YoutubeDL.getInstance().init(context) }
            .onFailure {
                diagnostics.log(TAG, Diagnostics.Level.W, "Initialisation de yt-dlp impossible avant mise à jour", it)
                return@withContext YtDlpUpdater.Result.Rejected("yt-dlp n'a pas pu démarrer")
            }
        val updater = YtDlpUpdater(
            binary = ytDlpBinary(),
            backup = ytDlpBackup(),
            runLibraryUpdate = {
                when (YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)) {
                    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdater.LibraryOutcome.UPDATED
                    else -> YtDlpUpdater.LibraryOutcome.ALREADY_UP_TO_DATE
                }
            },
            installedVersion = { YoutubeDL.getInstance().version(context) },
            fetchSums = ::fetchShaSums,
        )
        val result = ytDlpLock.withLock {
            val libraryPrefs = context.getSharedPreferences(LIB_PREFS, Context.MODE_PRIVATE)
            val savedVersion = libraryPrefs.getString(PREF_DLP_VERSION, null)
            val savedVersionName = libraryPrefs.getString(PREF_DLP_VERSION_NAME, null)
            val outcome = updater.update()
            if (outcome is YtDlpUpdater.Result.Rejected) {
                if (outcome.lost) {
                    // Le binaire embarqué revient : aucune version téléchargée n'est plus en place.
                    restoreLibraryVersion(libraryPrefs, null, null)
                    reinstallBundledYtDlp()
                } else {
                    restoreLibraryVersion(libraryPrefs, savedVersion, savedVersionName)
                }
            }
            outcome
        }
        when (result) {
            is YtDlpUpdater.Result.Updated -> diagnostics.log(TAG, Diagnostics.Level.I, "yt-dlp mis à jour et vérifié : ${result.version}")
            is YtDlpUpdater.Result.Rejected -> if (result.lost) {
                diagnostics.log(TAG, Diagnostics.Level.E, "yt-dlp : mise à jour rejetée, ancien binaire perdu (${result.reason})")
            } else {
                diagnostics.log(TAG, Diagnostics.Level.W, "yt-dlp : mise à jour rejetée, ancien binaire restauré (${result.reason})")
            }
            YtDlpUpdater.Result.AlreadyUpToDate -> diagnostics.log(TAG, Diagnostics.Level.I, "yt-dlp déjà à jour")
        }
        result
    }

    private fun ytDlpDir(): File = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")

    private fun ytDlpBinary(): File = File(ytDlpDir(), "yt-dlp")

    /** Hors du dossier `yt-dlp`, que la bibliothèque supprime pendant sa mise à jour. */
    private fun ytDlpBackup(): File = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp.bak")

    /**
     * Une sauvegarde restante signale une mise à jour interrompue (application tuée entre
     * la copie et la vérification) : elle repasse par-dessus le binaire non vérifié avant
     * toute commande. À appeler sous [ytDlpLock].
     */
    private fun recoverInterruptedUpdate() {
        val backup = ytDlpBackup()
        if (!backup.exists()) return
        val recovered = YtDlpUpdater.recoverInterrupted(ytDlpBinary(), backup)
        if (recovered) {
            diagnostics.log(TAG, Diagnostics.Level.W, "yt-dlp : mise à jour interrompue, ancien binaire repris")
        } else {
            diagnostics.log(TAG, Diagnostics.Level.W, "yt-dlp : mise à jour interrompue, reprise impossible")
            restoreLibraryVersion(context.getSharedPreferences(LIB_PREFS, Context.MODE_PRIVATE), null, null)
            reinstallBundledYtDlp()
        }
    }

    /** Remet les préférences de version de la bibliothèque dans leur état d'avant la mise à jour. */
    private fun restoreLibraryVersion(prefs: android.content.SharedPreferences, version: String?, versionName: String?) {
        runCatching {
            val editor = prefs.edit()
            if (version == null) editor.remove(PREF_DLP_VERSION) else editor.putString(PREF_DLP_VERSION, version)
            if (versionName == null) editor.remove(PREF_DLP_VERSION_NAME) else editor.putString(PREF_DLP_VERSION_NAME, versionName)
            check(editor.commit()) { "écriture des préférences refusée" }
        }.onFailure { diagnostics.log(TAG, Diagnostics.Level.W, "Version de yt-dlp non rétablie après refus", it) }
    }

    /**
     * Réinstalle le binaire embarqué dans l'APK. `init` ne le ferait pas : il rend la main
     * dès que la bibliothèque est initialisée dans le processus. `init_ytdlp` ne copie
     * le binaire embarqué que si le fichier `yt-dlp` est absent : un binaire vidé faute
     * d'avoir pu être supprimé est retiré une dernière fois avant l'appel.
     */
    private fun reinstallBundledYtDlp() {
        runCatching {
            val binary = ytDlpBinary()
            if (binary.exists() && binary.length() == 0L) binary.delete()
            YoutubeDL.getInstance().init_ytdlp(context, ytDlpDir())
        }
            .onFailure { diagnostics.log(TAG, Diagnostics.Level.E, "Réinstallation du yt-dlp embarqué impossible", it) }
    }

    private fun fetchShaSums(version: String): String? = runCatching {
        val url = "https://github.com/yt-dlp/yt-dlp/releases/download/$version/SHA2-256SUMS"
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                diagnostics.log(TAG, Diagnostics.Level.W, "SHA2-256SUMS : HTTP ${response.code}")
                return@runCatching null
            }
            response.body.string()
        }
    }.onFailure { diagnostics.log(TAG, Diagnostics.Level.W, "SHA2-256SUMS inaccessible", it) }.getOrNull()

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
        /** Préférences de youtubedl-android 0.18.1, écrites par sa mise à jour. */
        const val LIB_PREFS = "youtubedl-android"
        const val PREF_DLP_VERSION = "dlpVersion"
        const val PREF_DLP_VERSION_NAME = "dlpVersionName"
    }
}
