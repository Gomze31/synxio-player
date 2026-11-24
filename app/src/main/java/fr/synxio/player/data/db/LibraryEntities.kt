package fr.synxio.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ligne brute de MediaStore, telle que lue par le scanner (après `tidyTag`).
 *
 * C'est le cache de la bibliothèque : au démarrage, l'app publie ces lignes avant toute
 * requête MediaStore. Les filtres utilisateur (durée minimale, dossiers exclus, dossiers
 * livres audio) ne sont PAS appliqués ici : ils le sont à la publication, voir
 * `LibraryBuilder`. Ainsi, changer un filtre ne demande aucun rescan.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["path"], unique = true),
        Index("albumId"),
        Index("artistId"),
        Index("dateAddedSec"),
    ],
)
data class LibrarySongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val genre: String?,
    val durationMs: Long,
    val track: Int,
    val disc: Int,
    val year: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val path: String,
    val isPodcast: Boolean,
    /** `IS_AUDIOBOOK` de MediaStore (Android 10+). Le drapeau « dossier livre audio » se calcule à la publication. */
    val isNativeAudiobook: Boolean,
    val brokenEncoding: Boolean,
    /** `GENERATION_MODIFIED` (Android 11+), 0 avant. */
    val generation: Long,
)

/** État du dernier scan ; une seule ligne (`id = 0`). */
@Entity(tableName = "library_scan_state")
data class LibraryScanStateEntity(
    @PrimaryKey val id: Int = 0,
    val lastGeneration: Long,
    val volumeName: String,
    val lastFullScanAt: Long,
    val lastScanAt: Long,
    /** Incrémenté quand la projection ou la liste noire du scanner change : force un scan complet. */
    val scannerVersion: Int,
)
