package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class MemoryEngineTest {
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val today: LocalDate = LocalDate.of(2026, 11, 5)
    private val songs = (1L..40L).map { song(id = it, title = "T$it").copy(albumId = (it - 1) / 10 + 1, track = ((it - 1) % 10 + 1).toInt()) }
    private val albums = (1L..4L).map { a -> Album(id = a, title = "Album $a", artist = "Artiste", artistId = 1, year = 2020, songs = songs.filter { it.albumId == a }) }

    private fun play(id: Long, date: LocalDate, hour: Int = 12, listenedMs: Long = 180_000) =
        PlayHistory(songs[(id - 1).toInt()], date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant(), listenedMs)

    private fun build(history: List<PlayHistory>, today: LocalDate = this.today) =
        MemoryEngine.build(songs, albums, history, today, zone)

    private inline fun <reified T : Memory> List<Memory>.one(): T? = filterIsInstance<T>().firstOrNull()

    @Test
    fun `ce jour-la - trois titres il y a un an, ordonnes par nombre de lectures`() {
        val d = today.minusYears(1)
        val history = listOf(play(1, d), play(2, d), play(2, d, 13), play(3, d), play(4, d.plusDays(1)))
        val m = build(history).filterIsInstance<Memory.ThatDay>().single()
        assertEquals(1, m.yearsAgo)
        assertEquals(listOf(2L, 1L, 3L), m.songs.map { it.id })
        assertEquals("thatday-1", m.key)
    }

    @Test
    fun `ce jour-la - moins de trois titres, pas de carte`() {
        val d = today.minusYears(2)
        assertNull(build(listOf(play(1, d), play(2, d))).one<Memory.ThatDay>())
    }

    @Test
    fun `capsule - mois le plus ecoute entre 6 et 24 mois, 25 titres max, stable dans la semaine`() {
        val month = YearMonth.of(2025, 9)
        val history = (1L..30L).flatMap { id -> List(2) { play(id, month.atDay(3)) } } +
            (1L..12L).map { play(it, today.minusMonths(2).withDayOfMonth(2)) } // trop récent
        val a = build(history).one<Memory.TimeCapsule>()!!
        assertEquals(month, a.month)
        assertEquals(25, a.songs.size)
        val b = build(history, today.plusDays(2)).one<Memory.TimeCapsule>()!!
        assertEquals(a.month, b.month)
        assertEquals("capsule-2025-09", a.key)
    }

    @Test
    fun `capsule - absente sans mois a dix lectures`() {
        val history = (1L..9L).map { play(it, today.minusMonths(8).withDayOfMonth(1)) }
        assertNull(build(history).one<Memory.TimeCapsule>())
    }

    @Test
    fun `premieres fois - premiere lecture dans les 30 jours, triees par relectures`() {
        val recent = today.minusDays(10)
        val history = listOf(
            play(5, today.minusDays(60)), play(5, recent), // déjà connu : exclu
            play(6, recent), play(6, recent, 14), play(6, recent, 15),
            play(7, recent), play(7, recent, 14),
            play(8, recent),
        )
        val m = build(history).one<Memory.FirstTimes>()!!
        assertEquals(listOf(6L, 7L, 8L), m.songs.map { it.id })
    }

    @Test
    fun `albums jamais termines - entames, moins de la moitie joues, pas ecoutes depuis 30 jours`() {
        val old = today.minusDays(45)
        val history = listOf(
            play(1, old), play(2, old), // album 1 : 2/10 joués, ancien → carte
            play(11, old), play(12, old), play(13, old), play(14, old), play(15, old), // album 2 : 5/10 → exclu
            play(21, today.minusDays(3)), // album 3 : récent → exclu
        )
        val cards = build(history).filterIsInstance<Memory.UnfinishedAlbum>()
        assertEquals(listOf(1L), cards.map { it.album.id })
        assertEquals(2, cards.single().playedCount)
        assertEquals((3L..10L).toList(), cards.single().songs.map { it.id })
        assertTrue(cards.single().subtitle.contains("2 titres sur 10"))
    }

    @Test
    fun `historique vide - aucune carte`() {
        assertTrue(build(emptyList()).isEmpty())
    }
}
