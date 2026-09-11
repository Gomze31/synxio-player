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

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
)
