package fr.synxio.player.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Une version publiée, telle que décrite par l'API GitHub. */
data class Release(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val publishedAt: String,
)

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val release: Release) : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Mise à jour depuis les *releases* GitHub.
 *
 * ## Pourquoi un dépôt public séparé
 *
 * Les binaires d'un dépôt privé ne sont téléchargeables qu'avec un jeton, et un jeton
 * embarqué dans un APK est extractible par quiconque récupère le fichier. Le code reste
 * donc privé, et seuls les APK sont publiés dans un dépôt public : l'application
 * n'embarque aucun secret et interroge l'API publique de GitHub.
 *
 * ## Ce que cette mécanique ne remplace pas
 *
 * `REQUEST_INSTALL_PACKAGES` est fortement restreinte par Google Play. Une application
 * distribuée sur le Play Store se met à jour par le Store, pas par ce chemin. Cet
 * updater est fait pour une distribution directe (APK partagé), pas pour cohabiter avec
 * une publication sur le Store.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {

    val isConfigured: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    /** Interroge la dernière version publiée et la compare à celle installée. */
    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext UpdateCheck.Failed("Aucun dépôt de mises à jour configuré")

        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
        val body = runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> null  // Dépôt sans aucune release publiée.
                    !response.isSuccessful -> throw IllegalStateException("HTTP ${response.code}")
                    else -> response.body?.string()
                }
            }
        }.getOrElse {
            Log.w(TAG, "Vérification impossible", it)
            return@withContext UpdateCheck.Failed("Serveur injoignable")
        } ?: return@withContext UpdateCheck.UpToDate

        val release = parseRelease(body)
            ?: return@withContext UpdateCheck.Failed("Publication sans fichier APK")

        if (isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
            UpdateCheck.Available(release)
        } else {
            UpdateCheck.UpToDate
        }
    }

    private fun parseRelease(body: String): Release? = runCatching {
        val json = JSONObject(body)
        val assets = json.getJSONArray("assets")

        var apkUrl: String? = null
        var size = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                size = asset.optLong("size")
                break
            }
        }

        Release(
            versionName = json.optString("tag_name").removePrefix("v").trim(),
            notes = json.optString("body").trim(),
            apkUrl = apkUrl ?: return null,
            sizeBytes = size,
            publishedAt = json.optString("published_at").take(10),
        )
    }.getOrNull()

    /**
     * Compare deux versions sémantiques, segment par segment.
     *
     * Une comparaison de chaînes dirait que « 2.10.0 » est antérieur à « 2.9.0 », ce qui
     * bloquerait toutes les mises à jour passé la neuvième version mineure.
     */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        if (a.isEmpty()) return false

        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    /**
     * Télécharge l'APK dans le cache, en signalant l'avancement.
     *
     * Écrit dans un fichier temporaire renommé à la fin : un téléchargement interrompu
     * laisserait sinon un APK tronqué que l'on tenterait d'installer au prochain essai.
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }

                val target = File(dir, "synxio-${release.versionName}.apk")
                val partial = File(dir, target.name + ".part")

                val request = Request.Builder()
                    .url(release.apkUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Réponse vide")
                    val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes

                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER)
                            var copied = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (total > 0) onProgress(copied.toFloat() / total)
                            }
                        }
                    }
                }

                check(partial.renameTo(target)) { "Impossible de finaliser le téléchargement" }
                target
            }
        }

    /**
     * Vérifie que l'APK téléchargé porte la même signature que l'app installée.
     *
     * Android refuserait de toute façon une mise à jour signée différemment, mais avec
     * une erreur système opaque. Échouer ici permet de dire *pourquoi* — et de ne pas
     * installer un binaire qui ne vient pas de la même source.
     */
    fun hasMatchingSignature(apk: File): Boolean = runCatching {
        if (BuildConfig.DEBUG) return true // Skip verification for debug builds
        if (android.os.Build.VERSION.SDK_INT < 28) return true // Skip verification on Android 8

        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES

        val downloaded = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?.signingInfo?.apkContentsSigners.orEmpty()
        val installed = pm.getPackageInfo(context.packageName, flags)
            .signingInfo?.apkContentsSigners.orEmpty()

        if (downloaded.isEmpty() || installed.isEmpty()) return false

        // `Array<out Signature>` : `apkContentsSigners` renvoie un tableau covariant,
        // qu'un paramètre `Array<Signature>` invariant refuserait.
        val digestsOf = { signatures: Array<out android.content.pm.Signature> ->
            signatures.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }
                .toSet()
        }
        digestsOf(downloaded) == digestsOf(installed)
    }.getOrElse {
        Log.w(TAG, "Comparaison de signature impossible", it)
        false
    }

    /** Vrai si l'utilisateur a autorisé l'installation d'applications depuis Synxio. */
    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Intent menant au réglage système « Installer des applications inconnues ». */
    fun installPermissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${context.packageName}"))

    /** Intent d'installation. Android affiche sa propre confirmation, toujours. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "UpdateRepository"
        const val UPDATE_DIR = "updates"
        const val DOWNLOAD_BUFFER = 64 * 1024
        const val USER_AGENT = "Synxio Player"
    }
}
