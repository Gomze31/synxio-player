package fr.synxio.player.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations explicites de la base.
 *
 * La base contient des données que l'utilisateur ne peut pas reconstituer : favoris,
 * compteurs d'écoute, historique, playlists. Une migration destructive les effacerait
 * à chaque montée de version — d'où ces scripts, écrits une fois pour toutes.
 *
 * Les instructions sont idempotentes (`IF NOT EXISTS`) pour qu'une base déjà à jour,
 * créée par un `fallbackToDestructiveMigration` antérieur, traverse la migration sans
 * échouer.
 */

/** v3 → v4 : décalage manuel des paroles synchronisées. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lyrics_offsets` (
                `songPath` TEXT NOT NULL,
                `offsetMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songPath`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v2 → v3 : profils d'égaliseur par périphérique.
 *
 * La table avait été livrée via une migration destructive ; ce script permet aux
 * installations restées en v2 de conserver leurs données.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `device_profiles` (
                `deviceName` TEXT NOT NULL,
                `curveId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`deviceName`)
            )
            """.trimIndent()
        )
    }
}

/** v4 -> v5 : niveaux sonores mesures, pour la normalisation du volume. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loudness` (
                `songPath` TEXT NOT NULL,
                `dbfs` REAL NOT NULL,
                `fileModifiedSec` INTEGER NOT NULL,
                `analysedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songPath`)
            )
            """.trimIndent()
        )
    }
}

/** v5 -> v6 : empreintes sonores, pour la recherche de titres similaires. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audio_features` (
                `songPath` TEXT NOT NULL,
                `vector` TEXT NOT NULL,
                `fileModifiedSec` INTEGER NOT NULL,
                `analysedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songPath`)
            )
            """.trimIndent()
        )
    }
}

/** v6 -> v7 : etat de lecture persiste, pour que le widget affiche le bon bouton. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ALTER TABLE ADD COLUMN echoue si la colonne existe deja, et SQLite ne sait pas
        // dire IF NOT EXISTS ici : on inspecte le schema avant d'agir.
        val existing = db.query("PRAGMA table_info(`playback_state`)").use { cursor ->
            val names = mutableSetOf<String>()
            val index = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) names += cursor.getString(index)
            names
        }
        if ("isPlaying" !in existing) {
            db.execSQL(
                "ALTER TABLE `playback_state` ADD COLUMN `isPlaying` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
}

/** v7 -> v8 : playlists à règles définies par l'utilisateur. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rule_playlists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `rulesJson` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v8 -> v9 : progression de lecture des podcasts. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `podcast_progress` (
                `songId` INTEGER NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `lastPlayedSec` INTEGER NOT NULL,
                PRIMARY KEY(`songId`)
            )
            """.trimIndent()
        )
    }
}

/** v9 -> v10 : dossiers de livres audio. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audiobook_folders` (
                `path` TEXT NOT NULL,
                PRIMARY KEY(`path`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v10 -> v11 : index sur l'historique et les statistiques d'écoute.
 *
 * Les noms suivent la convention de Room (`index_<table>_<colonnes>`) : la validation
 * du schéma à l'ouverture compare les index par nom et ferait planter l'application
 * au moindre écart.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` ON `play_history` (`playedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_songId` ON `play_history` (`songId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_stats_lastPlayedAt` ON `play_stats` (`lastPlayedAt`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_play_stats_playCount_lastPlayedAt` " +
                "ON `play_stats` (`playCount`, `lastPlayedAt`)"
        )
    }
}

/** v11 -> v12 : cache de la cascade de métadonnées et des recherches en ligne. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `track_meta` (
                `path` TEXT NOT NULL,
                `mediaId` INTEGER NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `modifiedSec` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                `title` TEXT,
                `artist` TEXT,
                `album` TEXT,
                `albumArtist` TEXT,
                `year` INTEGER,
                `genre` TEXT,
                `track` INTEGER,
                `disc` INTEGER,
                `durationMs` INTEGER,
                `sources` TEXT NOT NULL,
                `artUri` TEXT,
                `artSource` TEXT NOT NULL,
                `altTitle` TEXT,
                `altArtist` TEXT,
                `onlineKey` TEXT,
                `onlineCheckedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`path`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `online_lookups` (
                `key` TEXT NOT NULL,
                `found` INTEGER NOT NULL,
                `title` TEXT,
                `artist` TEXT,
                `album` TEXT,
                `albumArtist` TEXT,
                `year` INTEGER,
                `genre` TEXT,
                `track` INTEGER,
                `disc` INTEGER,
                `artPath` TEXT,
                `confidence` INTEGER NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent()
        )
    }
}

/** v12 -> v13 : sortie MusicBrainz retenue, pour redemander une pochette sans refaire la recherche. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `online_lookups` ADD COLUMN `releaseId` TEXT")
        db.execSQL("ALTER TABLE `online_lookups` ADD COLUMN `releaseGroupId` TEXT")
    }
}

/** v13 -> v14 : réveils en musique. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `alarms` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `days` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `playlistId` INTEGER,
                `rampSeconds` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
)
