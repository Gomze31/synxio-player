package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlaylistDao {

    @Query("SELECT * FROM smart_playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SmartPlaylistEntity>>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    fun observeById(id: Long): Flow<SmartPlaylistEntity?>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    suspend fun get(id: Long): SmartPlaylistEntity?

    @Insert
    suspend fun insert(playlist: SmartPlaylistEntity): Long

    @Update
    suspend fun update(playlist: SmartPlaylistEntity)

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun delete(id: Long)
}
