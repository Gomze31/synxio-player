package fr.synxio.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        SmartPlaylistEntity::class,
    ],
    version = 4,
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
    abstract fun smartPlaylistDao(): SmartPlaylistDao

    companion object {
        const val NAME = "synxio.db"

        /**
         * v3 → v4 : ajout des playlists intelligentes.
         *
         * Écrite à la main et non déléguée à une migration destructive : l'utilisateur
         * perdrait playlists, favoris, statistiques et historique.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `smart_playlists` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `rulesJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
