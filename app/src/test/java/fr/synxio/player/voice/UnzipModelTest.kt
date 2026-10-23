package fr.synxio.player.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UnzipModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String?>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                content?.let { zip.write(it.toByteArray()) }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun `retire le dossier racine de l'archive`() {
        val target = tmp.newFolder("model")
        unzipModel(
            zip("vosk-model/" to null, "vosk-model/am/final.mdl" to "am", "vosk-model/conf/model.conf" to "conf")
                .inputStream(),
            target,
        )
        assertEquals("am", File(target, "am/final.mdl").readText())
        assertEquals("conf", File(target, "conf/model.conf").readText())
        assertFalse(File(target, "vosk-model").exists())
    }

    @Test
    fun `refuse une entree qui sort du dossier`() {
        val target = tmp.newFolder("model")
        val result = runCatching { unzipModel(zip("vosk-model/../../evil.txt" to "x").inputStream(), target) }
        assertTrue(result.isFailure)
        assertFalse(File(tmp.root, "evil.txt").exists())
    }
}
