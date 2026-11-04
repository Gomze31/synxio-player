package fr.synxio.player.core.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `garde seulement les dernieres lignes en memoire`() {
        val log = DiagnosticLog(tmp.newFolder(), maxLines = 3)
        listOf("a", "b", "c", "d").forEach(log::append)
        assertEquals(listOf("b", "c", "d"), log.recent(10))
        assertEquals(listOf("c", "d"), log.recent(2))
    }

    @Test
    fun `ecrit chaque ligne dans synxio log`() {
        val dir = tmp.newFolder()
        val log = DiagnosticLog(dir)
        log.append("premiere")
        log.append("seconde")
        assertEquals("premiere\nseconde\n", File(dir, "synxio.log").readText())
    }

    @Test
    fun `bascule vers synxio log 1 quand la taille est depassee`() {
        val dir = tmp.newFolder()
        val log = DiagnosticLog(dir, maxFileBytes = 20)
        log.append("0123456789")          // 11 octets
        log.append("0123456789")          // 22 octets : au-dessus du seuil après écriture
        log.append("apres rotation")      // déclenche la rotation avant d'écrire
        assertTrue(File(dir, "synxio.log.1").exists())
        assertEquals("apres rotation\n", File(dir, "synxio.log").readText())
        assertTrue(log.snapshot().startsWith("0123456789\n0123456789\n"))
        assertTrue(log.snapshot().endsWith("apres rotation\n"))
    }

    @Test
    fun `clear supprime les fichiers et vide la memoire`() {
        val dir = tmp.newFolder()
        val log = DiagnosticLog(dir)
        log.append("x")
        log.clear()
        assertFalse(File(dir, "synxio.log").exists())
        assertEquals(emptyList<String>(), log.recent(10))
        assertEquals(0L, log.sizeBytes())
    }

    @Test
    fun `la taille reste bornee meme si le renommage echoue`() {
        val dir = tmp.newFolder()
        // Un dossier non vide en guise de synxio.log.1 fait échouer renameTo de façon déterministe.
        File(dir, "synxio.log.1").apply { mkdir(); File(this, "bloque").writeText("x") }
        val log = DiagnosticLog(dir, maxFileBytes = 20)
        repeat(10) { log.append("0123456789") }
        assertTrue(File(dir, "synxio.log").length() <= 20 + 11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `refuse maxLines nul`() {
        DiagnosticLog(tmp.newFolder(), maxLines = 0)
    }
}
