package fr.synxio.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlistId"), Index(value = ["playlistId", "position"])],
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: Long,
    val songId: Long,
    /** Chemin conservé pour retrouver le morceau si MediaStore réattribue les ids. */
    val songPath: String,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val songPath: String,
    val addedAt: Long,
)

/**
 * Statistiques d'écoute par morceau. [skipCount] alimente le classement « à revoir »
 * et permet plus tard d'exclure les titres systématiquement zappés du mode aléatoire.
 */
@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val songId: Long,
    val songPath: String,
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAt: Long,
    val totalListenedMs: Long,
)

/** File d'attente persistée : restaurée telle quelle au prochain démarrage. */
@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val songId: Long,
    val position: Int,
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val queueTitle: String,
)

/** Cache local des paroles récupérées en ligne, pour éviter de re-télécharger. */
@Entity(tableName = "lyrics_cache", indices = [Index("songPath", unique = true)])
data class LyricsCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songPath: String,
    val content: String,
    val synced: Boolean,
    val source: String,
    val fetchedAt: Long,
)

/**
 * Profil d'égaliseur associé à une sortie audio.
 *
 * On indexe par nom de périphérique (« HONOR Earbuds 3 Pro ») plutôt que par adresse
 * MAC : l'adresse demanderait la permission BLUETOOTH_CONNECT, et le nom suffit très
 * largement pour reconnaître un casque appairé.
 */
@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val deviceName: String,
    val curveId: String,
    val updatedAt: Long,
)

/** Dossiers exclus du scan (sonneries, enregistrements vocaux, podcasts...). */
@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val path: String,
)

/** Historique de lecture. */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val songPath: String,
    val playedAt: Long,  // Timestamp en secondes
    val listenedMs: Long,
    val durationMs: Long,
)

/** Cache pour les couleurs des pochettes. */
@Entity(tableName = "artwork_colors")
data class ArtworkColorEntity(
    @PrimaryKey val artworkUri: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val backgroundColor: Int,
    val onPrimaryColor: Int,
    val onSecondaryColor: Int,
    val onBackgroundColor: Int,
)

/**
 * Une playlist intelligente de l'utilisateur. Les règles sont sérialisées en JSON
 * plutôt qu'éclatées en colonnes : leur nombre est variable et elles ne sont jamais
 * interrogées en SQL, l'évaluation se faisant en mémoire.
 */
@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
