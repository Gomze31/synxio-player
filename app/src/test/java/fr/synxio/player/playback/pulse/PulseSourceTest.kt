package fr.synxio.player.playback.pulse

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
