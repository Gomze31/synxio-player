package fr.synxio.player.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeEnvelopeTest {

    private val points = (0..100).map { it / 100f }

    @Test
    fun `equal-power garde une puissance constante a un pour cent`() {
        for (p in points) {
            val out = CrossfadeEnvelope.fadeOut(p, CrossfadeCurve.EQUAL_POWER)
            val inn = CrossfadeEnvelope.fadeIn(p, CrossfadeCurve.EQUAL_POWER)
            val power = out * out + inn * inn
            assertTrue("p=$p puissance=$power", power in 0.99f..1.01f)
        }
    }

    @Test
    fun `bornes du fondu`() {
        for (curve in CrossfadeCurve.entries) {
            assertEquals(1f, CrossfadeEnvelope.fadeOut(0f, curve), 1e-6f)
            assertEquals(0f, CrossfadeEnvelope.fadeIn(0f, curve), 1e-6f)
            assertEquals(0f, CrossfadeEnvelope.fadeOut(1f, curve), 1e-6f)
            assertEquals(1f, CrossfadeEnvelope.fadeIn(1f, curve), 1e-6f)
            // Hors bornes : écrêté, jamais négatif ni supérieur à 1.
            assertEquals(1f, CrossfadeEnvelope.fadeOut(-0.5f, curve), 1e-6f)
            assertEquals(1f, CrossfadeEnvelope.fadeIn(1.5f, curve), 1e-6f)
        }
    }

    @Test
    fun `lineaire somme des gains egale un`() {
        for (p in points) {
            val sum = CrossfadeEnvelope.fadeOut(p, CrossfadeCurve.LINEAR) + CrossfadeEnvelope.fadeIn(p, CrossfadeCurve.LINEAR)
            assertEquals(1f, sum, 1e-5f)
        }
    }

    @Test
    fun `douce somme des gains egale un et monotone`() {
        var lastIn = -1f
        for (p in points) {
            val out = CrossfadeEnvelope.fadeOut(p, CrossfadeCurve.SMOOTH)
            val inn = CrossfadeEnvelope.fadeIn(p, CrossfadeCurve.SMOOTH)
            assertEquals(1f, out + inn, 1e-5f)
            assertTrue("fondu entrant non monotone en $p", inn >= lastIn)
            lastIn = inn
        }
        // Plus douce qu'equal-power au départ : à 10 % le suivant est encore presque muet.
        assertTrue(CrossfadeEnvelope.fadeIn(0.1f, CrossfadeCurve.SMOOTH) < CrossfadeEnvelope.fadeIn(0.1f, CrossfadeCurve.EQUAL_POWER))
    }

    @Test
    fun `equal-power au milieu vaut racine de deux sur deux`() {
        assertEquals(0.7071f, CrossfadeEnvelope.fadeOut(0.5f, CrossfadeCurve.EQUAL_POWER), 1e-3f)
        assertEquals(0.7071f, CrossfadeEnvelope.fadeIn(0.5f, CrossfadeCurve.EQUAL_POWER), 1e-3f)
    }

    @Test
    fun `nom inconnu retombe sur equal-power`() {
        assertEquals(CrossfadeCurve.EQUAL_POWER, CrossfadeCurve.fromName(null))
        assertEquals(CrossfadeCurve.EQUAL_POWER, CrossfadeCurve.fromName("ancienne"))
        assertEquals(CrossfadeCurve.SMOOTH, CrossfadeCurve.fromName("SMOOTH"))
    }
}
