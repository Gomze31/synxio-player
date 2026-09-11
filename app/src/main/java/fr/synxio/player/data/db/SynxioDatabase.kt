package fr.synxio.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        FavoriteEntity::class,
        PlayStatEntity::class,
        QueueEntity::class,
        PlaybackStateEntity::class,
        LyricsCacheEntity::class,
        ExcludedFolderEntity::class,
        PlayHistoryEntity::class,
        ArtworkColorEntity::class,
        DeviceProfileEntity::class,
        LyricsOffsetEntity::class,
        LoudnessEntity::class,
        AudioFeatureEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class SynxioDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playStatDao(): PlayStatDao
    abstract fun queueDao(): QueueDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun excludedFolderDao(): ExcludedFolderDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun artworkColorDao(): ArtworkColorDao
    abstract fun deviceProfileDao(): DeviceProfileDao
    abstract fun lyricsOffsetDao(): LyricsOffsetDao
    abstract fun loudnessDao(): LoudnessDao
    abstract fun audioFeatureDao(): AudioFeatureDao

    companion object {
        const val NAME = "synxio.db"
    }
}
