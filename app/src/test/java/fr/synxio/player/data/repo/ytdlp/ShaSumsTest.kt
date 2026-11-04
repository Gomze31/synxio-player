package fr.synxio.player.data.repo.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShaSumsTest {

    private val sums = """
        1111111111111111111111111111111111111111111111111111111111111111  yt-dlp.exe
        ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789  yt-dlp
        2222222222222222222222222222222222222222222222222222222222222222 *yt-dlp.tar.gz
    """.trimIndent()

    @Test
    fun `trouve la ligne du fichier, en minuscules`() {
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            ShaSums.digestFor(sums, "yt-dlp"),
        )
    }

    @Test
    fun `accepte le marqueur binaire etoile`() {
        assertEquals("2".repeat(64), ShaSums.digestFor(sums, "yt-dlp.tar.gz"))
    }

    @Test
    fun `ne confond pas yt-dlp et yt-dlp exe`() {
        assertEquals("1".repeat(64), ShaSums.digestFor(sums, "yt-dlp.exe"))
    }

    @Test
    fun `absent ou vide`() {
        assertNull(ShaSums.digestFor(sums, "autre"))
        assertNull(ShaSums.digestFor("", "yt-dlp"))
    }

    @Test
    fun `rejette une somme qui n'est pas du SHA-256`() {
        assertNull(ShaSums.digestFor("zz  yt-dlp", "yt-dlp"))
    }
}
