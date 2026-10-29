package fr.synxio.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningTrackerTest {

    private var now = 1_000L
    private val tracker = ListeningTracker(uptime = { now }, wallClockSec = { 1_700_000_000L })

    @Test
    fun `le temps ecoute ne court que pendant la lecture`() {
        tracker.start(songId = 7, playing = true)
        now += 20_000
        tracker.accumulate() // pause
        now += 60_000 // en pause : ne compte pas
        tracker.onResumed()
        now += 10_000
        val listen = tracker.finish(skipped = false, durationMs = 180_000)!!
        assertEquals(30_000L, listen.listenedMs)
        assertEquals(7L, listen.songId)
        assertEquals(1_700_000_000L, listen.startedAtSec)
        assertTrue(listen.counted)
    }

    @Test
    fun `demarre en pause - rien ne court avant la reprise`() {
        tracker.start(songId = 7, playing = false)
        now += 50_000
        assertNull(tracker.finish(skipped = false, durationMs = 180_000))
    }

    @Test
    fun `saut - compte seulement au-dela de la moitie`() {
        tracker.start(songId = 1, playing = true)
        now += 60_000
        assertFalse(tracker.finish(skipped = true, durationMs = 180_000)!!.counted)

        tracker.start(songId = 2, playing = true)
        now += 90_000
        assertTrue(tracker.finish(skipped = true, durationMs = 180_000)!!.counted)
    }

    @Test
    fun `effleurement sous trois secondes - rien a enregistrer, temps conserve`() {
        tracker.start(songId = 1, playing = true)
        now += 2_000
        assertNull(tracker.finish(skipped = true, durationMs = 180_000))
        assertEquals(2_000L, tracker.listenedMs)
    }

    @Test
    fun `fin d'ecoute - compteur remis a zero`() {
        tracker.start(songId = 1, playing = true)
        now += 10_000
        tracker.finish(skipped = false, durationMs = 180_000)
        assertEquals(0L, tracker.listenedMs)
    }

    @Test
    fun `position - celle du lecteur tant qu'il est sur le morceau, sinon la derniere relevee`() {
        tracker.start(songId = 5, playing = true)
        assertEquals(42_000L, tracker.position(currentSongId = 5, livePositionMs = 42_000))
        // Le lecteur quitte le morceau : la position de l'ancien est relevée avant la transition.
        tracker.notePosition(songId = 5, positionMs = 61_000)
        assertEquals(61_000L, tracker.position(currentSongId = 6, livePositionMs = 0))
        // Une position d'un autre morceau ne touche pas celle du morceau suivi.
        tracker.notePosition(songId = 6, positionMs = 3_000)
        assertEquals(61_000L, tracker.position(currentSongId = 6, livePositionMs = 3_000))
    }

    @Test
    fun `nouveau morceau - position et suivi repartent de zero`() {
        tracker.start(songId = 5, playing = true)
        tracker.notePosition(5, 61_000)
        tracker.start(songId = null, playing = false)
        assertFalse(tracker.isTracking)
        assertEquals(ListeningTracker.NONE, tracker.songId)
        assertEquals(0L, tracker.position(currentSongId = 9, livePositionMs = 1_000))
    }

    @Test
    fun `position negative ramenee a zero`() {
        tracker.start(songId = 5, playing = true)
        assertEquals(0L, tracker.position(currentSongId = 5, livePositionMs = -40))
    }
}
