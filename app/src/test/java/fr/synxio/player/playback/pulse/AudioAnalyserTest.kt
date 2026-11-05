package fr.synxio.player.playback.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioAnalyserTest {
    private val rate = 44_100
    private fun sine(hz: Float, amplitude: Float = 0.8f, size: Int = 1024) =
        FloatArray(size) { (amplitude * sin(2.0 * PI * hz * it / rate)).toFloat() }
    private fun noise(size: Int = 1024, seed: Int = 7): FloatArray {
        val r = java.util.Random(seed.toLong())
        return FloatArray(size) { (r.nextFloat() * 2f - 1f) }
    }
    private fun settle(analyser: AudioAnalyser, frame: FloatArray, frames: Int = 20): AudioPulse {
        var pulse = AudioPulse.SILENT
        repeat(frames) { pulse = analyser.analyse(frame) }
        return pulse
    }

    @Test
    fun `silence - pouls muet`() {
        val pulse = AudioAnalyser(rate).analyse(FloatArray(1024))
        assertEquals(AudioPulse.SILENT, pulse)
    }

    @Test
    fun `sinus 60 Hz - basses hautes, aigus bas`() {
        val pulse = settle(AudioAnalyser(rate), sine(60f))
        assertTrue("bass=${pulse.bass}", pulse.bass > 0.7f)
        assertTrue("treble=${pulse.treble}", pulse.treble < 0.1f)
        assertTrue("rms=${pulse.rms}", pulse.rms > 0.7f)
    }

    @Test
    fun `sinus 8 kHz - aigus hauts, basses basses`() {
        val pulse = settle(AudioAnalyser(rate), sine(8_000f))
        assertTrue("treble=${pulse.treble}", pulse.treble > 0.7f)
        assertTrue("bass=${pulse.bass}", pulse.bass < 0.1f)
    }

    @Test
    fun `bruit blanc - toutes les bandes vivent`() {
        val pulse = settle(AudioAnalyser(rate), noise())
        assertTrue(pulse.bands.joinToString(), pulse.bands.all { it > 0.1f })
        assertEquals(32, pulse.bands.size)
    }

    @Test
    fun `attaque - onset proche de 1 puis retombe sous 0,1 en 400 ms`() {
        val analyser = AudioAnalyser(rate)
        settle(analyser, FloatArray(1024), frames = 10)
        val hit = analyser.analyse(sine(200f))
        assertTrue("onset=${hit.onset}", hit.onset > 0.8f)
        val after = settle(analyser, sine(200f), frames = 12)
        assertTrue("onset=${after.onset}", after.onset < 0.1f)
    }

    @Test
    fun `tout reste dans 0 a 1`() {
        val pulse = settle(AudioAnalyser(rate), noise(seed = 3).map { it * 3f }.toFloatArray())
        listOf(pulse.bass, pulse.mid, pulse.treble, pulse.rms, pulse.onset).forEach { assertTrue(it in 0f..1f) }
        assertTrue(pulse.bands.all { it in 0f..1f })
    }
}
