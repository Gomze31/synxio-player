package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayHistoryDao
import fr.synxio.player.data.db.PlayHistoryEntity
import fr.synxio.player.data.model.PlayHistory
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Début de la période, en millisecondes epoch.
 *
 * Calculé une fois par émission par les appelants, et non pour chaque ligne examinée.
 */
internal fun StatsPeriod.startMillis(now: Long = System.currentTimeMillis()): Long {
    if (this == StatsPeriod.ALL_TIME) return 0L
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    when (this) {
        StatsPeriod.TODAY -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        StatsPeriod.WEEK -> calendar.add(Calendar.DAY_OF_YEAR, -7)
        StatsPeriod.MONTH -> calendar.add(Calendar.MONTH, -1)
        StatsPeriod.YEAR -> calendar.add(Calendar.YEAR, -1)
        StatsPeriod.ALL_TIME -> Unit
    }
    return calendar.timeInMillis
}

/**
 * Repository pour gérer l'historique de lecture.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val musicRepository: MusicRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    /**
     * Associe chaque entrée à son morceau, en suivant l'index de la bibliothèque.
     *
     * Lire un instantané de l'index ne suffisait pas : un historique émis avant la fin
     * du premier scan restait vide jusqu'à la lecture suivante.
     */
    private fun Flow<List<PlayHistoryEntity>>.withSongs(): Flow<List<PlayHistory>> =
        combine(this, musicRepository.songsById) { entities, index -> entities.resolve(index) }
            .flowOn(Dispatchers.Default)

    private fun List<PlayHistoryEntity>.resolve(index: Map<Long, Song>): List<PlayHistory> =
        mapNotNull { entity ->
            index[entity.songId]?.let {
                PlayHistory(
                    song = it,
                    playedAt = Instant.ofEpochSecond(entity.playedAt),
                    listenedMs = entity.listenedMs
                )
            }
        }

    /**
     * Flux de tout l'historique de lecture.
     *
     * Partagé seulement tant qu'un écran l'observe : garder toute la table chargée en
     * permanence la relisait entièrement à chaque morceau joué.
     */
    val history: StateFlow<List<PlayHistory>> = playHistoryDao.observeAll()
        .withSongs()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Flux de l'historique pour un morceau spécifique.
     */
    fun historyForSong(songId: Long): Flow<List<PlayHistory>> =
        playHistoryDao.observeBySong(songId).withSongs()

    /** Un flux partagé par taille demandée : les ViewModels le redemandent à chaque création. */
    private val recentByCount = ConcurrentHashMap<Int, StateFlow<List<PlayHistory>>>()

    /**
     * Flux de l'historique récent (limité à count éléments).
     *
     * La limite est appliquée en SQL : on ne charge plus tout l'historique pour n'en
     * garder que les premières lignes.
     */
    fun recentHistory(count: Int = 50): StateFlow<List<PlayHistory>> =
        recentByCount.getOrPut(count) {
            observeRecent(count).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    /** Les [count] dernières écoutes, en flux froid. */
    fun observeRecent(count: Int): Flow<List<PlayHistory>> =
        playHistoryDao.observeRecent(count).withSongs()

    /**
     * Ajoute une entrée à l'historique.
     */
    suspend fun add(songId: Long, songPath: String, listenedMs: Long, durationMs: Long) {
        playHistoryDao.add(
            PlayHistoryEntity(
                songId = songId,
                songPath = songPath,
                playedAt = System.currentTimeMillis() / 1000,
                listenedMs = listenedMs,
                durationMs = durationMs
            )
        )
    }

    /**
     * Supprime une entrée de l'historique.
     */
    suspend fun delete(id: Long) {
        playHistoryDao.delete(id)
    }

    /**
     * Supprime l'historique pour un morceau spécifique.
     */
    suspend fun deleteForSong(songId: Long) {
        playHistoryDao.deleteBySong(songId)
    }

    /**
     * Efface tout l'historique.
     */
    suspend fun clear() {
        playHistoryDao.clear()
    }

    /**
     * Compte le nombre total d'entrées dans l'historique.
     */
    suspend fun count(): Int = playHistoryDao.count()

    /**
     * Récupère le temps total d'écoute.
     */
    suspend fun totalListenedMs(): Long = playHistoryDao.totalListenedMs()

    /**
     * Récupère les N dernières entrées.
     */
    suspend fun getRecent(count: Int): List<PlayHistory> =
        playHistoryDao.recent(count).resolve(musicRepository.songsById.value)

    /**
     * Récupère l'historique depuis une date spécifique.
     */
    fun getHistorySince(startTime: Long): Flow<List<PlayHistory>> =
        playHistoryDao.observeSince(startTime).withSongs()

    /**
     * Récupère la date du premier élément de l'historique.
     */
    suspend fun getFirstPlayedAt(): Instant? {
        val entities = playHistoryDao.observeAll().first()
        return entities.firstOrNull()?.let { Instant.ofEpochSecond(it.playedAt) }
    }

    /**
     * Récupère l'historique pour une période spécifique.
     *
     * Le filtrage se fait en SQL, sur l'index de `playedAt` ; le début de période est
     * calculé à l'abonnement, comme auparavant.
     */
    fun getHistoryForPeriod(period: StatsPeriod): Flow<List<PlayHistory>> = flow {
        emitAll(playHistoryDao.observeSince(period.startMillis() / 1000).withSongs())
    }

    /** Les [limit] dernières écoutes de la période, sans charger le reste. */
    fun observeRecentForPeriod(period: StatsPeriod, limit: Int): Flow<List<PlayHistory>> = flow {
        emitAll(playHistoryDao.observeRecentSince(period.startMillis() / 1000, limit).withSongs())
    }
}
