package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Métadonnées résolues d'un fichier, par la cascade tags → MediaStore → nom → en ligne.
 *
 * Valable tant que [sizeBytes], [modifiedSec] et [version] correspondent : un fichier
 * retagué ou remplacé change de date de modification, et l'entrée est recalculée.
 */
@Entity(tableName = "track_meta")
data class TrackMetaEntity(
    @PrimaryKey val path: String,
    val mediaId: Long,
    val sizeBytes: Long,
    val modifiedSec: Long,
    /** Version de l'algorithme : la monter fait tout recalculer à la mise à jour. */
    val version: Int,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val year: Int?,
    val genre: String?,
    val track: Int?,
    val disc: Int?,
    val durationMs: Long?,
    /** « TITLE=FILE_TAGS;ARTIST=INFERRED… » : d'où vient chaque champ. */
    val sources: String,
    val artUri: String?,
    val artSource: String,
    /** Ordre inverse « Titre - Artiste » quand le nom de fichier est ambigu. */
    val altTitle: String?,
    val altArtist: String?,
    /** Clé de la recherche en ligne, tirée des seules sources locales (voir `online_lookups`). */
    val onlineKey: String?,
    /** Dernière recherche en ligne, 0 si jamais. */
    val onlineCheckedAt: Long,
    val updatedAt: Long,
)

/** Réponse d'une recherche en ligne, partagée par les fichiers qui demandent la même chose. */
@Entity(tableName = "online_lookups")
data class OnlineLookupEntity(
    @PrimaryKey val key: String,
    val found: Boolean,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val year: Int?,
    val genre: String?,
    val track: Int?,
    val disc: Int?,
    /** Pochette téléchargée, dans le stockage privé. */
    val artPath: String?,
    /** Sortie MusicBrainz retenue : la pochette peut être redemandée sans refaire la recherche. */
    val releaseId: String?,
    val releaseGroupId: String?,
    val confidence: Int,
    val fetchedAt: Long,
)

@Dao
interface TrackMetaDao {

    @Query("SELECT * FROM track_meta")
    suspend fun all(): List<TrackMetaEntity>

    @Query("SELECT * FROM track_meta WHERE path = :path")
    suspend fun get(path: String): TrackMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(entities: List<TrackMetaEntity>)

    @Query("DELETE FROM track_meta WHERE path IN (:paths)")
    suspend fun deleteAll(paths: List<String>)

    @Query("DELETE FROM track_meta")
    suspend fun clear()
}

@Dao
interface OnlineLookupDao {

    @Query("SELECT * FROM online_lookups WHERE `key` = :key")
    suspend fun get(key: String): OnlineLookupEntity?

    @Query("SELECT artPath FROM online_lookups WHERE artPath IS NOT NULL")
    suspend fun artPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: OnlineLookupEntity)

    @Query("DELETE FROM online_lookups WHERE `key` IN (:keys)")
    suspend fun deleteAll(keys: List<String>)

    /** Oublie les « rien trouvé » : ils seront redemandés. */
    @Query("DELETE FROM online_lookups WHERE found = 0")
    suspend fun deleteMisses()
}
