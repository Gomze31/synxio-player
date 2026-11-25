package fr.synxio.player.data.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Minutes écoutées un jour donné : une case de la heatmap. */
data class DayMinutes(val date: LocalDate, val minutes: Int)

/**
 * Jours d'affilée avec au moins [InsightsEngine.STREAK_MIN_MINUTES] minutes d'écoute :
 * la série en cours et la meilleure jamais atteinte.
 */
data class Streak(val current: Int, val best: Int)

/** Le mois en cours face au précédent : écoute, titres distincts, artiste n° 1. */
data class MonthOverMonth(
    val month: YearMonth,
    val minutes: Int,
    val previousMinutes: Int,
    val distinctSongs: Int,
    val previousDistinctSongs: Int,
    val topArtist: String?,
    val previousTopArtist: String?,
) {
    val deltaMinutes: Int get() = minutes - previousMinutes
}

/** Famille de tempo : lent sous 100 BPM, moyen de 100 à 130, rapide au-delà. */
enum class TempoBand(val label: String) {
    SLOW("lent"), MEDIUM("moyen"), FAST("rapide");

    companion object {
        fun of(bpm: Float): TempoBand = when {
            bpm < 100f -> SLOW
            bpm > 130f -> FAST
            else -> MEDIUM
        }
    }
}

/**
 * Tempo et énergie des 30 derniers jours, pondérés par la durée écoutée.
 *
 * [previousAverageBpm] : même calcul sur les 30 jours d'avant, pour dire « plus rapide que le
 * mois dernier » ; `null` sans écoute analysée sur cette fenêtre. [distribution] : parts du
 * temps d'écoute par famille, somme 1.
 */
data class Tempo(
    val averageBpm: Float,
    val previousAverageBpm: Float?,
    val averageEnergy: Float,
    val distribution: Map<TempoBand, Float>,
)

/** Un titre découvert dans les 30 derniers jours et déjà réécouté. */
data class FirstListen(val song: Song, val firstPlayedOn: LocalDate, val playCount: Int)

/**
 * Le volet « Insights » des statistiques, tout calculé par [InsightsEngine].
 *
 * [clock] : 24 cases de minutes, par heure locale ; [peakHour] `null` sans écoute.
 * [weekMinutes] : les 7 derniers jours, aujourd'hui compris (puce de l'accueil).
 */
data class Insights(
    val heatmap: List<DayMinutes>,
    val streak: Streak,
    val clock: IntArray,
    val peakHour: Int?,
    val monthOverMonth: MonthOverMonth,
    val tempo: Tempo?,
    val firsts: List<FirstListen>,
    val weekMinutes: Int,
) {
    val totalMinutes: Int get() = heatmap.sumOf { it.minutes }

    /** Rien à raconter : aucune minute sur l'année et aucune série passée. */
    val isEmpty: Boolean get() = totalMinutes == 0 && streak.best == 0
}

/**
 * Calcule les [Insights] à partir de l'historique. Fonction pure : la date du jour et le fuseau
 * sont passés en argument, rien n'est stocké.
 *
 * Chaque écoute est datée dans [ZoneId] à l'instant `playedAt` (fin d'écoute). Les minutes
 * viennent toujours de la somme des `listenedMs`, jamais du nombre de lectures : trente
 * lectures de dix secondes font cinq minutes. Les traits sont lus par chemin de fichier,
 * comme les stocke `TrackTraitsRepository`.
 */
object InsightsEngine {
    const val HEATMAP_DAYS = 365
    const val STREAK_MIN_MINUTES = 5
    const val TEMPO_DAYS = 30L
    const val FIRSTS_DAYS = 30L
    const val FIRSTS_MIN_PLAYS = 3
    const val FIRSTS_MAX = 10

    private class Play(val song: Song, val at: ZonedDateTime, val listenedMs: Long) {
        val date: LocalDate get() = at.toLocalDate()
    }

    fun compute(
        history: List<PlayHistory>,
        traitsByPath: Map<String, TrackTraits>,
        today: LocalDate,
        zone: ZoneId,
    ): Insights {
        val plays = history.map { Play(it.song, it.playedAt.atZone(zone), it.listenedMs.coerceAtLeast(0L)) }

        val msByDay = HashMap<LocalDate, Long>()
        val clockMs = LongArray(24)
        for (play in plays) {
            msByDay.merge(play.date, play.listenedMs, Long::plus)
            clockMs[play.at.hour] += play.listenedMs
        }

        val heatmap = List(HEATMAP_DAYS) { i ->
            val date = today.minusDays((HEATMAP_DAYS - 1 - i).toLong())
            DayMinutes(date, minutes(msByDay[date] ?: 0L))
        }
        val clock = IntArray(24) { minutes(clockMs[it]) }
        val peakHour = clock.indices.maxByOrNull { clock[it] }?.takeIf { clock[it] > 0 }
        val weekStart = today.minusDays(6)
        val weekMs = msByDay.entries.sumOf { (date, ms) -> if (date.isBefore(weekStart) || date.isAfter(today)) 0L else ms }

        return Insights(
            heatmap = heatmap,
            streak = streak(msByDay, today),
            clock = clock,
            peakHour = peakHour,
            monthOverMonth = monthOverMonth(plays, today),
            tempo = tempo(plays, traitsByPath, today),
            firsts = firsts(plays, today),
            weekMinutes = minutes(weekMs),
        )
    }

