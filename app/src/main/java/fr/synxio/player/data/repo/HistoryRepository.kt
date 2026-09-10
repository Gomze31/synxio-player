package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayHistoryDao
import fr.synxio.player.data.db.PlayHistoryEntity
import fr.synxio.player.data.model.PlayHistory
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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
     * Flux de tout l'historique de lecture.
     */
    val history: StateFlow<List<PlayHistory>> = playHistoryDao.observeAll()
        .map { entities ->
            entities.mapNotNull { entity ->
                val song = musicRepository.songById(entity.songId)
                song?.let {
                    PlayHistory(
                        song = it,
                        playedAt = Instant.ofEpochSecond(entity.playedAt),
                        listenedMs = entity.listenedMs
                    )
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Flux de l'historique pour un morceau spécifique.
     */
    fun historyForSong(songId: Long): Flow<List<PlayHistory>> = playHistoryDao.observeBySong(songId)
        .map { entities ->
            entities.mapNotNull { entity ->
                val song = musicRepository.songById(entity.songId)
                song?.let {
                    PlayHistory(
                        song = it,
                        playedAt = Instant.ofEpochSecond(entity.playedAt),
                        listenedMs = entity.listenedMs
                    )
                }
            }
        }

    /**
     * Flux de l'historique récent (limité à count éléments).
     */
    fun recentHistory(count: Int = 50): StateFlow<List<PlayHistory>> = playHistoryDao.observeAll()
        .map { entities ->
            entities.take(count).mapNotNull { entity ->
                val song = musicRepository.songById(entity.songId)
                song?.let {
                    PlayHistory(
                        song = it,
                        playedAt = Instant.ofEpochSecond(entity.playedAt),
                        listenedMs = entity.listenedMs
                    )
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

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
    suspend fun getRecent(count: Int): List<PlayHistory> {
        return playHistoryDao.recent(count).mapNotNull { entity ->
            val song = musicRepository.songById(entity.songId)
            song?.let {
                PlayHistory(
                    song = it,
                    playedAt = Instant.ofEpochSecond(entity.playedAt),
                    listenedMs = entity.listenedMs
                )
            }
        }
    }

    /**
     * Récupère l'historique depuis une date spécifique.
     */
    fun getHistorySince(startTime: Long): Flow<List<PlayHistory>> = playHistoryDao.observeSince(startTime)
        .map { entities ->
            entities.mapNotNull { entity ->
                val song = musicRepository.songById(entity.songId)
                song?.let {
                    PlayHistory(
                        song = it,
                        playedAt = Instant.ofEpochSecond(entity.playedAt),
                        listenedMs = entity.listenedMs
                    )
                }
            }
        }

    /**
     * Récupère la date du premier élément de l'historique.
     */
    suspend fun getFirstPlayedAt(): Instant? {
        val entities = playHistoryDao.observeAll().first()
        return entities.firstOrNull()?.let { Instant.ofEpochSecond(it.playedAt) }
    }

    /**
     * Récupère l'historique pour une période spécifique.
     */
    fun getHistoryForPeriod(period: fr.synxio.player.data.model.StatsPeriod): Flow<List<PlayHistory>> {
        return flow {
            val startTime = when (period) {
                fr.synxio.player.data.model.StatsPeriod.TODAY -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.timeInMillis / 1000
                }
                fr.synxio.player.data.model.StatsPeriod.WEEK -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -7)
                    calendar.timeInMillis / 1000
                }
                fr.synxio.player.data.model.StatsPeriod.MONTH -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.MONTH, -1)
                    calendar.timeInMillis / 1000
                }
                fr.synxio.player.data.model.StatsPeriod.YEAR -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.YEAR, -1)
                    calendar.timeInMillis / 1000
                }
                fr.synxio.player.data.model.StatsPeriod.ALL_TIME -> 0L
            }
            emit(getHistoryForTimestampRange(startTime, Long.MAX_VALUE))
        }
    }

    /**
     * Récupère l'historique pour une plage de timestamps.
     */
    private suspend fun getHistoryForTimestampRange(startTime: Long, endTime: Long): List<PlayHistory> {
        // Pour l'instant, on charge tout et on filtre. À optimiser avec une requête SQL.
        return history.value.filter { historyItem ->
            historyItem.playedAt.epochSecond >= startTime && historyItem.playedAt.epochSecond <= endTime
        }
    }
}
