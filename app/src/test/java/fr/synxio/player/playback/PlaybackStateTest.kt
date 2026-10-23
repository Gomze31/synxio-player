package fr.synxio.player.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStateTest {

    @Test
    fun `progression bornee entre 0 et 1`() {
        assertEquals(0f, PlaybackPosition().progress)
        assertEquals(0.5f, PlaybackPosition(positionMs = 50, durationMs = 100).progress)
        assertEquals(1f, PlaybackPosition(positionMs = 500, durationMs = 100).progress)
        assertEquals(0f, PlaybackPosition(positionMs = 50, durationMs = 0).progress)
    }

    @Test
    fun `etat de la boucle A-B`() {
        assertEquals(AbLoopState.OFF, PlayerUiState().loopState)
        assertEquals(AbLoopState.START_SET, PlayerUiState(loopStartMs = 1_000).loopState)
        assertEquals(AbLoopState.LOOPING, PlayerUiState(loopStartMs = 1_000, loopEndMs = 5_000).loopState)
    }
}

/**
 * Le temps virtuel est avancé explicitement : `advanceUntilIdle` n'exécute que les
 * tâches de premier plan, et la minuterie tourne dans `backgroundScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    @Test
    fun `duree fixe declenche le rappel a l echeance`() = runTest {
        val timer = SleepTimer(backgroundScope)
        var fired = 0
        timer.setCallback { fired++ }

        timer.start(durationMs = 3_000, finishCurrentTrack = false)
        assertTrue(timer.state.value.active)
        assertEquals(3_000, timer.state.value.remainingMs)

        advanceTimeBy(1_500)
        assertEquals(0, fired)
        assertTrue(timer.state.value.remainingMs in 1_000..2_000)

        advanceTimeBy(60_000)
        assertEquals(1, fired)
        assertFalse(timer.state.value.active)
    }

    @Test
    fun `fin de morceau laisse l etat actif jusqu a la transition`() = runTest {
        val timer = SleepTimer(backgroundScope)
        var fired = 0
        timer.setCallback { fired++ }

        timer.start(durationMs = 2_000, finishCurrentTrack = true)
        assertFalse(timer.shouldStopAtTrackEnd())
        advanceTimeBy(60_000)

        assertEquals(0, fired)
        assertTrue(timer.state.value.active)
        assertTrue(timer.shouldStopAtTrackEnd())

        timer.cancel()
        assertFalse(timer.state.value.active)
        assertFalse(timer.shouldStopAtTrackEnd())
    }

    @Test
    fun `prolonger et annuler`() = runTest {
        val timer = SleepTimer(backgroundScope)
        var fired = 0
        timer.setCallback { fired++ }

        timer.start(durationMs = 2_000, finishCurrentTrack = false)
        advanceTimeBy(1_000)
        timer.extend(5_000)
        assertTrue(timer.state.value.remainingMs >= 5_000)

        advanceTimeBy(3_000)
        assertEquals(0, fired)

        timer.cancel()
        advanceTimeBy(60_000)
        assertEquals(0, fired)
        assertFalse(timer.state.value.active)
    }

    @Test
    fun `prolonger sans minuterie active ne fait rien`() = runTest {
        val timer = SleepTimer(backgroundScope)
        timer.extend(5_000)
        assertFalse(timer.state.value.active)
    }
}
