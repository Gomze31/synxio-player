package fr.synxio.player.data.model

import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookProgressTest {

    /** Trois chapitres de 10 min, 20 min et 30 min : une heure en tout. */
    private val chapters = listOf(
        song(id = 1, track = 1, durationMs = 600_000),
        song(id = 2, track = 2, durationMs = 1_200_000),
        song(id = 3, track = 3, durationMs = 1_800_000),
    )

    private fun saved(vararg entries: PodcastProgressEntity) = entries.associateBy { it.songId }

    private fun at(songId: Long, positionMs: Long, lastPlayedSec: Long) =
        PodcastProgressEntity(songId = songId, positionMs = positionMs, lastPlayedSec = lastPlayedSec)

    @Test
    fun `livre jamais ecoute - premier chapitre, rien d'entame`() {
        val p = BookProgress.of(chapters, emptyMap())!!
        assertEquals(0, p.chapterIndex)
        assertEquals(0L, p.elapsedMs)
        assertEquals(3_600_000L, p.totalMs)
        assertFalse(p.started)
        assertNull(p.statusLabel())
    }

    @Test
    fun `sans chapitre, pas de livre`() {
        assertNull(BookProgress.of(emptyList(), emptyMap()))
    }

    @Test
    fun `le chapitre en cours est le dernier ecoute, chapitres precedents comptes`() {
        val p = BookProgress.of(
            chapters,
            saved(at(1, 600_000, lastPlayedSec = 100), at(2, 300_000, lastPlayedSec = 200)),
        )!!
        assertEquals(1, p.chapterIndex)
        assertEquals(2L, p.chapterSongId)
        assertEquals(300_000L, p.chapterPositionMs)
        // 10 min du chapitre 1 + 5 min du chapitre 2.
        assertEquals(900_000L, p.elapsedMs)
        assertEquals(2_700_000L, p.remainingMs)
        assertEquals(25, p.percent)
        assertEquals("Chapitre 2 sur 3 · encore 45 min", p.statusLabel())
    }

    @Test
    fun `revenir en arriere dans le livre deplace le chapitre en cours`() {
        val p = BookProgress.of(
            chapters,
            saved(at(3, 60_000, lastPlayedSec = 100), at(1, 120_000, lastPlayedSec = 300)),
        )!!
        assertEquals(0, p.chapterIndex)
        assertEquals(120_000L, p.elapsedMs)
    }

    @Test
    fun `chapitre termine - on attend au debut du suivant`() {
        val p = BookProgress.of(chapters, saved(at(1, 600_000, lastPlayedSec = 100)))!!
        assertEquals(1, p.chapterIndex)
        assertEquals(0L, p.chapterPositionMs)
        assertEquals(600_000L, p.elapsedMs)
        assertTrue(p.started)
        assertFalse(p.finished)
    }

    @Test
    fun `dernier chapitre termine - livre fini`() {
        val p = BookProgress.of(chapters, saved(at(3, 1_799_000, lastPlayedSec = 100)))!!
        assertTrue(p.finished)
        assertEquals(1f, p.fraction)
        assertEquals(100, p.percent)
        assertEquals("Terminé", p.remainingLabel())
        assertEquals("Terminé · 3 chapitres", p.statusLabel())
    }

    @Test
    fun `position au-dela de la duree bornee au chapitre`() {
        val p = BookProgress.of(chapters, saved(at(2, 5_000_000, lastPlayedSec = 1)))
        // Hors bornes : traité comme fini, donc chapitre suivant au début.
        assertEquals(2, p!!.chapterIndex)
        assertEquals(1_800_000L, p.elapsedMs)
    }

    @Test
    fun `positions d'autres morceaux ignorees`() {
        val p = BookProgress.of(chapters, saved(at(99, 100_000, lastPlayedSec = 999)))!!
        assertEquals(0, p.chapterIndex)
        assertFalse(p.started)
    }

    @Test
    fun `progression en direct pendant la lecture`() {
        val p = BookProgress.live(chapters, currentSongId = 3, positionMs = 900_000)!!
        assertEquals(2, p.chapterIndex)
        assertEquals(2_700_000L, p.elapsedMs)
        assertEquals(75, p.percent)
        assertEquals("Chapitre 3 sur 3", p.chapterLabel())
        assertNull(BookProgress.live(chapters, currentSongId = 42, positionMs = 0))
    }

    @Test
    fun `reprise - position enregistree, chapitre fini repart du debut`() {
        assertEquals(0L, BookProgress.resumePosition(0, 600_000))
        assertEquals(0L, BookProgress.resumePosition(-5, 600_000))
        assertEquals(125_000L, BookProgress.resumePosition(125_000, 600_000))
        assertEquals(0L, BookProgress.resumePosition(600_000, 600_000))
        assertEquals(0L, BookProgress.resumePosition(598_000, 600_000))
        // Durée inconnue : on fait confiance à la position.
        assertEquals(125_000L, BookProgress.resumePosition(125_000, 0))
    }

    @Test
    fun `temps restant - moins d'une minute et heures`() {
        val almost = BookProgress.live(chapters, currentSongId = 3, positionMs = 1_770_000)!!
        assertEquals("moins d'une minute", almost.remainingLabel())
        val start = BookProgress.live(chapters, currentSongId = 1, positionMs = 0)!!
        assertEquals("encore 1 h", start.remainingLabel())
    }

    @Test
    fun `ordre du livre suit les numeros de piste`() {
        val shuffled = listOf(chapters[2], chapters[0], chapters[1])
        assertEquals(listOf(1L, 2L, 3L), shuffled.inBookOrder().map { it.id })
    }

    @Test
    fun `livre a reprendre - le dernier ecoute parmi les livres entames`() {
        val other = listOf(song(id = 10, track = 1, durationMs = 600_000))
        val done = listOf(song(id = 20, track = 1, durationMs = 600_000))
        val progress = mapOf(
            "/livres/a" to BookProgress.of(chapters, saved(at(2, 60_000, lastPlayedSec = 100)))!!,
            "/livres/b" to BookProgress.of(other, saved(at(10, 60_000, lastPlayedSec = 300)))!!,
            // Plus récent, mais fini : pas proposé.
            "/livres/c" to BookProgress.of(done, saved(at(20, 600_000, lastPlayedSec = 900)))!!,
            // Jamais écouté : pas proposé.
            "/livres/d" to BookProgress.of(chapters, emptyMap())!!,
        )
        val (path, p) = progress.bookToResume()!!
        assertEquals("/livres/b", path)
        assertEquals(300L, p.lastPlayedSec)
        assertNull(mapOf("/livres/d" to progress.getValue("/livres/d")).bookToResume())
    }
}
