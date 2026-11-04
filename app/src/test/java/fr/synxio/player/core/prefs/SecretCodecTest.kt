package fr.synxio.player.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64
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

    @Test(expected = GeneralSecurityException::class)
    fun `une autre cle ne dechiffre pas`() {
        val stored = codec().encrypt("secret")
        codec().decrypt(stored)
    }

    @Test
    fun `unicode preserve`() {
        val codec = codec()
        assertEquals("clé — été ✓", codec.decrypt(codec.encrypt("clé — été ✓")))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `un bit modifie dans le tag est rejete`() {
        val codec = codec()
        val bytes = Base64.getDecoder().decode(codec.encrypt("secret"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        codec.decrypt(Base64.getEncoder().encodeToString(bytes))
    }
}