    private fun minutes(ms: Long): Int = (ms / 60_000L).toInt()

    /**
     * Série courante depuis aujourd'hui, ou depuis hier si la journée n'a pas encore ses cinq
     * minutes : elle n'est pas finie. Le record parcourt tout l'historique.
     */
    private fun streak(msByDay: Map<LocalDate, Long>, today: LocalDate): Streak {
        val qualifying = msByDay.filterValues { minutes(it) >= STREAK_MIN_MINUTES }.keys
        if (qualifying.isEmpty()) return Streak(0, 0)

        var current = 0
        var day = if (today in qualifying) today else today.minusDays(1)
        while (day in qualifying) {
            current++
            day = day.minusDays(1)
        }

        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in qualifying.sorted()) {
            run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            if (run > best) best = run
            previous = date
        }
        return Streak(current, best)
    }

    private fun monthOverMonth(plays: List<Play>, today: LocalDate): MonthOverMonth {
        val month = YearMonth.from(today)
        val previousMonth = month.minusMonths(1)
        val current = plays.filter { YearMonth.from(it.date) == month }
        val previous = plays.filter { YearMonth.from(it.date) == previousMonth }
        return MonthOverMonth(
            month = month,
            minutes = minutes(current.sumOf { it.listenedMs }),
            previousMinutes = minutes(previous.sumOf { it.listenedMs }),
            distinctSongs = current.distinctBy { it.song.id }.size,
            previousDistinctSongs = previous.distinctBy { it.song.id }.size,
            topArtist = topArtist(current),
            previousTopArtist = topArtist(previous),
        )
    }

    /** L'artiste le plus écouté en minutes, pas en lectures : trente extraits ne font pas un favori. */
    private fun topArtist(plays: List<Play>): String? =
        plays.groupBy { it.song.artist }
            .mapValues { (_, list) -> list.sumOf { it.listenedMs } }
            .filterValues { it > 0L }
            .maxByOrNull { it.value }
            ?.key

    private fun tempo(plays: List<Play>, traitsByPath: Map<String, TrackTraits>, today: LocalDate): Tempo? {
        val start = today.minusDays(TEMPO_DAYS - 1)
        val previousStart = start.minusDays(TEMPO_DAYS)
        val current = plays.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        val previous = plays.filter { !it.date.isBefore(previousStart) && it.date.isBefore(start) }
        val now = weighted(current, traitsByPath) ?: return null
        return Tempo(
            averageBpm = now.bpm,
            previousAverageBpm = weighted(previous, traitsByPath)?.bpm,
            averageEnergy = now.energy,
            distribution = now.distribution,
        )
    }

    private class Weighted(val bpm: Float, val energy: Float, val distribution: Map<TempoBand, Float>)

    /** Moyennes pondérées par la durée écoutée, sur les seules écoutes dont le titre a un BPM. */
    private fun weighted(plays: List<Play>, traitsByPath: Map<String, TrackTraits>): Weighted? {
        var weight = 0.0
        var bpmSum = 0.0
        var energySum = 0.0
        val bands = DoubleArray(TempoBand.entries.size)
        for (play in plays) {
            val traits = traitsByPath[play.song.path] ?: continue
            if (!traits.hasBpm || play.listenedMs <= 0L) continue
            val w = play.listenedMs.toDouble()
            weight += w
            bpmSum += traits.bpm * w
            energySum += traits.energy * w
            bands[TempoBand.of(traits.bpm).ordinal] += w
        }
        if (weight <= 0.0) return null
        return Weighted(
            bpm = (bpmSum / weight).toFloat(),
            energy = (energySum / weight).toFloat(),
            distribution = TempoBand.entries.associateWith { (bands[it.ordinal] / weight).toFloat() },
        )
    }

    /**
     * Titres dont la toute première écoute tombe dans les 30 derniers jours et qui ont déjà été
     * réécoutés ([FIRSTS_MIN_PLAYS] écoutes en tout) : les découvertes qui ont pris.
     */
    private fun firsts(plays: List<Play>, today: LocalDate): List<FirstListen> {
        val windowStart = today.minusDays(FIRSTS_DAYS - 1)
        return plays.groupBy { it.song.id }.values
            .mapNotNull { list ->
                val first = list.minBy { it.at }
                if (first.date.isBefore(windowStart) || list.size < FIRSTS_MIN_PLAYS) null
                else FirstListen(first.song, first.date, list.size)
            }
            .sortedWith(compareByDescending<FirstListen> { it.playCount }.thenByDescending { it.firstPlayedOn })
            .take(FIRSTS_MAX)
    }
}
