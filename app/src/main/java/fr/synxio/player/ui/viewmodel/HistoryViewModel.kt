package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.model.formatDate
import fr.synxio.player.data.repo.HistoryRepository
import fr.synxio.player.data.repo.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
     * Récupère les statistiques par jour pour la semaine.
     */
    val dailyStats: StateFlow<Map<String, Int>> = history
        .map { historyList ->
            val dailyCounts = mutableMapOf<String, Int>()
            historyList.forEach { item ->
                val date = item.playedAt.formatDate()
                dailyCounts[date] = (dailyCounts[date] ?: 0) + 1
            }
            dailyCounts.entries
                .sortedBy { it.key }
                .take(7)
                .associate { it.toPair() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
