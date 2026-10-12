package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.model.AlbumStats
import fr.synxio.player.data.model.ArtistStats
import fr.synxio.player.data.model.PlayHistory
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.model.StatsSummary
import fr.synxio.player.data.model.formatDate
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour gérer les statistiques d'écoute.
 *
 * Toutes les vues dérivent du flux partagé [MusicRepository.allStats] : chaque
 * `playStatDao.observeAll()` ouvrait auparavant son propre observateur Room, relu en
 * entier à chaque morceau joué. Les flux exposés ne vivent que tant qu'un écran les
 * observe, et leurs calculs tournent sur Dispatchers.Default.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val playStatDao: PlayStatDao,
    private val musicRepository: MusicRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val statsMap: StateFlow<Map<Long, PlayStatEntity>> = musicRepository.allStats

    /** Titres de la bibliothèque, réémis seulement quand un scan en produit de nouveaux. */
    private val librarySongs: Flow<List<Song>> = musicRepository.library
        .map { it.songs }
        .distinctUntilChanged { a, b -> a === b }

    /**
     * Flux du résumé des statistiques.
     */
    val statsSummary: StateFlow<StatsSummary> = combine(
        musicRepository.library,
        statsMap,
        historyRepository.observeRecent(RECENT_ACTIVITY_SIZE)
    ) { library, stats, recent ->
        summarize(library, stats, periodStartMs = null, recent = recent)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), StatsSummary())

    /**
     * Statistiques pour une période spécifique.
     *
     * Flux froid : l'appelant le partage dans sa propre portée. En créer un `stateIn`
     * sur la portée de l'application à chaque appel accumulait des abonnements.
     */
    fun getStatsForPeriod(period: StatsPeriod): Flow<StatsSummary> = combine(
        musicRepository.library,
        statsMap,
        historyRepository.observeRecentForPeriod(period, RECENT_ACTIVITY_SIZE)
    ) { library, stats, recent ->
        summarize(library, stats, periodStartMs = period.startMillis(), recent = recent)
    }.flowOn(Dispatchers.Default)

    /**
     * Calcule un résumé.
     *
     * [periodStartMs] nul : toutes les statistiques comptent dans les totaux, y compris
     * celles de morceaux sortis de la bibliothèque (comportement historique du résumé
     * global). Sinon, seuls les morceaux présents et joués depuis ce début sont comptés.
     */
    private fun summarize(
        library: Library,
        stats: Map<Long, PlayStatEntity>,
        periodStartMs: Long?,
        recent: List<PlayHistory>,
    ): StatsSummary {
        val start = periodStartMs ?: Long.MIN_VALUE
        fun statOf(song: Song): PlayStatEntity? = stats[song.id]?.takeIf { it.lastPlayedAt >= start }

        val topArtists = library.artists
            .mapNotNull { artist ->
                var plays = 0
                var time = 0L
                for (song in artist.songs) {
                    val stat = statOf(song) ?: continue
                    plays += stat.playCount
                    time += stat.totalListenedMs
                }
                if (plays > 0) ArtistStats(artist = artist, playCount = plays, totalListenedMs = time) else null
            }
            .sortedByDescending { it.playCount }
            .take(TOP_SIZE)

        val topAlbums = library.albums
            .mapNotNull { album ->
                val plays = album.songs.sumOf { statOf(it)?.playCount ?: 0 }
                if (plays > 0) AlbumStats(album = album, playCount = plays) else null
            }
            .sortedByDescending { it.playCount }
            .take(TOP_SIZE)

        val topGenres = library.genres
            .mapNotNull { genre ->
                val plays = genre.songs.sumOf { statOf(it)?.playCount ?: 0 }
                if (plays > 0) genre.name to plays else null
            }
            .sortedByDescending { it.second }
            .take(TOP_SIZE)

        val counted: Collection<PlayStatEntity> = if (periodStartMs == null) {
            stats.values
        } else {
            // Ensemble d'ids plutôt qu'un `songs.find {}` par statistique : O(n) au lieu de O(n²).
            val songIds = library.songs.mapTo(HashSet(library.songs.size)) { it.id }
            stats.values.filter { it.songId in songIds && it.lastPlayedAt >= periodStartMs }
        }

        return StatsSummary(
            totalSongs = library.songs.size,
            totalPlayTimeMs = counted.sumOf { it.totalListenedMs },
            totalPlayCount = counted.sumOf { it.playCount },
            topArtists = topArtists,
            topAlbums = topAlbums,
            topGenres = topGenres,
            recentActivity = recent
        )
    }

    /**
     * Récupère les statistiques pour un artiste.
     */
    suspend fun getArtistStats(artistName: String): ArtistStats? {
        val library = musicRepository.library.value
        val artist = library.artists.find { it.name.equals(artistName, ignoreCase = true) } ?: return null

        val statsEntities = playStatDao.observeAll().first()
        val statsMap = statsEntities.associateBy { it.songId }

        val playCount = artist.songs.sumOf { statsMap[it.id]?.playCount ?: 0 }
        val totalListenedMs = artist.songs.sumOf { statsMap[it.id]?.totalListenedMs ?: 0L }

        return ArtistStats(
            artist = artist,
            playCount = playCount,
            totalListenedMs = totalListenedMs
        )
    }

    /**
     * Récupère les statistiques pour un album.
     */
    suspend fun getAlbumStats(albumId: Long): AlbumStats? {
        val library = musicRepository.library.value
        val album = library.albums.find { it.id == albumId } ?: return null

        val statsEntities = playStatDao.observeAll().first()
        val statsMap = statsEntities.associateBy { it.songId }

        val playCount = album.songs.sumOf { statsMap[it.id]?.playCount ?: 0 }

        return AlbumStats(
            album = album,
            playCount = playCount
        )
    }

    /**
     * Récupère le nombre total de lectures.
     */
    suspend fun getTotalPlayCount(): Int {
        return playStatDao.observeAll().first().sumOf { it.playCount }
    }

    /**
     * Récupère le temps total d'écoute.
     */
    suspend fun getTotalPlayTimeMs(): Long {
        return playStatDao.observeAll().first().sumOf { it.totalListenedMs }
    }

    /**
     * Récupère le nombre de morceaux écoutés au moins une fois.
     */
    suspend fun getPlayedSongsCount(): Int {
        return playStatDao.observeAll().first().count { it.playCount > 0 }
    }

    /**
     * Récupère le nombre de morceaux jamais écoutés.
     */
    suspend fun getUnplayedSongsCount(): Int {
        val allSongs = musicRepository.library.value.songs
        val statsEntities = playStatDao.observeAll().first()
        val playedSongIds = statsEntities.map { it.songId }.toSet()
        return allSongs.count { it.id !in playedSongIds }
    }

    /**
     * Morceaux les plus écoutés, en flux froid à partager côté appelant.
     *
     * Suit aussi l'index de la bibliothèque : lu en instantané, il donnait une liste
     * vide tant que le premier scan n'était pas terminé.
     */
    fun getMostPlayedSongs(limit: Int = 50): Flow<List<Song>> = combine(
        playStatDao.observeMostPlayed(limit),
        musicRepository.songsById,
    ) { entities, index -> entities.mapNotNull { index[it.songId] } }

    /**
     * Morceaux récemment écoutés, en flux froid à partager côté appelant.
     */
    fun getRecentlyPlayedSongs(limit: Int = 50): Flow<List<Song>> = combine(
        playStatDao.observeRecentlyPlayed(limit),
        musicRepository.songsById,
    ) { entities, index -> entities.mapNotNull { index[it.songId] } }

    /**
     * Morceaux jamais écoutés, en flux froid à partager côté appelant.
     */
    fun getNeverPlayedSongs(): Flow<List<Song>> = combine(librarySongs, statsMap) { songs, stats ->
        songs.filter { it.id !in stats }
    }.flowOn(Dispatchers.Default)

    /**
     * Récupère les statistiques par jour pour la dernière semaine.
     */
    suspend fun getDailyStats(): Map<String, Int> {
        val history = historyRepository.getRecent(100)
        val dailyCounts = mutableMapOf<String, Int>()

        history.forEach { item ->
            val date = Instant.ofEpochSecond(item.playedAt.epochSecond)
                .formatDate()
            dailyCounts[date] = (dailyCounts[date] ?: 0) + 1
        }

        return dailyCounts.entries
            .sortedBy { it.key }
            .take(7)
            .associate { it.toPair() }
    }

    /**
     * Récupère les statistiques par jour de la semaine.
     */
    suspend fun getWeekdayStats(): Map<String, Int> {
        val history = historyRepository.getRecent(1000)
        val weekdayCounts = mutableMapOf<String, Int>()
        val weekdayNames = listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")

        history.forEach { item ->
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

        return weekdayCounts.entries
            .sortedBy { weekdayNames.indexOf(it.key) }
            .associate { it.toPair() }
    }

    /**
     * Récupère les statistiques par heure de la journée.
     */
    suspend fun getHourlyStats(): Map<String, Int> {
        val history = historyRepository.getRecent(1000)
        val hourlyCounts = mutableMapOf<String, Int>()

        history.forEach { item ->
            val date = java.util.Date(item.playedAt.toEpochMilli())
            val calendar = java.util.Calendar.getInstance().apply { time = date }
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val hourRange = when {
                hour < 6 -> "0h-6h"
                hour < 12 -> "6h-12h"
                hour < 18 -> "12h-18h"
                else -> "18h-24h"
            }
            hourlyCounts[hourRange] = (hourlyCounts[hourRange] ?: 0) + 1
        }

        // Initialiser toutes les plages avec 0
        listOf("0h-6h", "6h-12h", "12h-18h", "18h-24h").forEach { range ->
            if (range !in hourlyCounts) {
                hourlyCounts[range] = 0
            }
        }

        return hourlyCounts
    }

    /**
     * Genres les plus écoutés, en flux froid à partager côté appelant.
     *
     * Suit désormais les statistiques : l'ancienne version ne les relisait qu'au
     * changement de bibliothèque.
     */
    fun getTopGenres(limit: Int = 10): Flow<List<Pair<String, Int>>> = combine(
        musicRepository.library,
        statsMap,
    ) { library, stats ->
        library.genres
            .map { genre -> genre.name to genre.songs.sumOf { stats[it.id]?.playCount ?: 0 } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
    }.flowOn(Dispatchers.Default)

    /**
     * Récupère le temps moyen d'écoute par morceau.
     */
    suspend fun getAveragePlayTimeMs(): Float {
        val statsEntities = playStatDao.observeAll().first()
        val totalPlays = statsEntities.sumOf { it.playCount }
        val totalTime = statsEntities.sumOf { it.totalListenedMs }
        return if (totalPlays > 0) (totalTime.toFloat() / totalPlays) else 0f
    }

    /**
     * Efface toutes les statistiques.
     */
    suspend fun clearStats() {
        playStatDao.clear()
    }

    /**
     * Flux du nombre de chansons jouées au moins une fois.
     *
     * Combiné aux statistiques plutôt qu'un `first { it.isNotEmpty() }`, qui restait
     * suspendu pour toujours sur une table vide et ne voyait jamais les écoutes suivantes.
     */
    val playedSongsCount: StateFlow<Int> = combine(librarySongs, statsMap) { songs, stats ->
        songs.count { it.id in stats }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Flux du nombre de chansons jamais écoutées.
     */
    val unplayedSongsCount: StateFlow<Int> = combine(librarySongs, statsMap) { songs, stats ->
        songs.count { it.id !in stats }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Statistiques par jour.
     */
    val dailyStats: StateFlow<Map<String, Int>> = statsMap
        .map { stats ->
            stats.values.groupBy { entity ->
                val instant = java.time.Instant.ofEpochMilli(entity.lastPlayedAt)
                instant.formatDate()
            }
                .mapValues { (_, list) -> list.sumOf { it.playCount } }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Statistiques par jour de la semaine.
     */
    val weekdayStats: StateFlow<Map<String, Int>> = statsMap
        .map { stats ->
            val weekdayNames = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
            val zone = java.time.ZoneId.systemDefault()
            stats.values.groupBy {
                val dayOfWeek = java.time.Instant.ofEpochMilli(it.lastPlayedAt).atZone(zone).dayOfWeek
                weekdayNames[dayOfWeek.value - 1]
            }
                .mapValues { (_, list) -> list.sumOf { it.playCount } }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Statistiques par heure.
     */
    val hourlyStats: StateFlow<Map<String, Int>> = statsMap
        .map { stats ->
            val zone = java.time.ZoneId.systemDefault()
            stats.values.groupBy {
                val hour = java.time.Instant.ofEpochMilli(it.lastPlayedAt).atZone(zone).hour
                "${hour}h"
            }
                .mapValues { (_, list) -> list.sumOf { it.playCount } }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private companion object {
        /** Taille des classements (artistes, albums, genres). */
        const val TOP_SIZE = 10

        /** Nombre d'écoutes affichées dans l'activité récente. */
        const val RECENT_ACTIVITY_SIZE = 20
    }
}
