package fr.synxio.player.data.media.dsp

import fr.synxio.player.data.model.KeyNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class TrackDspTest {

    private val rate = 22_050

    private fun clicks(bpm: Float, seconds: Int): ShortArray {
        val out = ShortArray(rate * seconds)
        val period = (60f / bpm * rate).toInt()
        var t = 0
        while (t < out.size) {
            for (i in 0 until 400) if (t + i < out.size) {
                out[t + i] = (20_000 * exp(-i / 60.0) * sin(2 * PI * 1500 * i / rate)).toInt().toShort()
            }
            t += period
        }
        return out
    }

    private fun tones(frequencies: List<Double>, seconds: Int): ShortArray = ShortArray(rate * seconds) { i ->
        (frequencies.sumOf { f -> sin(2 * PI * f * i / rate) } / frequencies.size * 12_000).toInt().toShort()
    }

    @Test
    fun `tempo - clics a 120 BPM`() {
        val e = TempoEstimator.estimate(OnsetEnvelope.compute(clicks(120f, 12), rate))
        assertTrue("bpm=${e.bpm}", e.bpm in 118f..122f)
        assertTrue("confiance=${e.confidence}", e.confidence > 0.2f)
    }

    @Test
    fun `tempo - clics a 90 et 170 BPM`() {
        assertTrue(TempoEstimator.estimate(OnsetEnvelope.compute(clicks(90f, 12), rate)).bpm in 88f..92f)
        assertTrue(TempoEstimator.estimate(OnsetEnvelope.compute(clicks(170f, 12), rate)).bpm in 167f..173f)
    }

    @Test
    fun `tempo - silence donne 0`() {
        val e = TempoEstimator.estimate(OnsetEnvelope.compute(ShortArray(rate * 6), rate))
        assertEquals(0f, e.bpm)
        assertEquals(0f, e.confidence)
    }

    @Test
    fun `tonalite - do mi sol est do majeur, la do mi est la mineur`() {
        val cMajor = KeyEstimator.estimate(Chroma.compute(tones(listOf(261.63, 329.63, 392.0), 4), rate))
        assertEquals(0, cMajor.keyIndex)
        val aMinor = KeyEstimator.estimate(Chroma.compute(tones(listOf(220.0, 261.63, 329.63), 4), rate))
        assertEquals(21, aMinor.keyIndex)
    }

    @Test
    fun `camelot et noms`() {
        assertEquals("8B", KeyNames.camelot(0))
        assertEquals("8A", KeyNames.camelot(21))
        assertEquals("9B", KeyNames.camelot(7))
        assertEquals("la mineur", KeyNames.french(21))
        assertEquals(21, KeyNames.parse("8A"))
        assertEquals(0, KeyNames.parse("Do majeur"))
        assertEquals(1, KeyNames.distance(0, 21))
        assertEquals(1, KeyNames.distance(0, 7))
        assertEquals(3, KeyNames.distance(0, 6))
    }

    @Test
    fun `intro - deux secondes de silence puis du son`() {
        val head = ShortArray(rate * 6) { i -> if (i < rate * 2) 0 else (8_000 * sin(2 * PI * 440 * i / rate)).toInt().toShort() }
        val intro = SilenceBounds.introMs(head, rate)
        assertTrue("intro=$intro", intro in 1_800L..2_300L)
        assertEquals(0L, SilenceBounds.introMs(tones(listOf(440.0), 3), rate))
    }

    @Test
    fun `energie - fort et dense bat faible et calme`() {
        val loud = EnergyScore.score(-10f, 4f)
        val quiet = EnergyScore.score(-33f, 0.2f)
        assertTrue(loud > 0.7f && quiet < 0.15f)
    }
}
