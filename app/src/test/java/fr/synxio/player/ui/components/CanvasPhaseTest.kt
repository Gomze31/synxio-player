package fr.synxio.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPhaseTest {
    @Test
    fun `huit secondes a vitesse 1 - un tour`() {
        val phase = CanvasPhase()
        phase.advance(0L, speed = 1f)
        val v = phase.advance(8_000_000_000L, speed = 1f)
        // Un tour complet retombe à 0 modulo 2π : on accepte 0 ou 2π à la précision près.
        assertTrue("v=$v", v < 1e-3f || kotlin.math.abs(v - CANVAS_TWO_PI) < 1e-3f)
    }

    @Test
    fun `vitesse 2 - deux fois plus vite, sans saut au changement`() {
        val phase = CanvasPhase()
        phase.advance(0L, 1f)
        val a = phase.advance(1_000_000_000L, 1f)
        val b = phase.advance(1_000_000_000L, 2f)
        assertEquals(a, b, 1e-6f)
        val c = phase.advance(2_000_000_000L, 2f)
        assertEquals(a + CANVAS_TWO_PI / 8f * 2f, c, 1e-3f)
    }

    @Test
    fun `reste borne`() {
        val phase = CanvasPhase()
        phase.advance(0L, 1f)
        val v = phase.advance(100_000_000_000_000L, 3f)
        assertTrue(v >= 0f && v < CANVAS_TWO_PI)
    }
}
