package fr.synxio.player.core.diag

import java.io.PrintWriter
import java.io.StringWriter

/** Met en forme un rapport de crash lisible tel quel dans un éditeur de texte. */
object CrashReportFormatter {

    fun format(
        header: Map<String, String>,
        threadName: String,
        error: Throwable,
        recentLines: List<String>,
    ): String = buildString {
        appendLine("=== Synxio Player — rapport de crash ===")
        header.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine("Thread: $threadName")
        appendLine()
        appendLine("--- Trace ---")
        appendLine(stackTraceOf(error))
        appendLine("--- Dernières lignes du journal ---")
        recentLines.forEach { appendLine(it) }
    }

    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
