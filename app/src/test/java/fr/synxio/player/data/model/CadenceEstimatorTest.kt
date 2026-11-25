package fr.synxio.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceEstimatorTest {

    /** Pas réguliers à [spm] pas/min de [fromMs] à [untilMs] ; rend l'instant du dernier pas. */
    private fun CadenceEstimator.walk(spm: Float, fromMs: Long, untilMs: Long): Long {
        val period = 60_000f / spm
        var t = fromMs.toFloat()
        var last = fromMs
        while (t <= untilMs) {
            last = t.toLong()
            onStep(last)
            tick(last)
            t += period
        }
        return last
    }

    @Test
    fun `170 pas par minute reguliers - cadence 170 a 2 pres et stable apres 30 s`() {
        val e = CadenceEstimator()
        e.walk(170f, 0L, 30_000L)
        assertEquals(170f, e.cadence, 2f)
        assertTrue(e.stable)
    }

    @Test
    fun `un seul pas - aucune cadence`() {
        val e = CadenceEstimator()
        e.onStep(1_000L)
        assertEquals(0f, e.cadence, 0f)
        assertFalse(e.stable)
    }

    @Test
    fun `arret - la cadence retombe a 0 apres 10 s sans pas, pas avant`() {
        val e = CadenceEstimator()
        val last = e.walk(170f, 0L, 30_000L)
        e.tick(last + 9_000L)
        assertTrue(e.cadence > 100f)
        e.tick(last + 10_001L)
        assertEquals(0f, e.cadence, 0f)
        assertFalse(e.stable)
        assertEquals(null, e.lastStepAt)
    }

    @Test
    fun `stabilite - instable avant 20 s d'historique, puis stable`() {
        val e = CadenceEstimator()
        e.walk(160f, 0L, 15_000L)
        assertFalse(e.stable)
        e.walk(160f, 15_000L, 25_000L)
        assertTrue(e.stable)
    }

    @Test
    fun `changement de rythme - devient instable puis se stabilise sur la nouvelle cadence`() {
        val e = CadenceEstimator()
        e.walk(150f, 0L, 30_000L)
        assertTrue(e.stable)
        e.walk(180f, 30_000L, 40_000L)
        assertFalse(e.stable)
        e.walk(180f, 40_000L, 70_000L)
        assertTrue(e.stable)
        assertEquals(180f, e.cadence, 2f)
    }
}
