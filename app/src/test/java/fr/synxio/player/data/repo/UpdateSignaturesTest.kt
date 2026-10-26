package fr.synxio.player.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSignaturesTest {

    private val old = "aa11"
    private val new = "bb22"

    @Test
    fun `meme cle - accepte`() {
        assertTrue(UpdateSignatures.compatible(setOf(old), setOf(old), listOf(old)))
    }

    @Test
    fun `rotation - appli sur l'ancienne cle, APK signe par la lignee - accepte`() {
        assertTrue(UpdateSignatures.compatible(setOf(old), setOf(new), listOf(old, new)))
    }

    @Test
    fun `apres rotation - APK signe par l'ancienne cle seule - refuse`() {
        assertFalse(UpdateSignatures.compatible(setOf(new), setOf(old), listOf(old)))
    }

    @Test
    fun `cle inconnue sans lignee - refuse`() {
        assertFalse(UpdateSignatures.compatible(setOf(old), setOf("cc33"), listOf("cc33")))
        assertFalse(UpdateSignatures.compatible(setOf(old), emptySet(), emptyList()))
    }
}
