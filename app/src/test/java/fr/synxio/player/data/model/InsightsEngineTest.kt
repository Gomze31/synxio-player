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
import java.time.ZoneOffset

class InsightsEngineTest {
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val today: LocalDate = LocalDate.of(2026, 11, 25)
    private val songs = (1L..10L).map { song(id = it, title = "T$it", artist = if (it % 2 == 0L) "Pair" else "Impair") }

    private val minute = 60_000L

    private fun play(id: Long, date: LocalDate, hour: Int = 12, minute: Int = 0, listenedMs: Long = 10 * this.minute) =
        PlayHistory(songs[(id - 1).toInt()], date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant(), listenedMs)

    private fun traits(bpm: Float, energy: Float = 0.5f) =
        TrackTraits(bpm = bpm, bpmConfidence = 1f, keyIndex = 0, keyConfidence = 1f, energy = energy, introMs = 0, outroMs = 0)

    private fun compute(history: List<PlayHistory>, traits: Map<String, TrackTraits> = emptyMap(), today: LocalDate = this.today) =
        InsightsEngine.compute(history, traits, today, zone)

    @Test
    fun `heatmap - 365 jours jusqu'a aujourd'hui, minutes additionnees par jour local`() {
        val d = today.minusDays(5)
        // 23 h 30 UTC la veille = 00 h 30 à Paris le jour même : l'écoute tombe sur le bon jour local.
        val lateUtc = PlayHistory(songs[0], today.minusDays(1).atTime(23, 30).toInstant(ZoneOffset.UTC), 7 * minute)
        val history = listOf(play(1, d, listenedMs = 30 * minute), play(2, d, hour = 21, listenedMs = 18 * minute), lateUtc)
        val insights = compute(history)

        assertEquals(365, insights.heatmap.size)
        assertEquals(today.minusDays(364), insights.heatmap.first().date)
        assertEquals(today, insights.heatmap.last().date)
        assertEquals(48, insights.heatmap.single { it.date == d }.minutes)
        assertEquals(7, insights.heatmap.last().minutes)
        assertEquals(0, insights.heatmap.single { it.date == today.minusDays(1) }.minutes)
    }

    @Test
    fun `heatmap - trente lectures de dix secondes font cinq minutes, pas trente`() {
        val history = List(30) { play(1, today, listenedMs = 10_000) }
        assertEquals(5, compute(history).heatmap.last().minutes)
    }

    @Test
    fun `serie - courante depuis hier quand aujourd'hui n'a pas encore ses cinq minutes, record avec un trou`() {
        val history = (1L..3L).map { play(1, today.minusDays(it)) } +      // 3 jours : hier, avant-hier, il y a 3 jours
            listOf(play(1, today.minusDays(4), listenedMs = 4 * minute)) +  // 4 min : ne compte pas, c'est le trou
            (5L..9L).map { play(1, today.minusDays(it)) }                   // 5 jours d'affilée
        val streak = compute(history).streak
        assertEquals(3, streak.current)
        assertEquals(5, streak.best)

        val withToday = compute(history + play(1, today, listenedMs = 5 * minute)).streak
        assertEquals(4, withToday.current)

        val todayShort = compute(history + play(1, today, listenedMs = 2 * minute)).streak
        assertEquals(3, todayShort.current)
    }

    @Test
    fun `serie - rompue avant-hier, courante a zero`() {
        val history = (3L..6L).map { play(1, today.minusDays(it)) }
        val streak = compute(history).streak
        assertEquals(0, streak.current)
        assertEquals(4, streak.best)
    }

    @Test
    fun `horloge - minutes par heure locale et heure de pointe`() {
        val history = listOf(
            play(1, today, hour = 8, listenedMs = 10 * minute),
            play(2, today, hour = 8, minute = 30, listenedMs = 5 * minute),
            play(3, today.minusDays(1), hour = 18, listenedMs = 30 * minute),
        )
        val insights = compute(history)
        assertEquals(24, insights.clock.size)
        assertEquals(15, insights.clock[8])
        assertEquals(30, insights.clock[18])
        assertEquals(0, insights.clock[12])
        assertEquals(18, insights.peakHour)
    }

