package fr.synxio.player.core.diag

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {

    @Test
    fun `contient entete trace et dernieres lignes`() {
        val report = CrashReportFormatter.format(
            header = linkedMapOf("Version" to "5.6.1 (20261110)", "Android" to "17 (API 37)"),
            threadName = "main",
            error = IllegalStateException("boum"),
            recentLines = listOf("12:00:00.000 I/Test: avant"),
        )
        assertTrue(report.contains("Version: 5.6.1 (20261110)"))
        assertTrue(report.contains("Android: 17 (API 37)"))
        assertTrue(report.contains("Thread: main"))
        assertTrue(report.contains("java.lang.IllegalStateException: boum"))
        assertTrue(report.contains("CrashReportFormatterTest"))
        assertTrue(report.contains("--- Dernières lignes du journal ---"))
        assertTrue(report.contains("12:00:00.000 I/Test: avant"))
    }

    @Test
    fun `inclut la cause`() {
        val error = RuntimeException("externe", IllegalArgumentException("interne"))
        val report = CrashReportFormatter.format(emptyMap(), "worker", error, emptyList())
        assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException: interne"))
    }
}
