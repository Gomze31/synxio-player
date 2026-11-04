package fr.synxio.player.core.diag

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic local : journal des zones sensibles, rapports de crash, export par partage.
 * Rien ne quitte l'appareil sans un geste de l'utilisateur.
 */
@Singleton
class Diagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Level { D, I, W, E }

    data class Summary(val logBytes: Long, val crashCount: Int)

    private val dir = File(context.filesDir, "diagnostics")
    private val log = DiagnosticLog(dir)
    private val prefs = context.getSharedPreferences("synxio_diagnostics", Context.MODE_PRIVATE)
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)

    fun log(tag: String, level: Level, message: String, throwable: Throwable? = null) {
        when (level) {
            Level.D -> Log.d(tag, message, throwable)
            Level.I -> Log.i(tag, message, throwable)
            Level.W -> Log.w(tag, message, throwable)
            Level.E -> Log.e(tag, message, throwable)
        }
        val line = buildString {
            append(timestamp.format(Date())).append(' ').append(level.name).append('/').append(tag)
            append(": ").append(message)
            if (throwable != null) append('\n').append(Log.getStackTraceString(throwable))
        }
        log.append(line)
    }

    /** À appeler une fois, depuis `Application.onCreate`. Le handler précédent est conservé. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrashReport(thread, error) }
                // Le journal lui-même est peut-être en cause : on passe par logcat.
                .onFailure { Log.e("Diagnostics", "Écriture du rapport de crash impossible", it) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrashReport(thread: Thread, error: Throwable) {
        dir.mkdirs()
        val runtime = Runtime.getRuntime()
        val header = linkedMapOf(
            "Version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Appareil" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Mémoire libre" to "${(runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)} Mo",
            "Date" to timestamp.format(Date()),
        )
        val report = CrashReportFormatter.format(header, thread.name, error, log.recent(100))
        File(dir, "crash-${fileStamp.format(Date())}.txt").writeText(report)
        crashReports().drop(MAX_CRASH_REPORTS).forEach { it.delete() }
    }

    /** Rapports du plus récent au plus ancien. */
    private fun crashReports(): List<File> =
        dir.listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".txt") }
            .orEmpty()
            .sortedByDescending { it.name }

    fun hasUnseenCrash(): Boolean {
        val latest = crashReports().firstOrNull() ?: return false
        return latest.lastModified() > prefs.getLong(KEY_CRASH_SEEN, 0L)
    }

    fun markCrashesSeen() {
        prefs.edit().putLong(KEY_CRASH_SEEN, System.currentTimeMillis()).apply()
    }

    fun summary(): Summary = Summary(logBytes = log.sizeBytes(), crashCount = crashReports().size)

    /**
     * Concatène rapports et journal dans le cache partageable, et prépare l'intent de
     * partage. `null` si rien n'a pu être écrit.
     */
    fun exportIntent(): Intent? = runCatching {
        val out = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(out, "synxio-diagnostic-${fileStamp.format(Date())}.txt")
        file.writeText(buildString {
            appendLine("Synxio Player ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            crashReports().forEach { report ->
                appendLine("##### ${report.name}")
                appendLine(report.readText())
                appendLine()
            }
            appendLine("##### Journal")
            append(log.snapshot())
        })
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Diagnostic Synxio Player")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.onFailure { log("Diagnostics", Level.W, "Export du diagnostic impossible", it) }.getOrNull()

    fun clear() {
        log.clear()
        crashReports().forEach { it.delete() }
        File(context.cacheDir, "diagnostics").deleteRecursively()
        markCrashesSeen()
    }

    private companion object {
        const val MAX_CRASH_REPORTS = 5
        const val KEY_CRASH_SEEN = "crash_seen_at"
    }
}
