package fr.synxio.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import fr.synxio.player.core.util.fuzzyScore
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.PlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arborescence exposée aux clients `MediaBrowser` : Android Auto, Assistant,
 * Wear OS et notre propre widget.
 */
@Singleton
class MediaLibraryTree @Inject constructor(
    private val music: MusicRepository,
    private val playlists: PlaylistRepository,
) {

    val rootItem: MediaItem = browsableItem(
        id = ROOT,
        title = "Synxio",
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    )

    fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> rootCategories()
        parentId == CAT_SONGS -> music.library.value.songs.toMediaItems()
        parentId == CAT_FAVORITES -> music.favoriteSongs.value.toMediaItems()
        parentId == CAT_RECENT -> music.recentlyAdded.value.toMediaItems()
        parentId == CAT_MOST_PLAYED -> music.mostPlayed.value.take(50).toMediaItems()
        parentId == CAT_SHUFFLE -> music.library.value.songs.shuffled().take(100).toMediaItems()

        parentId == CAT_ALBUMS -> music.library.value.albums
            .sortedBy { it.title.lowercase() }
            .map { browsableItem("$ALBUM_PREFIX${it.id}", it.title, it.artist, it.artworkUri, MediaMetadata.MEDIA_TYPE_ALBUM) }

        parentId == CAT_ARTISTS -> music.library.value.artists
            .sortedBy { it.name.lowercase() }
            .map { browsableItem("$ARTIST_PREFIX${it.name}", it.name, "${it.albumCount} albums", it.artworkUri, MediaMetadata.MEDIA_TYPE_ARTIST) }

        parentId == CAT_GENRES -> music.library.value.genres
            .map { browsableItem("$GENRE_PREFIX${it.name}", it.name, "${it.songCount} titres", it.artworkUris.firstOrNull()) }

        parentId == CAT_PLAYLISTS -> playlists.playlists.value
            .map { browsableItem("$PLAYLIST_PREFIX${it.id}", it.name, "${it.songCount} titres", it.artworkUris.firstOrNull(), MediaMetadata.MEDIA_TYPE_PLAYLIST) }

        parentId.startsWith(ALBUM_PREFIX) ->
            music.albumById(parentId.removePrefix(ALBUM_PREFIX).toLongOrNull() ?: -1)
                ?.songs.orEmpty().toMediaItems()

        parentId.startsWith(ARTIST_PREFIX) ->
            music.artistByName(parentId.removePrefix(ARTIST_PREFIX))?.songs.orEmpty().toMediaItems()

        parentId.startsWith(GENRE_PREFIX) ->
            music.genreByName(parentId.removePrefix(GENRE_PREFIX))?.songs.orEmpty().toMediaItems()

        parentId.startsWith(PLAYLIST_PREFIX) ->
            playlists.playlists.value
                .firstOrNull { it.id.toString() == parentId.removePrefix(PLAYLIST_PREFIX) }
                ?.songs.orEmpty().toMediaItems()

        else -> emptyList()
    }

    fun itemById(mediaId: String): MediaItem? = when {
        mediaId == ROOT -> rootItem
        mediaId.toLongOrNull() != null -> music.songById(mediaId.toLong())?.toMediaItem()
        else -> rootCategories().firstOrNull { it.mediaId == mediaId }
    }

    /**
     * Résout « Joue les Daft Punk » : on cherche d'abord un artiste, puis un album,
     * puis on retombe sur les titres.
     */
    fun search(query: String): List<Song> {
        if (query.isBlank()) return music.library.value.songs.shuffled()

        val artist = music.library.value.artists
            .maxByOrNull { fuzzyScore(it.name, query) }
            ?.takeIf { fuzzyScore(it.name, query) >= 400 }
        if (artist != null) return artist.songs

        val album = music.library.value.albums
            .maxByOrNull { fuzzyScore(it.title, query) }
            ?.takeIf { fuzzyScore(it.title, query) >= 400 }
        if (album != null) return album.songs

        val results = music.search(query, limitPerSection = 50)
        return results.songs.ifEmpty { results.albums.flatMap { it.songs } }
    }

    private fun rootCategories(): List<MediaItem> = listOf(
        browsableItem(CAT_SONGS, "Tous les titres"),
        browsableItem(CAT_ALBUMS, "Albums"),
        browsableItem(CAT_ARTISTS, "Artistes"),
        browsableItem(CAT_GENRES, "Genres"),
        browsableItem(CAT_PLAYLISTS, "Playlists"),
        browsableItem(CAT_FAVORITES, "Favoris"),
        browsableItem(CAT_RECENT, "Ajoutés récemment"),
        browsableItem(CAT_MOST_PLAYED, "Les plus écoutés"),
        browsableItem(CAT_SHUFFLE, "Lecture aléatoire"),
    )

    companion object {
        const val ROOT = "synxio://root"
        const val CAT_SONGS = "synxio://songs"
        const val CAT_ALBUMS = "synxio://albums"
        const val CAT_ARTISTS = "synxio://artists"
        const val CAT_GENRES = "synxio://genres"
        const val CAT_PLAYLISTS = "synxio://playlists"
        const val CAT_FAVORITES = "synxio://favorites"
        const val CAT_RECENT = "synxio://recent"
        const val CAT_MOST_PLAYED = "synxio://most-played"
        const val CAT_SHUFFLE = "synxio://shuffle"

        const val ALBUM_PREFIX = "synxio://album/"
        const val ARTIST_PREFIX = "synxio://artist/"
        const val GENRE_PREFIX = "synxio://genre/"
        const val PLAYLIST_PREFIX = "synxio://playlist/"
    }
}
