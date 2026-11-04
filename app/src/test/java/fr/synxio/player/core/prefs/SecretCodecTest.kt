package fr.synxio.player.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import javax.crypto.KeyGenerator

class SecretCodecTest {

    private fun codec(): SecretCodec = SecretCodec(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())

    @Test
    fun `aller retour`() {
        val codec = codec()
        val stored = codec.encrypt("https://discord.com/api/webhooks/1/abc")
        assertEquals("https://discord.com/api/webhooks/1/abc", codec.decrypt(stored))
    }

    @Test
    fun `deux chiffrements du meme texte different (IV aleatoire)`() {
        val codec = codec()
        assertNotEquals(codec.encrypt("secret"), codec.encrypt("secret"))
    }

    @Test
    fun `le texte chiffre ne contient pas le clair`() {
        val stored = codec().encrypt("motdepasse")
        assertEquals(false, stored.contains("motdepasse"))
    }

    @Test(expected = Exception::class)
    fun `une autre cle ne dechiffre pas`() {
        val stored = codec().encrypt("secret")
        codec().decrypt(stored)
    }

    @Test
    fun `unicode preserve`() {
        val codec = codec()
        assertEquals("clé — été ✓", codec.decrypt(codec.encrypt("clé — été ✓")))
    }
}
