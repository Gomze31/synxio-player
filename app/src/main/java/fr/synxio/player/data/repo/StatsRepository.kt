package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.model.AlbumStats
import fr.synxio.player.data.model.ArtistStats
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.model.StatsSummary
import fr.synxio.player.data.model.formatDate
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour gérer les statistiques d'écoute.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val playStatDao: PlayStatDao,
    private val musicRepository: MusicRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    /**
     * Flux du résumé des statistiques.
     */
    val statsSummary: StateFlow<StatsSummary> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
        historyRepository.history
    ) { library, statsEntities, history ->
        // Calculer les statistiques
        val statsMap = statsEntities.associateBy { it.songId }
        // Index construits une fois par émission : une recherche linéaire par entrée
        // du classement rendait le calcul quadratique sur une grande bibliothèque.
        val artistsByName = library.artists.associateBy { it.name }
        val albumsById = library.albumsById

        // Top artistes
        val artistPlayCounts = mutableMapOf<String, Pair<Int, Long>>()
        library.artists.forEach { artist ->
            val artistSongs = artist.songs
            val totalPlays = artistSongs.sumOf { statsMap[it.id]?.playCount ?: 0 }
            val totalTime = artistSongs.sumOf { statsMap[it.id]?.totalListenedMs ?: 0L }
            if (totalPlays > 0) {
                artistPlayCounts[artist.name] = totalPlays to totalTime
            }
        }

        val topArtists = artistPlayCounts.entries
            .sortedByDescending { it.value.first }
            .take(10)
            .mapNotNull { entry ->
                artistsByName[entry.key]?.let { artist ->
                    ArtistStats(
                        artist = artist,
                        playCount = entry.value.first,
                        totalListenedMs = entry.value.second
                    )
                }
            }

        // Top albums
        val albumPlayCounts = mutableMapOf<Long, Int>()
        library.albums.forEach { album ->
            val albumPlays = album.songs.sumOf { statsMap[it.id]?.playCount ?: 0 }
            if (albumPlays > 0) {
                albumPlayCounts[album.id] = albumPlays
            }
        }

        val topAlbums = albumPlayCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .mapNotNull { entry ->
                albumsById[entry.key]?.let { album ->
                    AlbumStats(
                        album = album,
                        playCount = entry.value
                    )
                }
            }

        // Top genres
        val genrePlayCounts = mutableMapOf<String, Int>()
        library.genres.forEach { genre ->
            val genrePlays = genre.songs.sumOf { statsMap[it.id]?.playCount ?: 0 }
            if (genrePlays > 0) {
                genrePlayCounts[genre.name] = genrePlays
            }
        }

        val topGenres = genrePlayCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }

        // Temps total d'écoute
        val totalListenedMs = statsEntities.sumOf { it.totalListenedMs }

        StatsSummary(
            totalSongs = library.songs.size,
            totalPlayTimeMs = totalListenedMs,
            totalPlayCount = statsEntities.sumOf { it.playCount },
            topArtists = topArtists,
            topAlbums = topAlbums,
            topGenres = topGenres,
            recentActivity = history.take(20)
        )
    }
    .stateIn(scope, SharingStarted.Eagerly, StatsSummary())

    /**
     * Récupère les statistiques pour une période spécifique.
     *
     * Flux froid : c'est le ViewModel qui le partage, une seule fois. Un `stateIn` ici
     * créerait un collecteur à chaque changement de période.
     */
    fun getStatsForPeriod(period: StatsPeriod): Flow<StatsSummary> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
        historyRepository.getHistoryForPeriod(period)
    ) { library, statsEntities, history ->
        // Le début de période et les index sont calculés une fois par émission : les
        // recalculer par entrée (un Calendar par test, une recherche linéaire par
        // morceau) rendait l'écran des statistiques perceptiblement lent.
        val periodStart = periodStartMs(period)
        val statsMap = statsEntities.associateBy { it.songId }
        val librarySongIds = library.songs.mapTo(HashSet(library.songs.size)) { it.id }
        val artistsByName = library.artists.associateBy { it.name }
        val albumsById = library.albumsById

        fun PlayStatEntity.inPeriod(): Boolean = lastPlayedAt >= periodStart

        // Filtrer les statistiques pour la période
        val periodStats = statsEntities.filter { entity ->
            entity.songId in librarySongIds && entity.inPeriod()
        }

        // Top artistes pour la période
        val artistPlayCounts = mutableMapOf<String, Pair<Int, Long>>()
        library.artists.forEach { artist ->
            val artistSongs = artist.songs
            val artistPeriodPlays = artistSongs.sumOf { song ->
                statsMap[song.id]?.takeIf { it.inPeriod() }?.playCount ?: 0
            }
            val artistPeriodTime = artistSongs.sumOf { song ->
                statsMap[song.id]?.takeIf { it.inPeriod() }?.totalListenedMs ?: 0L
            }
            if (artistPeriodPlays > 0) {
                artistPlayCounts[artist.name] = artistPeriodPlays to artistPeriodTime
            }
        }

        val topArtists = artistPlayCounts.entries
            .sortedByDescending { it.value.first }
            .take(10)
            .mapNotNull { entry ->
                artistsByName[entry.key]?.let { artist ->
                    ArtistStats(
                        artist = artist,
                        playCount = entry.value.first,
                        totalListenedMs = entry.value.second
                    )
                }
            }

        // Top albums pour la période
        val albumPlayCounts = mutableMapOf<Long, Int>()
        library.albums.forEach { album ->
            val albumPeriodPlays = album.songs.sumOf { song ->
                statsMap[song.id]?.takeIf { it.inPeriod() }?.playCount ?: 0
            }
            if (albumPeriodPlays > 0) {
                albumPlayCounts[album.id] = albumPeriodPlays
            }
        }

        val topAlbums = albumPlayCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .mapNotNull { entry ->
                albumsById[entry.key]?.let { album ->
                    AlbumStats(
                        album = album,
                        playCount = entry.value
                    )
                }
            }

        // Top genres pour la période
        val genrePlayCounts = mutableMapOf<String, Int>()
        library.genres.forEach { genre ->
            val genrePeriodPlays = genre.songs.sumOf { song ->
                statsMap[song.id]?.takeIf { it.inPeriod() }?.playCount ?: 0
            }
            if (genrePeriodPlays > 0) {
                genrePlayCounts[genre.name] = genrePeriodPlays
            }
        }

        val topGenres = genrePlayCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }

        StatsSummary(
            totalSongs = library.songs.size,
            totalPlayTimeMs = periodStats.sumOf { it.totalListenedMs },
            totalPlayCount = periodStats.sumOf { it.playCount },
            topArtists = topArtists,
            topAlbums = topAlbums,
            topGenres = topGenres,
            recentActivity = history.take(20)
        )
    }

    /**
     * Début d'une période, en millisecondes. Zéro pour « depuis toujours ».
     */
    private fun periodStartMs(period: StatsPeriod): Long {
        if (period == StatsPeriod.ALL_TIME) return 0L
        val calendar = java.util.Calendar.getInstance()
        when (period) {
            StatsPeriod.TODAY -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
            }
            StatsPeriod.WEEK -> calendar.add(java.util.Calendar.DAY_OF_YEAR, -7)
            StatsPeriod.MONTH -> calendar.add(java.util.Calendar.MONTH, -1)
            StatsPeriod.YEAR -> calendar.add(java.util.Calendar.YEAR, -1)
            StatsPeriod.ALL_TIME -> Unit
        }
        return calendar.timeInMillis
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

    // Les flux paramétrés ci-dessous sont froids : partagés par le ViewModel, une fois
    // par paramètre. Un `stateIn(scope, Eagerly)` à chaque appel laissait un collecteur
    // éternel par ouverture d'écran. Ils croisent la base avec `songsById` plutôt que
    // d'appeler `songById` : l'index est vide avant le scan MediaStore, et une liste
    // construite avant resterait vide jusqu'à la prochaine écriture en base.

    /**
     * Récupère les morceaux les plus écoutés.
     */
    fun getMostPlayedSongs(limit: Int = 50): Flow<List<Song>> = combine(
        playStatDao.observeMostPlayed(limit),
        musicRepository.songsById,
    ) { entities, index -> entities.mapNotNull { index[it.songId] } }

    /**
     * Récupère les morceaux récemment écoutés.
     */
    fun getRecentlyPlayedSongs(limit: Int = 50): Flow<List<Song>> = combine(
        playStatDao.observeRecentlyPlayed(limit),
        musicRepository.songsById,
    ) { entities, index -> entities.mapNotNull { index[it.songId] } }

    /**
     * Morceaux jamais écoutés.
     */
    val neverPlayedSongs: StateFlow<List<Song>> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
    ) { library, statsEntities ->
        val playedSongIds = statsEntities.mapTo(HashSet()) { it.songId }
        library.songs.filter { it.id !in playedSongIds }
    }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

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
     * Récupère les genres les plus écoutés.
     */
    fun getTopGenres(limit: Int = 10): Flow<List<Pair<String, Int>>> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
    ) { library, statsEntities ->
        val statsMap = statsEntities.associateBy { it.songId }

        library.genres
            .map { genre ->
                val playCount = genre.songs.sumOf { statsMap[it.id]?.playCount ?: 0 }
                genre.name to playCount
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
    }

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
     * Combine les deux sources au lieu d'attendre une table non vide : sur une
     * installation neuve, l'attente ne se terminait jamais et le compteur restait figé.
     */
    val playedSongsCount: StateFlow<Int> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
    ) { library, statsEntities ->
        val playedSongIds = statsEntities.mapTo(HashSet()) { it.songId }
        library.songs.count { it.id in playedSongIds }
    }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * Flux du nombre de chansons jamais écoutées.
     */
    val unplayedSongsCount: StateFlow<Int> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
    ) { library, statsEntities ->
        val playedSongIds = statsEntities.mapTo(HashSet()) { it.songId }
        library.songs.count { it.id !in playedSongIds }
    }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * Statistiques par jour.
     */
    val dailyStats: StateFlow<Map<String, Int>> = playStatDao.observeAll()
        .map { entities ->
            entities.groupBy { entity ->
                val instant = java.time.Instant.ofEpochMilli(entity.lastPlayedAt)
                instant.formatDate()
            }
                .mapValues { (_, list) -> list.sumOf { it.playCount } }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Statistiques par jour de la semaine.
     */
    val weekdayStats: StateFlow<Map<String, Int>> = playStatDao.observeAll()
        .map { entities ->
            val weekdayNames = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
            entities.groupBy { 
                val dayOfWeek = java.time.Instant.ofEpochMilli(it.lastPlayedAt).atZone(java.time.ZoneId.systemDefault()).dayOfWeek
                weekdayNames[dayOfWeek.value - 1]
            }
                .mapValues { (_, list) -> list.sumOf { it.playCount } }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Statistiques par heure.
     */
    val hourlyStats: StateFlow<Map<String, Int>> = playStatDao.observeAll()
        .map { entities ->
            entities.groupBy { 
                val hour = java.time.Instant.ofEpochMilli(it.lastPlayedAt).atZone(java.time.ZoneId.systemDefault()).hour
                "${hour}h"
            }
                .mapValues { (_, list) -> list.sumOf { it.playCount } }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
}
