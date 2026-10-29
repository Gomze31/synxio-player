package fr.synxio.player.data.model

import fr.synxio.player.data.media.Chapter
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterNavigationTest {

    /** Un fichier de 30 min en trois chapitres de 10 min, puis un fichier de 5 min. */
    private val files = listOf(
        song(id = 1, track = 1, durationMs = 1_800_000),
        song(id = 2, title = "Épilogue", track = 2, durationMs = 300_000),
    )
    private val embedded = mapOf(1L to (0 until 3).map { Chapter("Partie ${it + 1}", it * 600_000L, (it + 1) * 600_000L) })
    private val chapters = bookChapters(files, embedded)

    private fun at(songId: Long, positionMs: Long) = BookProgress.live(files, songId, positionMs, embedded)!!

    @Test
    fun `suivant - chapitre integre puis fichier suivant, rien apres le dernier`() {
        assertEquals("Partie 2", chapters.nextAfter(at(1, 100_000))?.title)
        val next = chapters.nextAfter(at(1, 1_300_000))!!
        assertEquals("Épilogue", next.title)
        assertEquals(2L, next.songId)
        assertEquals(0L, next.startInFileMs)
        assertNull(chapters.nextAfter(at(2, 10_000)))
    }

    @Test
    fun `precedent - debut du chapitre apres trois secondes, sinon chapitre d'avant`() {
        val restart = chapters.previousFrom(at(1, 700_000))!!
        assertEquals("Partie 2", restart.title)
        assertEquals(600_000L, restart.startInFileMs)

        val back = chapters.previousFrom(at(1, 601_000))!!
        assertEquals("Partie 1", back.title)

        // Au tout début du livre : on reste au premier chapitre.
        assertEquals("Partie 1", chapters.previousFrom(at(1, 1_000))?.title)
        // Début du second fichier : retour au dernier chapitre du premier.
        assertEquals("Partie 3", chapters.previousFrom(at(2, 2_000))?.title)
    }

    @Test
    fun `fin du chapitre - temps reel selon la vitesse`() {
        val p = at(1, 900_000) // 5 min avant la fin de la partie 2
        assertEquals(300_000L, p.msToChapterEnd(1f))
        assertEquals(200_000L, p.msToChapterEnd(1.5f))
        assertEquals(0L, at(1, 1_800_000).msToChapterEnd(1f))
    }
}
