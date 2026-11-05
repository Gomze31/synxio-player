package fr.synxio.player.playback.pulse

import androidx.media3.common.C
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
class PulseSourceTest {
    private var analyses = 0
    private val loud = AudioPulse(rms = 0.9f)

    // backgroundScope : le partage `stateIn` reste actif après le test, runTest l'annule.
    private fun TestScope.source() = PulseSource(
        scope = backgroundScope,
        tickMs = 33,
        produce = { analyses++; loud },
        sink = PulseSink(PulseRingBuffer()),
    )

    @Test
    fun `sans abonne - aucune analyse`() = runTest {
        val source = source()
        source.setPlaying(true)
        advanceTimeBy(1_000)
        assertEquals(0, analyses)
        assertEquals(AudioPulse.SILENT, source.pulse.value)
    }

    @Test
    fun `abonne et en lecture - analyse a 30 Hz`() = runTest {
        val source = source()
        source.setPlaying(true)
        val job: Job = launch { source.pulse.collect { } }
        advanceTimeBy(1_000)
        assertTrue("analyses=$analyses", analyses in 28..32)
        assertEquals(loud, source.pulse.value)
        job.cancel()
    }

    @Test
    fun `abonne mais en pause - muet sans analyse`() = runTest {
        val source = source()
        source.setPlaying(false)
        val job = launch { source.pulse.collect { } }
        advanceTimeBy(500)
        assertEquals(0, analyses)
        assertEquals(AudioPulse.SILENT, source.pulse.value)
        job.cancel()
    }

    @Test
    fun `desabonnement - l'analyse s'arrete apres 500 ms`() = runTest {
        val source = source()
        source.setPlaying(true)
        val job = launch { source.pulse.collect { } }
        advanceTimeBy(330)
        job.cancel()
        val before = analyses
        advanceTimeBy(2_000)
        assertTrue(analyses - before <= 16)
    }

    private fun sineBuffer(): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(2 * 2048).order(ByteOrder.nativeOrder())
        for (i in 0 until 2048) buf.putShort((sin(2.0 * PI * 440.0 * i / 44_100.0) * 20_000).toInt().toShort())
        buf.flip()
        return buf
    }

    @Test
    fun `tampon qui n'avance plus - le pouls retombe au silence`() = runTest {
        val analysis = PulseSource.Analysis(backgroundScope)
        val source = PulseSource(analysis)
        analysis.sink.flush(44_100, 1, C.ENCODING_PCM_16BIT)
        source.setPlaying(true)
        val job = launch { source.pulse.collect { } }
        // Un son est arrivé, puis plus rien (Cast, décrochage).
        analysis.sink.handleBuffer(sineBuffer())
        advanceTimeBy(40)
        assertNotEquals(AudioPulse.SILENT, source.pulse.value)
        advanceTimeBy(200)
        assertEquals(AudioPulse.SILENT, source.pulse.value)
        job.cancel()
    }

    @Test
    fun `source jamais alimentee - muette apres 4 ticks`() = runTest {
        val analysis = PulseSource.Analysis(backgroundScope)
        val source = PulseSource(analysis)
        analysis.sink.flush(44_100, 1, C.ENCODING_PCM_16BIT)
        source.setPlaying(true)
        val job = launch { source.pulse.collect { } }
        advanceTimeBy(140)
        assertEquals(AudioPulse.SILENT, source.pulse.value)
        job.cancel()
    }

    @Test
    fun `tampon alimente entre les ticks - le pouls reste vivant`() = runTest {
        val analysis = PulseSource.Analysis(backgroundScope)
        val source = PulseSource(analysis)
        analysis.sink.flush(44_100, 1, C.ENCODING_PCM_16BIT)
        source.setPlaying(true)
        val job = launch { source.pulse.collect { } }
        repeat(10) {
            analysis.sink.handleBuffer(sineBuffer())
            advanceTimeBy(33)
            runCurrent()
        }
        assertNotEquals(AudioPulse.SILENT, source.pulse.value)
        assertTrue(source.pulse.value.rms > 0.3f)
        job.cancel()
    }
}
