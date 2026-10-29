package fr.synxio.player.playback

import androidx.media3.common.Player
import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePolicyTest {

    private val music = song(id = 1, genre = "Rock")
    private val chapter = song(id = 2, durationMs = 600_000).copy(isAudiobook = true)
    private val episode = song(id = 3, durationMs = 1_200_000).copy(isPodcast = true)

    private fun saved(positionMs: Long) = PodcastProgressEntity(songId = 0, positionMs = positionMs, lastPlayedSec = 1)

    @Test
    fun `seuls podcasts et livres gardent une position`() {
        assertFalse(ResumePolicy.tracksProgress(music))
        assertTrue(ResumePolicy.tracksProgress(chapter))
        assertTrue(ResumePolicy.tracksProgress(episode))
    }

    @Test
    fun `fin de piste - naturelle ou repetee seulement`() {
        assertTrue(ResumePolicy.endsTrack(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO))
        assertTrue(ResumePolicy.endsTrack(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT))
        assertFalse(ResumePolicy.endsTrack(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK))
        assertFalse(ResumePolicy.endsTrack(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
    }

    @Test
    fun `position enregistree - la duree une fois fini, sinon la position suivie`() {
        assertEquals(600_000L, ResumePolicy.positionToSave(chapter, finished = true, trackedPositionMs = 12_000))
        assertEquals(12_000L, ResumePolicy.positionToSave(chapter, finished = false, trackedPositionMs = 12_000))
    }

    @Test
    fun `reprise - au demarrage d'un livre ou podcast, jamais apres un zap`() {
        assertTrue(ResumePolicy.restoresOnTransition(chapter, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
        assertTrue(ResumePolicy.restoresOnTransition(episode, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO))
        assertFalse(ResumePolicy.restoresOnTransition(chapter, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK))
        assertFalse(ResumePolicy.restoresOnTransition(music, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
        assertFalse(ResumePolicy.restoresOnTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO))
    }

    @Test
    fun `cible de reprise - position enregistree, rien au debut ou une fois fini`() {
        assertEquals(125_000L, ResumePolicy.restoreTarget(chapter, saved(125_000)))
        assertNull(ResumePolicy.restoreTarget(chapter, saved(0)))
        assertNull(ResumePolicy.restoreTarget(chapter, saved(600_000)))
        assertNull(ResumePolicy.restoreTarget(chapter, null))
    }

    @Test
    fun `retour en arriere - apres une vraie pause sur le meme morceau`() {
        var now = 100_000L
        val rewind = AutoRewind { now }
        assertNull("aucune pause connue", rewind.onResumed(10, "a", 60_000))

        rewind.onPaused("a")
        now += 2_000
        assertNull("pause trop courte", rewind.onResumed(10, "a", 60_000))
        now += 10_000
        assertNull("autre morceau", rewind.onResumed(10, "b", 60_000))
        assertNull("réglage à zéro", rewind.onResumed(0, "a", 60_000))
        assertEquals(50_000L, rewind.onResumed(10, "a", 60_000))
        // Appliqué une fois : la reprise suivante ne recule plus.
        assertNull(rewind.onResumed(10, "a", 50_000))

        rewind.onPaused("a")
        now += 60_000
        assertEquals(0L, rewind.onResumed(30, "a", 4_000))
    }

    @Test
    fun `auto-dj - meme genre ou artiste, hors file, cinq au plus, rien pour un podcast ou un livre`() {
        val library = (10L..20L).map { song(id = it, genre = if (it % 2 == 0L) "Rock" else "Jazz") }
            .map { it.copy(artistId = 99) }
        val picked = AutoDj.pick(library, music.copy(artistId = 1), queued = setOf(10L, 12L)) { it }
        assertEquals(listOf(14L, 16L, 18L, 20L), picked.map { it.id })

        val many = (30L..45L).map { song(id = it, genre = "Rock") }
        assertEquals(AutoDj.BATCH_SIZE, AutoDj.pick(many, music, emptySet()) { it }.size)
        assertTrue(AutoDj.pick(library, episode, emptySet()).isEmpty())
        assertTrue(AutoDj.pick(library, chapter, emptySet()).isEmpty())
    }
}
