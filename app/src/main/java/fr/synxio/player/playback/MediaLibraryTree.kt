package fr.synxio.player.playback

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.R
import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.Genre
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.RadioStation
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.PlaylistRepository
import fr.synxio.player.data.repo.RadioRepository
import fr.synxio.player.data.repo.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arborescence exposée aux clients `MediaBrowser` : Android Auto, Assistant, Wear OS.
 *
 * La structure vit dans [BrowseTree], sans Android, pour être testée ; ici on la branche
 * sur les dépôts et on la traduit en `MediaItem` : styles de grille ou de liste, icônes de
 * catégorie, pochettes lisibles par l'hôte.
 */
@Singleton
class MediaLibraryTree @Inject constructor(
    @ApplicationContext private val context: Context,
    private val music: MusicRepository,
    private val playlists: PlaylistRepository,
    private val radios: RadioRepository,
    podcastProgressDao: fr.synxio.player.data.db.PodcastProgressDao,
    @fr.synxio.player.di.ApplicationScope scope: kotlinx.coroutines.CoroutineScope,
) {

    /** Positions de reprise tenues à jour en continu : l'hôte lit l'arborescence sans attendre. */
    private val positions = podcastProgressDao.observeAll()
        .map { list -> list.associateBy { it.songId } }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyMap())

    private val source = object : BrowseSource {
        override val hasPermission: Boolean get() = hasAudioPermission()
        override val hasScanned: Boolean get() = music.library.value.hasScanned
        override val songs: List<Song> get() = music.library.value.songs
        override val albums: List<Album> get() = music.library.value.albums
        override val artists: List<Artist> get() = music.library.value.artists
        override val genres: List<Genre> get() = music.library.value.genres
        override val playlists: List<Playlist> get() = this@MediaLibraryTree.playlists.playlists.value
        override val favorites: List<Song> get() = music.favoriteSongs.value
        override val recentlyPlayed: List<Song> get() = music.recentlyPlayed.value
        override val mostPlayed: List<Song> get() = music.mostPlayed.value
        override val recentlyAdded: List<Song> get() = music.recentlyAdded.value
        override val radios: List<RadioStation> get() = this@MediaLibraryTree.radios.favoriteStationsNow
        override val audiobooks: List<Song> get() = music.library.value.audiobooks
        override val podcasts: List<Song> get() = music.library.value.podcasts
        override val savedPositions get() = positions.value
        override fun search(query: String, limitPerSection: Int): SearchResults = music.search(query, limitPerSection)
    }

    val tree = BrowseTree(source)

    /**
     * Signale que ce que voit la voiture a changé : scan terminé, playlist ou favori
     * modifié, radio ajoutée. Le service relaie aux navigateurs abonnés.
     */
    val changes: Flow<Unit> = merge(
        music.library.map { it.hasScanned to it.songs }
            .distinctUntilChanged { a, b -> a.first == b.first && a.second === b.second },
        playlists.playlists,
        music.favoriteIds,
        music.recentlyPlayed,
        radios.favoriteStations,
    ).map { }

    val rootItem: MediaItem
        get() = MediaItem.Builder()
            .setMediaId(BrowseIds.ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Synxio")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    /** Styles par défaut de l'hôte, transmis avec la racine. */
    fun rootExtras(): Bundle = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }

    fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val nodes = tree.children(parentId)
        val from = (page.toLong() * pageSize).coerceAtMost(nodes.size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(nodes.size.toLong()).toInt()
        return nodes.subList(from, to).map { it.toMediaItem() }
    }

    fun itemById(mediaId: String): MediaItem? {
        if (mediaId == BrowseIds.ROOT) return rootItem
        return tree.item(mediaId)?.toMediaItem()
    }

    fun search(query: String): List<MediaItem> = tree.search(query).map { it.toMediaItem() }

    /** File jouable d'un élément choisi sur l'hôte : titre en contexte ou lecture aléatoire. */
    fun queueFor(mediaId: String): BrowseQueue? = tree.queueFor(mediaId)

    fun resolveVoice(request: VoiceRequest): BrowseQueue? = tree.resolveVoice(request)

    /** Webradio favorite prête à jouer, avec un logo que l'hôte sait afficher. */
    fun radioItem(mediaId: String): MediaItem? {
        val station = tree.radioFor(mediaId) ?: return null
        val item = station.toMediaItem()
        if (station.favicon.isBlank()) return item
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(ArtworkProvider.radioUri(station.stationUuid)).build())
            .build()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // --- Traduction en MediaItem ----------------------------------------------------

    private fun BrowseNode.toMediaItem(): MediaItem {
        song?.let { return songItem(it) }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(if (kind == NodeKind.ALBUM) subtitle else null)
            .setArtworkUri(artworkFor(art))
            .setIsBrowsable(kind.browsable)
            .setIsPlayable(kind.playable)
            .setMediaType(mediaType())
            .setExtras(extras())
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    /** Titre parcouru : le `MediaItem` complet du morceau, sous son identifiant en contexte. */
    private fun BrowseNode.songItem(song: Song): MediaItem {
        val base = song.toMediaItem()
        val extras = Bundle(base.mediaMetadata.extras ?: Bundle.EMPTY).apply { putAll(extras()) }
        return base.buildUpon()
            .setMediaId(id)
            .setMediaMetadata(
                base.mediaMetadata.buildUpon()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun BrowseNode.extras(): Bundle = Bundle().apply {
        browsableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it.toConstant()) }
        playableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it.toConstant()) }
        groupTitle?.let { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
    }

    private fun BrowseNode.mediaType(): Int = when (kind) {
        NodeKind.ALBUM -> MediaMetadata.MEDIA_TYPE_ALBUM
        NodeKind.ARTIST -> MediaMetadata.MEDIA_TYPE_ARTIST
        NodeKind.GENRE -> MediaMetadata.MEDIA_TYPE_GENRE
        NodeKind.PLAYLIST -> MediaMetadata.MEDIA_TYPE_PLAYLIST
        NodeKind.SONG -> MediaMetadata.MEDIA_TYPE_MUSIC
        NodeKind.RADIO -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
        NodeKind.ACTION -> MediaMetadata.MEDIA_TYPE_PLAYLIST
        NodeKind.FOLDER, NodeKind.INFO -> when (id) {
            BrowseIds.CAT_ALBUMS -> MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
            BrowseIds.CAT_ARTISTS -> MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
            BrowseIds.CAT_GENRES -> MediaMetadata.MEDIA_TYPE_FOLDER_GENRES
            BrowseIds.TAB_PLAYLISTS -> MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            BrowseIds.TAB_RADIOS -> MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
            BrowseIds.CAT_AUDIOBOOKS -> MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS
            BrowseIds.CAT_PODCASTS -> MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
            else -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        }
    }

    private fun ChildStyle.toConstant(): Int = when (this) {
        ChildStyle.LIST -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        ChildStyle.GRID -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        ChildStyle.CATEGORY_LIST -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
        ChildStyle.CATEGORY_GRID -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM
    }

    private fun artworkFor(art: NodeArt?): Uri? = when (art) {
        null -> null
        is NodeArt.Icon -> iconUri(art.icon)
        is NodeArt.OfSong -> ArtworkProvider.publicUri(art.song.artworkUri)
        is NodeArt.OfAlbum -> ArtworkProvider.publicUri(art.album.artworkUri)
        is NodeArt.OfArtist -> ArtworkProvider.publicUri(art.artist.artworkUri) ?: iconUri(BrowseIcon.ARTISTS)
        is NodeArt.OfRadio ->
            if (art.station.favicon.isBlank()) iconUri(BrowseIcon.RADIOS)
            else ArtworkProvider.radioUri(art.station.stationUuid)
    }

    /** Les hôtes lisent les vecteurs en `android.resource://`, teintés à leurs couleurs. */
    private fun iconUri(icon: BrowseIcon): Uri {
        val res = when (icon) {
            BrowseIcon.HOME -> R.drawable.ic_auto_home
            BrowseIcon.LIBRARY -> R.drawable.ic_auto_library
            BrowseIcon.PLAYLISTS -> R.drawable.ic_auto_playlists
            BrowseIcon.RADIOS -> R.drawable.ic_auto_radio
            BrowseIcon.ALBUMS -> R.drawable.ic_auto_album
            BrowseIcon.ARTISTS -> R.drawable.ic_auto_artist
            BrowseIcon.GENRES -> R.drawable.ic_auto_genre
            BrowseIcon.SONGS -> R.drawable.ic_auto_songs
            BrowseIcon.FAVORITES -> R.drawable.ic_auto_favorite
            BrowseIcon.HISTORY -> R.drawable.ic_auto_history
            BrowseIcon.MOST_PLAYED -> R.drawable.ic_auto_trending
            BrowseIcon.NEW -> R.drawable.ic_auto_new
            BrowseIcon.SHUFFLE -> R.drawable.ic_auto_shuffle
            BrowseIcon.INFO -> R.drawable.ic_auto_info
            BrowseIcon.BOOKS -> R.drawable.ic_auto_book
            BrowseIcon.PODCASTS -> R.drawable.ic_auto_podcast
            BrowseIcon.RESUME -> R.drawable.ic_auto_resume
        }
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(context.resources.getResourceTypeName(res))
            .appendPath(context.resources.getResourceEntryName(res))
            .build()
    }
}
