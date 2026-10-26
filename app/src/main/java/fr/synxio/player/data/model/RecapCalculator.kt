package fr.synxio.player.data.model

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class RecapPeriod(val label: String) {
    MONTH("Ce mois-ci"),
    YEAR("Cette année");

    fun start(today: LocalDate): LocalDate = when (this) {
        MONTH -> today.withDayOfMonth(1)
        YEAR -> today.withDayOfYear(1)
    }
}

enum class DayPart(val label: String) {
    MORNING("le matin"),
    AFTERNOON("l'après-midi"),
    EVENING("le soir"),
    NIGHT("la nuit");

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..22 -> EVENING
            else -> NIGHT
        }
    }
}

@Immutable
data class RecapRank<T>(val item: T, val plays: Int, val listenedMs: Long)

/** Le bilan d'une période, tiré du journal d'écoute. */
@Immutable
data class Recap(
    val period: RecapPeriod,
    val plays: Int,
    val totalListenedMs: Long,
    val distinctSongs: Int,
    val topSongs: List<RecapRank<Song>>,
    val topArtists: List<RecapRank<String>>,
    val favoriteDay: DayOfWeek?,
    val favoriteDayPart: DayPart?,
    val discoveries: Int,
    val activeDays: Int,
) {
    /** Trop peu d'écoutes pour un bilan qui ait du sens. */
    val isTooEarly: Boolean get() = plays < RecapCalculator.MIN_PLAYS
}

object RecapCalculator {

    const val MIN_PLAYS = 5
    private const val TOP = 5

    /**
     * [history] : tout le journal, pas seulement la période. Il faut connaître les écoutes
     * antérieures pour savoir si un titre est une vraie découverte.
     */
    fun compute(
        history: List<PlayHistory>,
        period: RecapPeriod,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Recap {
        val start = period.start(today).atStartOfDay(zone).toInstant()
        val inPeriod = history.filter { !it.playedAt.isBefore(start) }

        val topSongs = inPeriod
            .groupBy { it.song.id }
            .map { (_, plays) -> RecapRank(plays.first().song, plays.size, plays.sumOf { it.listenedMs }) }
            .sortedWith(compareByDescending<RecapRank<Song>> { it.plays }.thenByDescending { it.listenedMs })
            .take(TOP)

        val topArtists = inPeriod
            .filter { it.song.displayArtist != Song.UNKNOWN_ARTIST }
            .groupBy { it.song.displayArtist }
            .map { (artist, plays) -> RecapRank(artist, plays.size, plays.sumOf { it.listenedMs }) }
            .sortedWith(compareByDescending<RecapRank<String>> { it.plays }.thenByDescending { it.listenedMs })
            .take(TOP)

        val local = inPeriod.map { it.playedAt.atZone(zone) }
        val favoriteDay = local.groupingBy { it.dayOfWeek }.eachCount().maxByOrNull { it.value }?.key
        val favoriteDayPart = local.groupingBy { DayPart.of(it.hour) }.eachCount().maxByOrNull { it.value }?.key

        val firstPlay = history.groupBy { it.song.id }.mapValues { (_, plays) -> plays.minOf { it.playedAt } }
        val discoveries = firstPlay.values.count { !it.isBefore(start) }

        return Recap(
            period = period,
            plays = inPeriod.size,
            totalListenedMs = inPeriod.sumOf { it.listenedMs },
            distinctSongs = inPeriod.distinctBy { it.song.id }.size,
            topSongs = topSongs,
            topArtists = topArtists,
            favoriteDay = favoriteDay,
            favoriteDayPart = favoriteDayPart,
            discoveries = discoveries,
            activeDays = local.map { it.toLocalDate() }.distinct().size,
        )
    }
}
