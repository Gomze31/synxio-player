package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PodcastProgressDao {
    @Query("SELECT * FROM podcast_progress WHERE songId = :songId")
    suspend fun getProgress(songId: Long): PodcastProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PodcastProgressEntity)
}
