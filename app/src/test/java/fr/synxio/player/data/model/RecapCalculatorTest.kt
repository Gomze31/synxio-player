package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class RecapCalculatorTest {

    private val zone = ZoneOffset.UTC
    /** Mercredi 21 octobre 2026. */
    private val today = LocalDate.of(2026, 10, 21)

    private val a1 = song(id = 1, title = "A1", artist = "Alpha")
    private val a2 = song(id = 2, title = "A2", artist = "Alpha")
    private val b1 = song(id = 3, title = "B1", artist = "Beta")
    private val c1 = song(id = 4, title = "C1", artist = "")

    private fun play(song: Song, at: LocalDateTime, ms: Long = 180_000) =
        PlayHistory(song, at.toInstant(zone), ms)

    private val history = listOf(
        // Septembre : hors du mois, dans l'année.
        play(a1, LocalDateTime.of(2026, 9, 2, 8, 0)),
        // Octobre.
        play(a1, LocalDateTime.of(2026, 10, 5, 21, 0)),   // lundi soir
        play(a1, LocalDateTime.of(2026, 10, 12, 21, 30)), // lundi soir
        play(a1, LocalDateTime.of(2026, 10, 19, 22, 0)),  // lundi soir
        play(b1, LocalDateTime.of(2026, 10, 19, 22, 10)), // lundi soir, découverte
        play(a2, LocalDateTime.of(2026, 10, 20, 9, 0)),   // mardi matin, découverte
        play(c1, LocalDateTime.of(2026, 10, 20, 9, 5), ms = 60_000),
    )

    private fun compute(period: RecapPeriod = RecapPeriod.MONTH) =
        RecapCalculator.compute(history, period, today, zone)

    @Test
    fun `ne compte que les ecoutes de la periode`() {
        assertEquals(6, compute().plays)
        assertEquals(7, compute(RecapPeriod.YEAR).plays)
        assertEquals(5 * 180_000L + 60_000L, compute().totalListenedMs)
    }

    @Test
    fun `classe les titres et les artistes, artiste inconnu exclu`() {
        val recap = compute()
        assertEquals(a1, recap.topSongs.first().item)
        assertEquals(3, recap.topSongs.first().plays)
        assertEquals(listOf("Alpha", "Beta"), recap.topArtists.map { it.item })
        assertEquals(4, recap.topArtists.first().plays)
    }

    @Test
    fun `trouve le jour et le moment preferes`() {
        val recap = compute()
        assertEquals(DayOfWeek.MONDAY, recap.favoriteDay)
        assertEquals(DayPart.EVENING, recap.favoriteDayPart)
    }

    @Test
    fun `une decouverte est un titre ecoute pour la premiere fois dans la periode`() {
        // a1 avait déjà été écouté en septembre : ce n'est pas une découverte d'octobre.
        assertEquals(3, compute().discoveries)
        assertEquals(4, compute(RecapPeriod.YEAR).discoveries)
    }

    @Test
    fun `compte les jours d'ecoute distincts`() {
        assertEquals(4, compute().activeDays)
    }

    @Test
    fun `sous le seuil, le recap est vide`() {
        val few = RecapCalculator.compute(history.take(3), RecapPeriod.MONTH, today, zone)
        assertTrue(few.isTooEarly)
        assertFalse(compute().isTooEarly)
    }
}
