package fr.synxio.player.data.repo.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class YtDlpUpdaterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun binary(content: String): File = File(tmp.newFolder(), "yt-dlp").apply { writeText(content) }

    @Test
    fun `binaire absent - refuse sans rien toucher`() {
        val binary = File(tmp.newFolder(), "yt-dlp")
        val result = YtDlpUpdater(binary, { error("ne doit pas être appelé") }, { "v" }, { null }).update()
        assertTrue(result is YtDlpUpdater.Result.Rejected)
        assertFalse(File(binary.parentFile, "yt-dlp.bak").exists())
    }

    @Test
    fun `deja a jour - rien ne change, pas de bak`() {
        val binary = binary("ancien")
        val result = YtDlpUpdater(binary, { YtDlpUpdater.LibraryOutcome.ALREADY_UP_TO_DATE }, { "2026.10.22" }, { error("pas de réseau attendu") }).update()
        assertEquals(YtDlpUpdater.Result.AlreadyUpToDate, result)
        assertEquals("ancien", binary.readText())
        assertFalse(File(binary.parentFile, "yt-dlp.bak").exists())
    }

    @Test
    fun `somme egale - nouveau binaire garde, bak supprime`() {
        val binary = binary("ancien")
        val updater = YtDlpUpdater(
            binary,
            runLibraryUpdate = { binary.writeText("nouveau"); YtDlpUpdater.LibraryOutcome.UPDATED },
            installedVersion = { "2026.11.01" },
            fetchSums = { version -> "${sha256("nouveau".toByteArray())}  yt-dlp\n# $version" },
        )
        assertEquals(YtDlpUpdater.Result.Updated("2026.11.01"), updater.update())
        assertEquals("nouveau", binary.readText())
        assertFalse(File(binary.parentFile, "yt-dlp.bak").exists())
    }

    @Test
    fun `somme differente - ancien binaire restaure`() {
        val binary = binary("ancien")
        val updater = YtDlpUpdater(
            binary,
            runLibraryUpdate = { binary.writeText("trafique"); YtDlpUpdater.LibraryOutcome.UPDATED },
            installedVersion = { "2026.11.01" },
            fetchSums = { "${sha256("nouveau".toByteArray())}  yt-dlp" },
        )
        val result = updater.update()
        assertTrue(result is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
        assertFalse(File(binary.parentFile, "yt-dlp.bak").exists())
    }

    @Test
    fun `sommes inaccessibles - ancien binaire restaure`() {
        val binary = binary("ancien")
        val updater = YtDlpUpdater(
            binary,
            runLibraryUpdate = { binary.writeText("nouveau"); YtDlpUpdater.LibraryOutcome.UPDATED },
            installedVersion = { "2026.11.01" },
            fetchSums = { null },
        )
        assertTrue(updater.update() is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
    }

    @Test
    fun `la bibliotheque leve - ancien binaire restaure`() {
        val binary = binary("ancien")
        val updater = YtDlpUpdater(
            binary,
            runLibraryUpdate = { binary.writeText("moitie"); throw IllegalStateException("réseau") },
            installedVersion = { "x" },
            fetchSums = { null },
        )
        assertTrue(updater.update() is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
    }
}
