package fr.synxio.player.data.repo.ytdlp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * Disposition reprise de youtubedl-android : le binaire vit dans `yt-dlp/yt-dlp`, la
 * sauvegarde dans un dossier voisin, hors du dossier que la bibliothèque supprime.
 */
class YtDlpUpdaterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val root: File by lazy { tmp.newFolder("youtubedl-android") }
    private val binary: File by lazy { File(File(root, "yt-dlp").apply { mkdirs() }, "yt-dlp") }
    private val backup: File by lazy { File(root, "yt-dlp.bak") }

    private fun binary(content: String): File = binary.apply { writeText(content) }

    private fun updater(
        runLibraryUpdate: () -> YtDlpUpdater.LibraryOutcome,
        installedVersion: () -> String?,
        fetchSums: (String) -> String?,
    ) = YtDlpUpdater(binary, backup, runLibraryUpdate, installedVersion, fetchSums)

    @Test
    fun `binaire absent - refuse sans rien toucher`() {
        val result = updater({ error("ne doit pas être appelé") }, { "v" }, { null }).update()
        assertTrue(result is YtDlpUpdater.Result.Rejected)
        assertFalse(backup.exists())
    }

    @Test
    fun `deja a jour - rien ne change, pas de bak`() {
        binary("ancien")
        val result = updater({ YtDlpUpdater.LibraryOutcome.ALREADY_UP_TO_DATE }, { "2026.10.22" }, { error("pas de réseau attendu") }).update()
        assertEquals(YtDlpUpdater.Result.AlreadyUpToDate, result)
        assertEquals("ancien", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `somme egale - nouveau binaire garde, bak supprime`() {
        binary("ancien")
        val updater = updater(
            runLibraryUpdate = { binary.writeText("nouveau"); YtDlpUpdater.LibraryOutcome.UPDATED },
            installedVersion = { "2026.11.01" },
            fetchSums = { version -> "${sha256("nouveau".toByteArray())}  yt-dlp\n# $version" },
        )
        assertEquals(YtDlpUpdater.Result.Updated("2026.11.01"), updater.update())
        assertEquals("nouveau", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `somme differente - ancien binaire restaure`() {
        binary("ancien")
        val updater = updater(
            runLibraryUpdate = { binary.writeText("trafique"); YtDlpUpdater.LibraryOutcome.UPDATED },
            installedVersion = { "2026.11.01" },
            fetchSums = { "${sha256("nouveau".toByteArray())}  yt-dlp" },
        )
        val result = updater.update()
        assertTrue(result is YtDlpUpdater.Result.Rejected)
        assertFalse((result as YtDlpUpdater.Result.Rejected).lost)
        assertEquals("ancien", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `dossier supprime et recree par la bibliotheque, somme differente - ancien binaire restaure a l'octet pres`() {
        val original = ByteArray(4096) { (it * 31 + 7).toByte() }
        binary.writeBytes(original)
        val updater = updater(
            runLibraryUpdate = {
                // Comportement de youtubedl-android 0.18.1 : deleteDirectory, mkdirs, copie.
                val dir = binary.parentFile!!
                assertTrue(dir.deleteRecursively())
                assertTrue(dir.mkdirs())
                binary.writeText("trafique")
                YtDlpUpdater.LibraryOutcome.UPDATED
            },
            installedVersion = { "2026.11.01" },
            fetchSums = { "${sha256("nouveau".toByteArray())}  yt-dlp" },
        )
        val result = updater.update()
        assertTrue(result is YtDlpUpdater.Result.Rejected)
        assertFalse((result as YtDlpUpdater.Result.Rejected).lost)
        assertArrayEquals(original, binary.readBytes())
        assertFalse(backup.exists())
    }

    @Test
    fun `sauvegarde perdue apres mise a jour - refus LOST, binaire non verifie supprime`() {
        binary("ancien")
        val updater = updater(
            runLibraryUpdate = {
                backup.delete()
                binary.writeText("trafique")
                YtDlpUpdater.LibraryOutcome.UPDATED
            },
            installedVersion = { "2026.11.01" },
            fetchSums = { "${sha256("nouveau".toByteArray())}  yt-dlp" },
        )
        val result = updater.update()
        assertEquals(YtDlpUpdater.Result.Rejected(YtDlpUpdater.LOST, lost = true), result)
        assertTrue(!binary.exists() || binary.length() == 0L)
        assertFalse(backup.exists())
    }

    @Test
    fun `sommes inaccessibles - ancien binaire restaure`() {
        binary("ancien")
        val updater = updater(
            runLibraryUpdate = { binary.writeText("nouveau"); YtDlpUpdater.LibraryOutcome.UPDATED },
            installedVersion = { "2026.11.01" },
            fetchSums = { null },
        )
        assertTrue(updater.update() is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `la bibliotheque leve - ancien binaire restaure`() {
        binary("ancien")
        val updater = updater(
            runLibraryUpdate = { binary.writeText("moitie"); throw IllegalStateException("réseau") },
            installedVersion = { "x" },
            fetchSums = { null },
        )
        assertTrue(updater.update() is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
        assertFalse(backup.exists())
    }

    private fun updated(installed: () -> String?, sums: (String) -> String?) = updater(
        runLibraryUpdate = { binary.writeText("nouveau"); YtDlpUpdater.LibraryOutcome.UPDATED },
        installedVersion = installed,
        fetchSums = sums,
    )

    @Test
    fun `fetchSums leve - ancien binaire restaure, pas de bak`() {
        binary("ancien")
        val result = updated({ "2026.11.01" }, { throw java.io.IOException("timeout") }).update()
        assertTrue(result is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `version inconnue - ancien binaire restaure, pas de bak`() {
        binary("ancien")
        val result = updated({ null }, { null }).update()
        assertTrue(result is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `binaire supprime par la bibliotheque - ancien binaire restaure`() {
        binary("ancien")
        val updater = updater(
            runLibraryUpdate = { binary.delete(); YtDlpUpdater.LibraryOutcome.UPDATED },
            installedVersion = { "2026.11.01" },
            fetchSums = { "${sha256("nouveau".toByteArray())}  yt-dlp" },
        )
        assertTrue(updater.update() is YtDlpUpdater.Result.Rejected)
        assertEquals("ancien", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `bak preexistant et binaire corrompu - ancien binaire repris`() {
        binary("corrompu")
        backup.writeText("sain")
        val result = updater({ YtDlpUpdater.LibraryOutcome.ALREADY_UP_TO_DATE }, { "v" }, { null }).update()
        assertEquals(YtDlpUpdater.Result.AlreadyUpToDate, result)
        assertEquals("sain", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `recoverInterrupted - sauvegarde presente, ancien binaire repris`() {
        binary("non verifie")
        backup.writeText("sain")
        assertTrue(YtDlpUpdater.recoverInterrupted(binary, backup))
        assertEquals("sain", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `recoverInterrupted - dossier du binaire absent, recree et repris`() {
        backup.writeText("sain")
        binary.parentFile!!.deleteRecursively()
        assertTrue(YtDlpUpdater.recoverInterrupted(binary, backup))
        assertEquals("sain", binary.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `recoverInterrupted - pas de sauvegarde, rien ne change`() {
        binary("actuel")
        assertFalse(YtDlpUpdater.recoverInterrupted(binary, backup))
        assertEquals("actuel", binary.readText())
    }
}
