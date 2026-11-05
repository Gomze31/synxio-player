package fr.synxio.player.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private var now = 0L
    private var elapsed = 0
    private var paused = 0
    private var resumed = 0

    private fun TestScope.timer() = SleepTimer(backgroundScope) { now }.apply {
        setCallback { elapsed++ }
        setStillnessCallbacks({ paused++ }, { resumed++ })
    }

    @Test
    fun `echeance exacte malgre des ticks en retard`() = runTest {
        val t = timer()
        t.start(10_000, finishCurrentTrack = false)
        now = 7_000; advanceTimeBy(1_001)
        assertEquals(3_000L, t.state.value.remainingMs)
        now = 10_000; advanceTimeBy(1_001)
        assertEquals(1, elapsed)
        assertFalse(t.state.value.active)
    }

    @Test
    fun `prolongation - repart de l'echeance restante`() = runTest {
        val t = timer()
        t.start(10_000, false)
        now = 4_000; advanceTimeBy(1_001)
        t.extend(5_000)
        assertEquals(11_000L, t.state.value.remainingMs)
    }

    @Test
    fun `fondu adaptatif - 1 a 20 min, 0,15 a 60 s, puis fondu de fin`() {
        assertEquals(1f, adaptiveFade(25 * 60_000L), 1e-4f)
        assertEquals(1f, adaptiveFade(20 * 60_000L), 1e-4f)
        assertEquals(0.15f, adaptiveFade(60_000L), 1e-4f)
        assertEquals(0.075f, adaptiveFade(30_000L), 1e-4f)
        assertEquals(0.575f, adaptiveFade(10 * 60_000L + 30_000L), 1e-3f)
    }

    @Test
    fun `fadeMultiplier - adaptatif suit adaptiveFade, classique inchange`() {
        assertEquals(0.15f, SleepTimerState(active = true, remainingMs = 60_000, adaptive = true).fadeMultiplier, 1e-4f)
        assertEquals(1f, SleepTimerState(active = true, remainingMs = 60_000 + 1, adaptive = false).fadeMultiplier, 1e-4f)
        assertEquals(0.5f, SleepTimerState(active = true, remainingMs = 30_000, adaptive = false).fadeMultiplier, 1e-4f)
    }

    @Test
    fun `immobilite - pause, puis arret apres 30 s de grace`() = runTest {
        val t = timer()
        t.start(60 * 60_000L, false, adaptive = true)
        t.onStillness()
        assertEquals(1, paused)
        assertTrue(t.state.value.pausedForStillness)
        now = 31_000; advanceTimeBy(31_001)
        assertEquals(1, elapsed)
        assertFalse(t.state.value.active)
    }

    @Test
    fun `secousse pendant la grace - reprise et 15 min de plus`() = runTest {
        val t = timer()
        t.start(60 * 60_000L, false, adaptive = true)
        t.onStillness()
        now = 10_000; advanceTimeBy(10_001)
        t.onShake()
        assertEquals(1, resumed)
        assertFalse(t.state.value.pausedForStillness)
        assertEquals(60 * 60_000L - 10_000L + 15 * 60_000L, t.state.value.remainingMs)
        now = 40_000; advanceTimeBy(30_001)
        assertEquals(0, elapsed)
    }

    @Test
    fun `secousse hors grace - 15 min de plus, pas en mode classique`() = runTest {
        val t = timer()
        t.start(10 * 60_000L, false, adaptive = true)
        t.onShake()
        assertEquals(25 * 60_000L, t.state.value.remainingMs)
        t.cancel()
        t.start(10 * 60_000L, false, adaptive = false)
        t.onShake()
        assertEquals(10 * 60_000L, t.state.value.remainingMs)
    }

    @Test
    fun `immobilite en mode classique - ignoree`() = runTest {
        val t = timer()
        t.start(10 * 60_000L, false)
        t.onStillness()
        assertEquals(0, paused)
    }
}
