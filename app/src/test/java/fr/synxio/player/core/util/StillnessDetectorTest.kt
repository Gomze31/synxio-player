package fr.synxio.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessDetectorTest {
    @Test
    fun `immobile - stillFor grandit apres la fenetre de 10 s`() {
        val d = StillnessDetector()
        for (s in 0..20) d.sample(0.01f * (s % 2), 0f, 9.81f, s * 1_000L)
        assertTrue(d.stillFor(20_000L) >= 10_000L)
    }

    @Test
    fun `mouvement - remis a zero`() {
        val d = StillnessDetector()
        for (s in 0..20) d.sample(0f, 0f, 9.81f, s * 1_000L)
        // Une vraie secousse : 6 m/s² de plus sur un axe, la variance de la fenêtre dépasse le seuil.
        d.sample(6f, 0f, 9.81f, 21_000L)
        assertEquals(0L, d.stillFor(21_000L))
        for (s in 22..40) d.sample(0f, 0f, 9.81f, s * 1_000L)
        assertTrue(d.stillFor(40_000L) in 8_000L..20_000L)
    }

    @Test
    fun `sans echantillon - zero`() {
        assertEquals(0L, StillnessDetector().stillFor(5_000L))
    }
}
