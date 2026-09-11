package fr.synxio.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import fr.synxio.player.data.model.Song

const val EXTRA_PATH = "fr.synxio.player.PATH"
const val EXTRA_SONG_ID = "fr.synxio.player.SONG_ID"
const val EXTRA_DURATION = "fr.synxio.player.DURATION"

/**
 * Convertit un morceau en [MediaItem].
 *
 * Le `mediaId` est l'id MediaStore : c'est lui qui permet de retrouver le morceau
 * complet depuis le lecteur, la notification, le widget ou Android Auto.
 */
fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(EXTRA_PATH, path)
        putLong(EXTRA_SONG_ID, id)
        putLong(EXTRA_DURATION, durationMs)
    }

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(displayArtist)
        .setAlbumTitle(displayAlbum)
        .setAlbumArtist(albumArtist ?: displayArtist)
        .setGenre(genre)
        .setTrackNumber(track.takeIf { it > 0 })
        .setDiscNumber(disc.takeIf { it > 0 })
        .setRecordingYear(year.takeIf { it > 0 })
        .setArtworkUri(artworkUri)
        .setDurationMs(durationMs)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(uri)
                .setSearchQuery(null)
                .build()
        )
        .build()
}

fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

val MediaItem.songId: Long
    get() = mediaId.toLongOrNull() ?: -1L

val MediaItem.songPath: String?
    get() = mediaMetadata.extras?.getString(EXTRA_PATH)

/**
 * Reconstruit un objet Song basique à partir des métadonnées d'un MediaItem.
 * Utile pour les flux réseau (webradios) qui ne sont pas en base de données.
 */
fun MediaItem.toSynthesizedSong(): Song = Song(
    id = songId,
    title = mediaMetadata.title?.toString() ?: "Titre inconnu",
    artist = mediaMetadata.artist?.toString() ?: "Artiste inconnu",
    artistId = -1L,
    album = mediaMetadata.albumTitle?.toString() ?: "Inconnu",
    albumId = -1L,
    albumArtist = mediaMetadata.albumArtist?.toString(),
    genre = mediaMetadata.genre?.toString(),
    durationMs = mediaMetadata.extras?.getLong(EXTRA_DURATION) ?: 0L,
    track = mediaMetadata.trackNumber ?: 0,
    disc = mediaMetadata.discNumber ?: 0,
    year = mediaMetadata.recordingYear ?: 0,
    dateAddedSec = 0L,
    dateModifiedSec = 0L,
    sizeBytes = 0L,
    mimeType = "audio/mpeg",
    path = mediaMetadata.extras?.getString(EXTRA_PATH) ?: localConfiguration?.uri?.toString() ?: ""
)

/** Nœud de navigation (album, artiste, playlist…) pour Android Auto et `MediaBrowser`. */
fun browsableItem(
    id: String,
    title: String,
    subtitle: String? = null,
    artworkUri: Uri? = null,
    mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUri)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
    )
    .build()
