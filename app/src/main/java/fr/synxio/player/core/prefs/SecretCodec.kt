package fr.synxio.player.core.prefs

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM avec IV aléatoire de 12 octets, stocké devant le chiffré, le tout en
 * Base64. Indépendant d'Android : la clé vient de l'appelant (Keystore en production,
 * `KeyGenerator` en test).
 */
class SecretCodec(private val key: SecretKey) {

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(stored: String): String {
        val bytes = Base64.getDecoder().decode(stored)
        require(bytes.size > IV_BYTES) { "Secret chiffré trop court" }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val encrypted = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
