package fr.synxio.player.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** État du modèle de reconnaissance vocale sur l'appareil. */
sealed interface VoiceModelState {
    data object Missing : VoiceModelState
    /** [progress] entre 0 et 1, ou `null` si la taille est inconnue. */
    data class Downloading(val progress: Float?) : VoiceModelState
    data object Ready : VoiceModelState
    data class Failed(val message: String) : VoiceModelState
}

/**
 * Modèle français de Vosk (~42 Mo), téléchargé à la première activation du mode vocal
 * plutôt qu'embarqué : seuls ceux qui s'en servent paient la place.
 *
 * L'archive est vérifiée par empreinte SHA-256 avant d'être décompressée : un fichier
 * tronqué ou modifié n'est jamais chargé par la bibliothèque native.
 */
@Singleton
class VoskModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    private val root = File(context.filesDir, "voice")
    val modelDir = File(root, MODEL_NAME)
    private val marker = File(modelDir, ".complete")

    private val _state = MutableStateFlow(if (marker.exists()) VoiceModelState.Ready else VoiceModelState.Missing)
    val state: StateFlow<VoiceModelState> = _state.asStateFlow()

    private val mutex = Mutex()

    val isReady: Boolean get() = marker.exists()

    /** Télécharge et installe le modèle s'il manque. Rend `true` s'il est prêt à la fin. */
    suspend fun ensureInstalled(): Boolean = mutex.withLock {
        if (marker.exists()) return@withLock true
        _state.value = VoiceModelState.Downloading(null)
        val result = withContext(Dispatchers.IO) { runCatching { install() } }
        _state.value = result.fold(
            onSuccess = { VoiceModelState.Ready },
            onFailure = { VoiceModelState.Failed(it.message ?: "Téléchargement impossible") },
        )
        result.isSuccess
    }

    private fun install() {
        root.mkdirs()
        val archive = File(context.cacheDir, "$MODEL_NAME.zip.part")
        try {
            download(archive)
            check(sha256(archive) == MODEL_SHA256) { "Fichier du modèle corrompu, réessaie" }
            modelDir.deleteRecursively()
            archive.inputStream().use { unzipModel(it, modelDir) }
            marker.createNewFile()
        } finally {
            archive.delete()
        }
    }

    private fun download(target: File) {
        // Délai de lecture large : 42 Mo sur une connexion lente.
        val client = http.newBuilder().readTimeout(java.time.Duration.ofSeconds(60)).build()
        client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
            check(response.isSuccessful) { "Serveur du modèle indisponible (${response.code})" }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total != null) {
                            val percent = (copied * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = VoiceModelState.Downloading(percent / 100f)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_NAME = "vosk-model-small-fr-0.22"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val MODEL_SHA256 = "cabf6180e177eb9b3a9a9d43a437bd5e549f3a7d09525e5d69a3fed787be12ad"
    }
}

/**
 * Décompresse l'archive du modèle dans [target], sans son dossier racine
 * (`vosk-model-small-fr-0.22/am/…` devient `am/…`). Une entrée qui sortirait de [target]
 * (« ../ ») fait échouer l'installation.
 */
internal fun unzipModel(input: InputStream, target: File) {
    target.mkdirs()
    val base = target.canonicalFile
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val relative = entry.name.replace('\\', '/').substringAfter('/', "")
            if (relative.isEmpty()) continue
            val out = File(base, relative).canonicalFile
            check(out.path.startsWith(base.path + File.separator)) { "Archive du modèle invalide" }
            if (entry.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
        }
    }
}
