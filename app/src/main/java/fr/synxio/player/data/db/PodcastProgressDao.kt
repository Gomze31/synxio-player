package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Positions de reprise des podcasts et des chapitres de livres audio, par morceau. */
@Dao
interface PodcastProgressDao {
    @Query("SELECT * FROM podcast_progress WHERE songId = :songId")
    suspend fun getProgress(songId: Long): PodcastProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PodcastProgressEntity)

    @Query("SELECT * FROM podcast_progress")
    fun observeAll(): Flow<List<PodcastProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(progress: List<PodcastProgressEntity>)

    /** Oublie la position de ces morceaux : ils redeviennent « non lus ». */
    @Query("DELETE FROM podcast_progress WHERE songId IN (:songIds)")
    suspend fun deleteFor(songIds: List<Long>)
}