    @Test
    fun `mois contre mois - janvier face a decembre de l'annee precedente`() {
        val january = LocalDate.of(2027, 1, 15)
        val history = listOf(
            play(1, LocalDate.of(2027, 1, 3), listenedMs = 20 * minute),  // Impair
            play(2, LocalDate.of(2027, 1, 4), listenedMs = 6 * minute),   // Pair
            play(2, LocalDate.of(2027, 1, 10), listenedMs = 4 * minute),  // Pair, même titre
            play(3, LocalDate.of(2026, 12, 20), listenedMs = 10 * minute),
            play(4, LocalDate.of(2026, 12, 21), listenedMs = 25 * minute),
            play(6, LocalDate.of(2026, 12, 31), listenedMs = 25 * minute),
            play(5, LocalDate.of(2026, 11, 30), listenedMs = 90 * minute), // hors des deux mois
        )
        val m = compute(history, today = january).monthOverMonth
        assertEquals(YearMonth.of(2027, 1), m.month)
        assertEquals(30, m.minutes)
        assertEquals(60, m.previousMinutes)
        assertEquals(-30, m.deltaMinutes)
        assertEquals(2, m.distinctSongs)
        assertEquals(3, m.previousDistinctSongs)
        assertEquals("Impair", m.topArtist)
        assertEquals("Pair", m.previousTopArtist)
    }

    @Test
    fun `tempo - BPM pondere par la duree ecoutee, repartition, mois precedent, titre sans traits ignore`() {
        val traits = mapOf(
            songs[0].path to traits(90f, energy = 0.4f),
            songs[1].path to traits(150f, energy = 0.8f),
        )
        val history = listOf(
            play(1, today.minusDays(2), listenedMs = 1 * minute),
            play(2, today.minusDays(3), listenedMs = 3 * minute),
            play(3, today.minusDays(4), listenedMs = 10 * minute),            // pas de traits
            play(2, today.minusDays(35), listenedMs = 2 * minute),            // fenêtre précédente
            play(1, today.minusDays(35), listenedMs = 2 * minute),
            play(2, today.minusDays(70), listenedMs = 60 * minute),           // hors des deux fenêtres
        )
        val tempo = compute(history, traits).tempo!!
        assertEquals(135f, tempo.averageBpm, 0.01f)
        assertEquals(0.7f, tempo.averageEnergy, 0.001f)
        assertEquals(0.25f, tempo.distribution.getValue(TempoBand.SLOW), 0.001f)
        assertEquals(0f, tempo.distribution.getValue(TempoBand.MEDIUM), 0.001f)
        assertEquals(0.75f, tempo.distribution.getValue(TempoBand.FAST), 0.001f)
        assertEquals(120f, tempo.previousAverageBpm!!, 0.01f)
    }

    @Test
    fun `tempo - absent sans ecoute analysee, mois precedent absent sans ecoute analysee`() {
        assertNull(compute(listOf(play(3, today))).tempo)
        val traits = mapOf(songs[0].path to traits(120f))
        val tempo = compute(listOf(play(1, today)), traits).tempo!!
        assertEquals(120f, tempo.averageBpm, 0.01f)
        assertNull(tempo.previousAverageBpm)
        assertEquals(TempoBand.MEDIUM, TempoBand.of(100f))
        assertEquals(TempoBand.MEDIUM, TempoBand.of(130f))
        assertEquals(TempoBand.FAST, TempoBand.of(130.5f))
        assertEquals(TempoBand.SLOW, TempoBand.of(99.9f))
    }

    @Test
    fun `premieres fois - decouvert dans les 30 jours et reecoute trois fois, tries par ecoutes`() {
        val history = listOf(
            play(1, today.minusDays(10)), play(1, today.minusDays(8)), play(1, today.minusDays(2)),
            play(2, today.minusDays(40)), play(2, today.minusDays(5)), play(2, today.minusDays(4)), play(2, today.minusDays(3)), // connu avant
            play(3, today.minusDays(6)), play(3, today.minusDays(1)),                                                            // deux écoutes
            play(4, today.minusDays(20)), play(4, today.minusDays(19)), play(4, today.minusDays(18)), play(4, today.minusDays(17)),
        )
        val firsts = compute(history).firsts
        assertEquals(listOf(4L, 1L), firsts.map { it.song.id })
        assertEquals(4, firsts[0].playCount)
        assertEquals(today.minusDays(20), firsts[0].firstPlayedOn)
        assertEquals(3, firsts[1].playCount)
    }

    @Test
    fun `semaine - sept derniers jours aujourd'hui compris`() {
        val history = listOf(
            play(1, today, listenedMs = 5 * minute),
            play(1, today.minusDays(6), listenedMs = 10 * minute),
            play(1, today.minusDays(7), listenedMs = 10 * minute),
        )
        assertEquals(15, compute(history).weekMinutes)
    }

    @Test
    fun `historique vide - insights vides, rien ne plante`() {
        val insights = compute(emptyList())
        assertTrue(insights.isEmpty)
        assertEquals(365, insights.heatmap.size)
        assertTrue(insights.heatmap.all { it.minutes == 0 })
        assertEquals(Streak(0, 0), insights.streak)
        assertNull(insights.peakHour)
        assertNull(insights.tempo)
        assertTrue(insights.firsts.isEmpty())
        assertEquals(0, insights.weekMinutes)
        assertNull(insights.monthOverMonth.topArtist)
    }
}
