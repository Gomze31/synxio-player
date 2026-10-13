package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.model.PlayHistory
import fr.synxio.player.data.repo.HistoryRepository
import fr.synxio.player.data.repo.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel pour gérer l'historique de lecture.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    /**
     * Flux de tout l'historique de lecture.
     */
    val history: StateFlow<List<fr.synxio.player.data.model.PlayHistory>> = historyRepository.history

    /**
     * Flux de l'historique récent (limité à 50 éléments).
     */
    val recentHistory: StateFlow<List<fr.synxio.player.data.model.PlayHistory>> =
        historyRepository.recentHistory(50)

    /**
     * Filtre de période pour l'historique.
     */
    private val _periodFilter = MutableStateFlow(StatsPeriod.ALL_TIME)
    val periodFilter: StateFlow<StatsPeriod> = _periodFilter.asStateFlow()

    /**
     * Flux de l'historique filtré par période.
     */
    val filteredHistory: StateFlow<List<fr.synxio.player.data.model.PlayHistory>> = combine(
        history,
        _periodFilter
    ) { historyList, period ->
        when (period) {
            StatsPeriod.TODAY -> filterHistoryForToday(historyList)
            StatsPeriod.WEEK -> filterHistoryForWeek(historyList)
            StatsPeriod.MONTH -> filterHistoryForMonth(historyList)
            StatsPeriod.YEAR -> filterHistoryForYear(historyList)
            StatsPeriod.ALL_TIME -> historyList
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Filtre l'historique pour aujourd'hui.
     */
    private fun filterHistoryForToday(history: List<fr.synxio.player.data.model.PlayHistory>): List<fr.synxio.player.data.model.PlayHistory> {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis / 1000
        
        return history.filter { it.playedAt.epochSecond >= todayStart }
    }

    /**
     * Filtre l'historique pour cette semaine.
     */
    private fun filterHistoryForWeek(history: List<fr.synxio.player.data.model.PlayHistory>): List<fr.synxio.player.data.model.PlayHistory> {
        val weekStart = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis / 1000
        
        return history.filter { it.playedAt.epochSecond >= weekStart }
    }

    /**
     * Filtre l'historique pour ce mois.
     */
    private fun filterHistoryForMonth(history: List<fr.synxio.player.data.model.PlayHistory>): List<fr.synxio.player.data.model.PlayHistory> {
        val monthStart = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MONTH, -1)
        }.timeInMillis / 1000
        
        return history.filter { it.playedAt.epochSecond >= monthStart }
    }

    /**
     * Filtre l'historique pour cette année.
     */
    private fun filterHistoryForYear(history: List<fr.synxio.player.data.model.PlayHistory>): List<fr.synxio.player.data.model.PlayHistory> {
        val yearStart = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.YEAR, -1)
        }.timeInMillis / 1000
        
        return history.filter { it.playedAt.epochSecond >= yearStart }
    }

    /**
     * Définit la période de filtre.
     */
    fun setPeriodFilter(period: StatsPeriod) {
        _periodFilter.value = period
    }

    /**
     * Efface une entrée de l'historique.
     */
    fun delete(id: Long) = viewModelScope.launch {
        historyRepository.delete(id)
    }

    /**
     * Efface l'historique pour un morceau.
     */
    fun deleteForSong(songId: Long) = viewModelScope.launch {
        historyRepository.deleteForSong(songId)
    }

    /**
     * Efface tout l'historique.
     */
    fun clear() = viewModelScope.launch {
        historyRepository.clear()
    }

    /**
     * Compte le nombre total d'entrées dans l'historique.
     */
    val historyCount: StateFlow<Int> = historyRepository.history
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * Récupère le temps total d'écoute.
     */
    fun getTotalListenedMs(): Long {
        return history.value.sumOf { it.listenedMs }
    }

    /**
     * Récupère l'historique pour un morceau spécifique.
     */
    fun getHistoryForSong(songId: Long): StateFlow<List<fr.synxio.player.data.model.PlayHistory>> =
        historyRepository.historyForSong(songId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Écoutes des sept derniers jours, du plus ancien à aujourd'hui, jours vides compris.
     * (L'ancienne version triait des dates formatées comme du texte : l'ordre était faux.)
     */
    val lastSevenDays: StateFlow<List<DayCount>> = history
        .map { historyList ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val counts = historyList.groupingBy { it.playedAt.atZone(zone).toLocalDate() }.eachCount()
            (6 downTo 0).map { offset ->
                val day = today.minusDays(offset.toLong())
                DayCount(
                    shortLabel = day.format(SHORT_DAY),
                    fullLabel = dayLabel(day, today),
                    count = counts[day] ?: 0,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Historique filtré, groupé par jour, avec l'heure déjà formatée : la liste n'a plus
     * qu'à afficher (le groupement et le formatage se faisaient à chaque composition
     * d'une ligne, avec des `indexOf` en O(n²)).
     */
    val days: StateFlow<List<HistoryDay>> = filteredHistory
        .map { list ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val seen = HashMap<String, Int>()
            list.groupBy { it.playedAt.atZone(zone).toLocalDate() }
                .map { (day, items) ->
                    HistoryDay(
                        key = day.toString(),
                        label = dayLabel(day, today),
                        entries = items.map { item ->
                            val base = "${item.playedAt.toEpochMilli()}-${item.song.id}"
                            val n = seen.merge(base, 1, Int::plus) ?: 1
                            HistoryEntry(
                                key = if (n == 1) base else "$base-$n",
                                item = item,
                                time = item.playedAt.atZone(zone).toLocalTime().format(TIME),
                            )
                        },
                    )
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nombre d'écoutes de la période choisie (et non de tout l'historique). */
    val filteredCount: StateFlow<Int> = filteredHistory
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    data class DayCount(val shortLabel: String, val fullLabel: String, val count: Int)
    data class HistoryEntry(val key: String, val item: PlayHistory, val time: String)
    data class HistoryDay(val key: String, val label: String, val entries: List<HistoryEntry>)

    private companion object {
        val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)
        val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
        val LONG_DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

        fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
            today -> "Aujourd'hui"
            today.minusDays(1) -> "Hier"
            else -> day.format(if (day.year == today.year) LONG_DAY else LONG_DAY_YEAR)
                .replaceFirstChar { it.titlecase(Locale.FRENCH) }
        }
    }

    /**
     * Récupère les statistiques par jour de la semaine.
     */
    val weekdayStats: StateFlow<Map<String, Int>> = history
        .map { historyList ->
            val weekdayCounts = mutableMapOf<String, Int>()
            val weekdayNames = listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")

            historyList.forEach { item ->
                val date = java.util.Date(item.playedAt.toEpochMilli())
                val calendar = java.util.Calendar.getInstance().apply { time = date }
                val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
                val weekday = weekdayNames.getOrNull(dayOfWeek) ?: "Inconnu"
                weekdayCounts[weekday] = (weekdayCounts[weekday] ?: 0) + 1
            }

            // Initialiser tous les jours avec 0
            weekdayNames.forEach { day ->
                if (day !in weekdayCounts) {
                    weekdayCounts[day] = 0
                }
            }

            weekdayCounts.entries
                .sortedBy { weekdayNames.indexOf(it.key) }
                .associate { it.toPair() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
}
