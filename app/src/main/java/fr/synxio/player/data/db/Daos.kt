package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Les DAO qui embarquent des méthodes @Transaction sont des classes abstraites : Room
// génère alors un vrai wrapper transactionnel, ce qu'il ne fait pas de façon fiable
// pour les méthodes par défaut d'une interface Kotlin.

@Dao
abstract class PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    abstract fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    abstract fun observeAllEntries(): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position")
    abstract fun observeEntries(id: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position")
    abstract suspend fun entries(id: Long): List<PlaylistSongEntity>

    @Insert
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    abstract suspend fun insertEntries(entries: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    abstract suspend fun clearEntries(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id AND songId = :songId")
    abstract suspend fun removeSong(id: Long, songId: Long)

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    abstract suspend fun touch(id: Long, now: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    abstract suspend fun lastPosition(id: Long): Int

    /** Réécrit l'ordre complet d'une playlist (glisser-déposer, tri, suppression). */
    @Transaction
    open suspend fun replaceEntries(
        playlistId: Long,
        entries: List<PlaylistSongEntity>,
        now: Long,
    ) {
        clearEntries(playlistId)
        insertEntries(entries)
        touch(playlistId, now)
    }
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean
}

@Dao
abstract class PlayStatDao {

    @Query("SELECT * FROM play_stats")
    abstract fun observeAll(): Flow<List<PlayStatEntity>>

    @Query(
        "SELECT * FROM play_stats WHERE playCount > 0 " +
            "ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit"
    )
    abstract fun observeMostPlayed(limit: Int): Flow<List<PlayStatEntity>>

    @Query("SELECT * FROM play_stats WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    abstract fun observeRecentlyPlayed(limit: Int): Flow<List<PlayStatEntity>>

    @Query("SELECT * FROM play_stats WHERE songId = :songId")
    abstract suspend fun get(songId: Long): PlayStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(stat: PlayStatEntity)

    @Query("DELETE FROM play_stats")
    abstract suspend fun clear()

    @Transaction
    open suspend fun registerPlay(songId: Long, path: String, listenedMs: Long, now: Long) {
        val existing = get(songId)
        upsert(
            PlayStatEntity(
                songId = songId,
                songPath = path,
                playCount = (existing?.playCount ?: 0) + 1,
                skipCount = existing?.skipCount ?: 0,
                lastPlayedAt = now,
                totalListenedMs = (existing?.totalListenedMs ?: 0L) + listenedMs,
            )
        )
    }

    @Transaction
    open suspend fun registerSkip(songId: Long, path: String, listenedMs: Long) {
        val existing = get(songId)
        upsert(
            PlayStatEntity(
                songId = songId,
                songPath = path,
                playCount = existing?.playCount ?: 0,
                skipCount = (existing?.skipCount ?: 0) + 1,
                lastPlayedAt = existing?.lastPlayedAt ?: 0L,
                totalListenedMs = (existing?.totalListenedMs ?: 0L) + listenedMs,
            )
        )
    }
}

@Dao
abstract class QueueDao {

    @Query("SELECT * FROM queue ORDER BY position")
    abstract suspend fun queue(): List<QueueEntity>

    @Query("DELETE FROM queue")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insert(items: List<QueueEntity>)

    @Query("SELECT * FROM playback_state WHERE id = 0")
    abstract suspend fun state(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveState(state: PlaybackStateEntity)

    /**
     * Met a jour le seul drapeau de lecture.
     *
     * Le widget le lit pour choisir entre icone lecture et icone pause. Le rafraichir
     * juste avant de redessiner evite de dependre de l instant ou un callback du lecteur
     * a pris son instantane : selon l ordre des evenements, la derniere persistance
     * pouvait capturer un etat transitoire faux.
     */
    @Query("UPDATE playback_state SET isPlaying = :playing WHERE id = 0")
    abstract suspend fun setPlaying(playing: Boolean)

    /** File d'attente et position enregistrées d'un bloc : jamais l'une sans l'autre. */
    @Transaction
    open suspend fun persist(songIds: List<Long>, state: PlaybackStateEntity) {
        clear()
        insert(songIds.mapIndexed { index, id -> QueueEntity(songId = id, position = index) })
        saveState(state)
    }
}

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics_cache WHERE songPath = :path")
    suspend fun get(path: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE songPath = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM lyrics_cache")
    suspend fun clear()
}

@Dao
interface DeviceProfileDao {

    @Query("SELECT * FROM device_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DeviceProfileEntity>>

    @Query("SELECT * FROM device_profiles WHERE deviceName = :name")
    suspend fun forDevice(name: String): DeviceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(profile: DeviceProfileEntity)

    @Query("DELETE FROM device_profiles WHERE deviceName = :name")
    suspend fun remove(name: String)
}

@Dao
interface ExcludedFolderDao {

    @Query("SELECT * FROM excluded_folders")
    fun observeAll(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT path FROM excluded_folders")
    suspend fun paths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(folder: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE path = :path")
    suspend fun remove(path: String)
}

@Dao
interface PlayHistoryDao {

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE songId = :songId ORDER BY playedAt DESC")
    fun observeBySong(songId: Long): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE playedAt >= :startTime ORDER BY playedAt DESC")
    fun observeSince(startTime: Long): Flow<List<PlayHistoryEntity>>

    @Insert
    suspend fun add(entry: PlayHistoryEntity): Long

    @Insert
    suspend fun addAll(entries: List<PlayHistoryEntity>)

    @Query("DELETE FROM play_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM play_history WHERE songId = :songId")
    suspend fun deleteBySong(songId: Long)

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM play_history")
    suspend fun count(): Int

    @Query("SELECT SUM(listenedMs) FROM play_history")
    suspend fun totalListenedMs(): Long

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PlayHistoryEntity>
}

@Dao
interface LyricsOffsetDao {

    @Query("SELECT offsetMs FROM lyrics_offsets WHERE songPath = :path")
    suspend fun offsetFor(path: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(offset: LyricsOffsetEntity)

    @Query("DELETE FROM lyrics_offsets WHERE songPath = :path")
    suspend fun remove(path: String)

    @Query("DELETE FROM lyrics_offsets")
    suspend fun clear()
}

@Dao
interface LoudnessDao {

    @Query("SELECT * FROM loudness")
    fun observeAll(): Flow<List<LoudnessEntity>>

    @Query("SELECT * FROM loudness")
    suspend fun all(): List<LoudnessEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: LoudnessEntity)

    @Query("DELETE FROM loudness")
    suspend fun clear()
}

@Dao
interface AudioFeatureDao {

    @Query("SELECT * FROM audio_features")
    fun observeAll(): Flow<List<AudioFeatureEntity>>

    @Query("SELECT * FROM audio_features")
    suspend fun all(): List<AudioFeatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: AudioFeatureEntity)

    @Query("DELETE FROM audio_features")
    suspend fun clear()
}

@Dao
interface ArtworkColorDao {

    @Query("SELECT * FROM artwork_colors WHERE artworkUri = :uri")
    suspend fun get(uri: String): ArtworkColorEntity?

    @Query("SELECT * FROM artwork_colors")
    fun observeAll(): Flow<List<ArtworkColorEntity>>

    @Query("SELECT artworkUri FROM artwork_colors")
    suspend fun getAllUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ArtworkColorEntity)

    @Query("DELETE FROM artwork_colors WHERE artworkUri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM artwork_colors")
    suspend fun clear()
}
