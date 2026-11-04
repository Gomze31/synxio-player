package fr.synxio.player.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSignaturesTest {

    private val old = "aa11"
    private val new = "bb22"

    @Test
    fun `meme cle que l'empreinte epinglee - accepte`() {
        assertTrue(UpdateSignatures.compatible(setOf(new), setOf(new), listOf(old, new), pinned = new))
    }

    @Test
    fun `rotation - appli sur l'ancienne cle, APK epingle avec lignee - accepte`() {
        assertTrue(UpdateSignatures.compatible(setOf(old), setOf(new), listOf(old, new), pinned = new))
    }

    @Test
    fun `empreinte comparee sans tenir compte de la casse`() {
        assertTrue(UpdateSignatures.compatible(setOf("AA11"), setOf("BB22"), listOf("aa11", "bb22"), pinned = "bb22"))
    }

    @Test
    fun `APK signe par l'ancienne cle seule - refuse meme si l'appli est encore dessus`() {
        assertFalse(UpdateSignatures.compatible(setOf(old), setOf(old), listOf(old), pinned = new))
    }

    @Test
    fun `APK epingle mais lignee sans la cle installee - refuse`() {
        assertFalse(UpdateSignatures.compatible(setOf("cc33"), setOf(new), listOf(old, new), pinned = new))
    }

    @Test
    fun `empreinte epinglee vide - tout est refuse`() {
        assertFalse(UpdateSignatures.compatible(setOf(new), setOf(new), listOf(new), pinned = ""))
    }

    @Test
    fun `signataires multiples ou absents - refuse`() {
        assertFalse(UpdateSignatures.compatible(setOf(old), setOf(new, "cc33"), listOf(old, new), pinned = new))
        assertFalse(UpdateSignatures.compatible(emptySet(), setOf(new), listOf(new), pinned = new))
        assertFalse(UpdateSignatures.compatible(setOf(old), emptySet(), emptyList(), pinned = new))
    }
}
