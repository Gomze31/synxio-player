package fr.synxio.player.data.model

import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeStateTest {

    private fun at(positionMs: Long) = PodcastProgressEntity(songId = 1, positionMs = positionMs, lastPlayedSec = 1)

    @Test
    fun `etat d'un episode selon sa position`() {
        assertEquals(EpisodeState.NEW, episodeState(null, 600_000))
        assertEquals(EpisodeState.NEW, episodeState(at(0), 600_000))
        assertEquals(EpisodeState.IN_PROGRESS, episodeState(at(120_000), 600_000))
        assertEquals(EpisodeState.PLAYED, episodeState(at(599_000), 600_000))
        assertEquals(EpisodeState.PLAYED, episodeState(at(600_000), 600_000))
    }

    @Test
    fun `marquer lu - chaque morceau a sa fin`() {
        val songs = listOf(song(id = 1, durationMs = 600_000), song(id = 2, durationMs = 900_000))
        val marks = markedPlayed(songs, nowSec = 42)
        assertEquals(listOf(600_000L, 900_000L), marks.map { it.positionMs })
        assertTrue(marks.all { it.lastPlayedSec == 42L })
        assertTrue(marks.all { episodeState(it, songs.first { s -> s.id == it.songId }.durationMs) == EpisodeState.PLAYED })
    }

    @Test
    fun `livre marque lu - termine malgre des horodatages identiques`() {
        val chapters = listOf(
            song(id = 1, track = 1, durationMs = 600_000),
            song(id = 2, track = 2, durationMs = 600_000),
            song(id = 3, track = 3, durationMs = 600_000),
        )
        val saved = markedPlayed(chapters, nowSec = 100).associateBy { it.songId }
        val p = BookProgress.of(chapters, saved)!!
        assertTrue(p.finished)
        assertEquals(100, p.percent)
    }
}
