package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.model.StatsSummary
import fr.synxio.player.data.repo.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel pour gérer les statistiques d'écoute.
 *
 * Tous les flux sont partagés en `WhileSubscribed` : quitter l'écran coupe les
 * observateurs Room et les recalculs, au lieu de les laisser tourner à chaque écoute.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    /**
     * Flux du résumé des statistiques.
     */
    val statsSummary: StateFlow<StatsSummary> = statsRepository.statsSummary

    /**
     * Période de statistiques sélectionnée.
     */
    private val _selectedPeriod = MutableStateFlow(StatsPeriod.ALL_TIME)
    val selectedPeriod: StateFlow<StatsPeriod> = _selectedPeriod.asStateFlow()

    /**
     * Flux des statistiques pour la période sélectionnée.
     */
    val periodStats: StateFlow<StatsSummary> = _selectedPeriod
        .flatMapLatest { period -> statsRepository.getStatsForPeriod(period) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsSummary())

    /**
     * Définit la période sélectionnée.
     */
    fun setSelectedPeriod(period: StatsPeriod) {
        _selectedPeriod.value = period
    }

    /**
     * Flux du nombre total de morceaux.
     */
    val totalSongsCount: StateFlow<Int> = statsSummary
        .map { it.totalSongs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Flux du nombre total de lectures.
     */
    val totalPlayCount: StateFlow<Int> = statsSummary
        .map { it.totalPlayCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Flux du temps total d'écoute en millisecondes.
     */
    val totalPlayTimeMs: StateFlow<Long> = statsSummary
        .map { it.totalPlayTimeMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /**
     * Flux des top artistes.
     */
    val topArtists: StateFlow<List<fr.synxio.player.data.model.ArtistStats>> = periodStats
        .map { it.topArtists }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Flux des top albums.
     */
    val topAlbums: StateFlow<List<fr.synxio.player.data.model.AlbumStats>> = periodStats
        .map { it.topAlbums }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Flux des top genres.
     */
    val topGenres: StateFlow<List<Pair<String, Int>>> = periodStats
        .map { it.topGenres }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Flux de l'historique récent.
     */
    val recentActivity: StateFlow<List<fr.synxio.player.data.model.PlayHistory>> = periodStats
        .map { it.recentActivity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Flux du temps total d'écoute formaté.
     */
    val totalPlayTimeFormatted: StateFlow<String> = totalPlayTimeMs
        .map { ms ->
            val hours = ms / (1000 * 60 * 60)
            val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
            when {
                hours > 0 -> String.format(Locale.getDefault(), "%d h %02d min", hours, minutes)
                minutes > 0 -> String.format(Locale.getDefault(), "%d min", minutes)
                else -> "0 min"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 min")

    /**
     * Flux du nombre de morceaux écoutés au moins une fois.
     */
    val playedSongsCount: StateFlow<Int> = statsRepository.playedSongsCount

    /**
     * Flux du nombre de morceaux jamais écoutés.
     */
    val unplayedSongsCount: StateFlow<Int> = statsRepository.unplayedSongsCount

    /**
     * Flux du pourcentage de morceaux écoutés.
     */
    val playedPercentage: StateFlow<Float> = combine(
        playedSongsCount,
        totalSongsCount
    ) { played, total ->
        if (total > 0) (played.toFloat() / total) * 100 else 0f
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /**
     * Les 20 morceaux les plus écoutés.
     *
     * Une propriété stable et non une fonction : appelée depuis la composition, chaque
     * recomposition créait un nouveau flux partagé, jamais libéré, sur la portée de
     * l'application.
     */
    val mostPlayedSongs: StateFlow<List<Song>> = statsRepository.getMostPlayedSongs(MOST_PLAYED_SIZE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Les 10 morceaux récemment écoutés.
     */
    val recentlyPlayedSongs: StateFlow<List<Song>> = statsRepository.getRecentlyPlayedSongs(RECENTLY_PLAYED_SIZE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Récupère les morceaux jamais écoutés.
     */
    val neverPlayedSongs: StateFlow<List<Song>> = statsRepository.getNeverPlayedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Récupère les statistiques par jour pour la dernière semaine.
     */
    val dailyStats: StateFlow<Map<String, Int>> = statsRepository.dailyStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Récupère les statistiques par jour de la semaine.
     */
    val weekdayStats: StateFlow<Map<String, Int>> = statsRepository.weekdayStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Récupère les statistiques par heure de la journée.
     */
    val hourlyStats: StateFlow<Map<String, Int>> = statsRepository.hourlyStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Récupère les top genres, en flux froid : à collecter, ou à partager une seule fois.
     */
    fun getTopGenres(limit: Int = 10): Flow<List<Pair<String, Int>>> =
        statsRepository.getTopGenres(limit)

    /**
     * Récupère le temps moyen d'écoute par morceau.
     */
    val averagePlayTimeFormatted: StateFlow<String> = statsRepository.statsSummary
        .map { summary ->
            if (summary.totalPlayCount > 0) {
                val avgMs = summary.totalPlayTimeMs / summary.totalPlayCount
                val minutes = avgMs / (1000 * 60)
                val seconds = (avgMs % (1000 * 60)) / 1000
                if (minutes > 0) String.format(Locale.getDefault(), "%d min %02d s", minutes, seconds)
                else String.format(Locale.getDefault(), "%d s", seconds)
            } else {
                "0 s"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 s")

    /**
     * Récupère les statistiques pour un artiste.
     */
    suspend fun getArtistStats(artistName: String): fr.synxio.player.data.model.ArtistStats? =
        statsRepository.getArtistStats(artistName)

    /**
     * Récupère les statistiques pour un album.
     */
    suspend fun getAlbumStats(albumId: Long): fr.synxio.player.data.model.AlbumStats? =
        statsRepository.getAlbumStats(albumId)

    /**
     * Efface toutes les statistiques.
     */
    fun clearStats() = viewModelScope.launch {
        statsRepository.clearStats()
    }

    /**
     * Récupère le nombre total de lectures.
     */
    fun getTotalPlayCount(): Int = statsSummary.value.totalPlayCount

    /**
     * Récupère le temps total d'écoute.
     */
    fun getTotalPlayTimeMs(): Long = statsSummary.value.totalPlayTimeMs

    /**
     * Récupère les top artistes sous forme de paires (nom, count).
     */
    val topArtistsPairs: StateFlow<List<Pair<String, Int>>> = topArtists
        .map { artistStatsList ->
            artistStatsList.map { it.artist.name to it.playCount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Récupère les top albums sous forme de paires (nom, count).
     */
    val topAlbumsPairs: StateFlow<List<Pair<String, Int>>> = topAlbums
        .map { albumStatsList ->
            albumStatsList.map { it.album.title to it.playCount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private companion object {
        const val MOST_PLAYED_SIZE = 20
        const val RECENTLY_PLAYED_SIZE = 10
    }
}
