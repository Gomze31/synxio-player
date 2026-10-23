package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayHistoryDao
import fr.synxio.player.data.db.PlayHistoryEntity
import fr.synxio.player.data.model.PlayHistory
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour gérer l'historique de lecture.
 *
 * Chaque flux croise les lignes Room avec l'index de la bibliothèque (`songsById`)
 * plutôt que d'appeler `songById` au moment de la lecture : l'index est vide tant que
 * MediaStore n'a pas été scanné, et une liste construite avant le scan resterait vide
 * jusqu'à la prochaine écriture en base.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val musicRepository: MusicRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    /**
     * Flux de tout l'historique de lecture.
     */
    val history: StateFlow<List<PlayHistory>> = combine(
        playHistoryDao.observeAll(),
        musicRepository.songsById,
    ) { entities, index -> entities.resolve(index) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Flux de l'historique pour un morceau spécifique.
     */
    fun historyForSong(songId: Long): Flow<List<PlayHistory>> = combine(
        playHistoryDao.observeBySong(songId),
        musicRepository.songsById,
    ) { entities, index -> entities.resolve(index) }

    /**
     * Flux de l'historique récent (limité à count éléments).
     *
     * Dérivé de [history] : un flux froid, que l'appelant partage lui-même. Un
     * `stateIn` ici créerait un collecteur éternel à chaque appel.
     */
    fun recentHistory(count: Int = 50): Flow<List<PlayHistory>> =
        history.map { it.take(count) }

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
    fun getHistorySince(startTime: Long): Flow<List<PlayHistory>> = combine(
        playHistoryDao.observeSince(startTime),
        musicRepository.songsById,
    ) { entities, index -> entities.resolve(index) }

    /**
     * Récupère la date du premier élément de l'historique.
     */
    suspend fun getFirstPlayedAt(): Instant? =
        playHistoryDao.firstPlayedAt()?.let { Instant.ofEpochSecond(it) }

    /**
     * Récupère l'historique pour une période spécifique.
     *
     * Dérivé de [history] pour suivre les nouvelles écoutes : une émission unique
     * figerait l'activité récente de l'écran des statistiques.
     */
    fun getHistoryForPeriod(period: StatsPeriod): Flow<List<PlayHistory>> =
        history.map { list ->
            val startTime = periodStartSec(period)
            list.filter { it.playedAt.epochSecond >= startTime }
        }

    /** Début de la période, en secondes. */
    private fun periodStartSec(period: StatsPeriod): Long {
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
        return calendar.timeInMillis / 1000
    }

    /** Les lignes dont le morceau a disparu de la bibliothèque sont ignorées. */
    private fun List<PlayHistoryEntity>.resolve(index: Map<Long, Song>): List<PlayHistory> =
        mapNotNull { entity ->
            index[entity.songId]?.let { song ->
                PlayHistory(
                    song = song,
                    playedAt = Instant.ofEpochSecond(entity.playedAt),
                    listenedMs = entity.listenedMs
                )
            }
        }
}
