package fr.synxio.player.data.repo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class RepoSafetyLimitsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // --- Sauvegarde : taille plafonnée ---

    @Test
    fun `une sauvegarde sous la limite est lue entierement`() {
        val data = ByteArray(1000) { it.toByte() }
        assertArrayEquals(data, BackupRepository.readCapped(ByteArrayInputStream(data), 1000))
    }

    @Test
    fun `une sauvegarde trop volumineuse est refusee`() {
        try {
            BackupRepository.readCapped(ByteArrayInputStream(ByteArray(1001)), 1000)
            fail("IOException attendue")
        } catch (expected: IOException) {
            // attendu
        }
    }

    // --- FFmpeg : délai réellement appliqué ---

    private val java = File(System.getProperty("java.home"), "bin/java").absolutePath

    private fun javaProgram(body: String): ProcessBuilder {
        val source = tmp.newFile("Prog${System.nanoTime()}.java")
        source.writeText("public class Prog { public static void main(String[] a) throws Exception { $body } }")
        return ProcessBuilder(java, source.absolutePath).redirectErrorStream(true)
    }

    @Test
    fun `un processus trop long est tue a l expiration du delai`() {
        val started = System.currentTimeMillis()
        val result = BoundedProcess.run(javaProgram("Thread.sleep(60000);"), timeoutMs = 1_500)
        val elapsed = System.currentTimeMillis() - started
        assertTrue(result.timedOut)
        assertEquals(null, result.exitCode)
        assertTrue("durée $elapsed ms", elapsed < 20_000)
    }

    @Test
    fun `une sortie volumineuse ne bloque pas le processus`() {
        // ~2 Mo de sortie : bien plus que le tampon d'un pipe.
        val result = BoundedProcess.run(
            javaProgram("for (int i = 0; i < 40000; i++) System.out.println(\"ligne de journal FFmpeg numero \" + i);"),
            timeoutMs = 60_000,
        )
        assertFalse(result.timedOut)
        assertEquals(0, result.exitCode)
        assertTrue(result.outputTail.length <= 2000)
        assertTrue(result.outputTail.contains("39999"))
    }
}
