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
