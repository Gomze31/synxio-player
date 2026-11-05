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
import fr.synxio.player.data.db.QueueDao
import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.DailyMix
import fr.synxio.player.data.model.Genre
import fr.synxio.player.data.model.Memory
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.RadioStation
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.DailyMixRepository
import fr.synxio.player.data.repo.MemoryRepository
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.PlaylistRepository
import fr.synxio.player.data.repo.RadioRepository
import fr.synxio.player.data.repo.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
    private val dailyMixes: DailyMixRepository,
    private val memories: MemoryRepository,
    queueDao: QueueDao,
    podcastProgressDao: fr.synxio.player.data.db.PodcastProgressDao,
    @fr.synxio.player.di.ApplicationScope scope: kotlinx.coroutines.CoroutineScope,
) {

    /** Positions de reprise tenues à jour en continu : l'hôte lit l'arborescence sans attendre. */
    private val positions = podcastProgressDao.observeAll()
        .map { list -> list.associateBy { it.songId } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * File sauvegardée, relue comme au démarrage (titres disparus écartés). Tenue à jour
     * tant que le service collecte [changes], c'est-à-dire tant qu'un hôte peut naviguer.
     */
    private val lastQueue: StateFlow<BrowseQueue?> = combine(
        queueDao.observeQueue(), queueDao.observeState(), music.library,
    ) { rows, state, library ->
        val byId = library.songs.associateBy { it.id }
        restoreQueue(rows.sortedBy { it.position }.map { it.songId }, state?.currentIndex ?: 0, state?.positionMs ?: 0L, byId)
            ?.let { BrowseQueue(it.items, it.index, it.positionMs) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val source = object : BrowseSource {
        override val hasPermission: Boolean get() = hasAudioPermission()
        override val hasScanned: Boolean get() = music.library.value.hasScanned
        override val songs: List<Song> get() = music.library.value.songs
        override val albums: List<Album> get() = music.library.value.albums
        override val artists: List<Artist> get() = music.library.value.artists
        override val genres: List<Genre> get() = music.library.value.genres
        override val playlists: List<Playlist> get() = this@MediaLibraryTree.playlists.playlists.value
        override val favorites: List<Song> get() = music.favoriteSongs.value
        override val favoriteIds: Set<Long> get() = music.favoriteIds.value
        override val recentlyPlayed: List<Song> get() = music.recentlyPlayed.value
        override val mostPlayed: List<Song> get() = music.mostPlayed.value
        override val recentlyAdded: List<Song> get() = music.recentlyAdded.value
        override val radios: List<RadioStation> get() = this@MediaLibraryTree.radios.favoriteStationsNow
        override val audiobooks: List<Song> get() = music.library.value.audiobooks
        override val podcasts: List<Song> get() = music.library.value.podcasts
        override val savedPositions get() = positions.value
        override val dailyMixes: List<DailyMix> get() = this@MediaLibraryTree.dailyMixes.mixes.value
        override val memories: List<Memory> get() = this@MediaLibraryTree.memories.memories.value
        override val playCounts: Map<Long, Int> get() = music.allStats.value.mapValues { it.value.playCount }
        override val lastQueue: BrowseQueue? get() = this@MediaLibraryTree.lastQueue.value
        override fun search(query: String, limitPerSection: Int): SearchResults = music.search(query, limitPerSection)
    }

    val tree = BrowseTree(source)

    val hasPermission: Boolean get() = hasAudioPermission()

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
        // Partagés « tant qu'abonnés » : les collecter ici les tient vivants tant que le service écoute.
        dailyMixes.mixes,
        memories.memories,
        lastQueue,
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

    /**
     * Nombre d'actions par élément que l'hôte sait afficher, lu dans ses indices de racine.
     * Zéro tant qu'aucun hôte ne l'a annoncé : rien n'est déclaré.
     */
    @Volatile var actionLimit: Int = 0

    private class ActionSpec(val id: String, val label: String, val icon: Int)

    private val actionSpecs = listOf(
        ActionSpec(AutoKeys.ACTION_FAVORITE, "Favori", R.drawable.ic_auto_favorite_border),
        ActionSpec(AutoKeys.ACTION_UNFAVORITE, "Retirer des favoris", R.drawable.ic_auto_favorite),
        ActionSpec(AutoKeys.ACTION_PLAY_NEXT, "Lire ensuite", R.drawable.ic_auto_play_next),
        ActionSpec(AutoKeys.ACTION_ADD_TO_QUEUE, "Ajouter à la file", R.drawable.ic_auto_queue),
        ActionSpec(AutoKeys.ACTION_SHUFFLE, "Aléatoire", R.drawable.ic_auto_shuffle),
    )

    /** Styles par défaut de l'hôte et actions du menu contextuel, transmis avec la racine. */
    fun rootExtras(): Bundle = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        if (actionLimit > 0) {
            val list = ArrayList<Bundle>(actionSpecs.size)
            for (spec in actionSpecs) {
                list += Bundle().apply {
                    putString(AutoKeys.ACTION_ID, spec.id)
                    putString(AutoKeys.ACTION_LABEL, spec.label)
                    putString(AutoKeys.ACTION_ICON_URI, resourceUri(spec.icon).toString())
                    putBundle(AutoKeys.ACTION_EXTRAS, Bundle.EMPTY)
                }
            }
            putParcelableArrayList(AutoKeys.ROOT_EXTRAS_ACTION_LIST, list)
        }
    }

    /** Taille de pochette annoncée par chaque navigateur (indice de racine), par `uid`. */
    private val artSizes = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun setArtSize(uid: Int, size: Int) { artSizes[uid] = size }

    fun children(parentId: String, page: Int, pageSize: Int, uid: Int): List<MediaItem> {
        val nodes = tree.children(parentId)
        val from = (page.toLong() * pageSize).coerceAtMost(nodes.size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(nodes.size.toLong()).toInt()
        val size = artSizes[uid]
        return nodes.subList(from, to).map { it.toMediaItem(size) }
    }

    fun itemById(mediaId: String, uid: Int): MediaItem? {
        if (mediaId == BrowseIds.ROOT) return rootItem
        return tree.item(mediaId)?.toMediaItem(artSizes[uid])
    }

    fun search(query: String, uid: Int): List<MediaItem> = tree.search(query).map { it.toMediaItem(artSizes[uid]) }

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

    private fun BrowseNode.toMediaItem(size: Int?): MediaItem {
        song?.let { return songItem(it, size) }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(if (id.startsWith(BrowseIds.ALBUM_PREFIX)) subtitle else null)
            .setArtworkUri(artworkFor(art, size))
            .setIsBrowsable(kind.browsable)
            .setIsPlayable(kind.playable)
            .setMediaType(mediaType())
            .setExtras(extras())
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    /** Titre parcouru : le `MediaItem` complet du morceau, sous son identifiant en contexte. */
    private fun BrowseNode.songItem(song: Song, size: Int?): MediaItem {
        val base = song.toMediaItem()
        val extras = Bundle(base.mediaMetadata.extras ?: Bundle.EMPTY).apply { putAll(extras()) }
        return base.buildUpon()
            .setMediaId(id)
            .setMediaMetadata(
                base.mediaMetadata.buildUpon()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .apply { if (size != null) setArtworkUri(ArtworkProvider.sizedUri(song.artworkUri, size)) }
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun BrowseNode.extras(): Bundle = Bundle().apply {
        browsableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it.toConstant()) }
        playableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it.toConstant()) }
        groupTitle?.let { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
        // L'hôte lit cet indice pour l'élément lui-même, pas pour ses enfants.
        selfStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, it.toConstant()) }
        progress?.let {
            putInt(
                MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                when (it.completion) {
                    Completion.NOT_PLAYED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                    Completion.PARTIALLY_PLAYED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                    Completion.FULLY_PLAYED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                },
            )
            putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, it.fraction.toDouble())
        }
        if (actionLimit > 0 && actions.isNotEmpty()) {
            putStringArrayList(AutoKeys.ITEM_ACTION_ID_LIST, ArrayList(actions.take(actionLimit)))
        }
    }

    private fun BrowseNode.mediaType(): Int = when (kind) {
        NodeKind.ALBUM -> MediaMetadata.MEDIA_TYPE_ALBUM
        NodeKind.ARTIST -> MediaMetadata.MEDIA_TYPE_ARTIST
        NodeKind.GENRE -> MediaMetadata.MEDIA_TYPE_GENRE
        NodeKind.PLAYLIST -> MediaMetadata.MEDIA_TYPE_PLAYLIST
        NodeKind.SONG -> MediaMetadata.MEDIA_TYPE_MUSIC
        NodeKind.RADIO -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
        NodeKind.ACTION -> MediaMetadata.MEDIA_TYPE_PLAYLIST
        NodeKind.FOLDER_PLAYABLE -> when {
            id.startsWith(BrowseIds.ALBUM_PREFIX) -> MediaMetadata.MEDIA_TYPE_ALBUM
            id.startsWith(BrowseIds.GENRE_PREFIX) -> MediaMetadata.MEDIA_TYPE_GENRE
            else -> MediaMetadata.MEDIA_TYPE_PLAYLIST
        }
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

    private fun artworkFor(art: NodeArt?, size: Int?): Uri? = when (art) {
        null -> null
        is NodeArt.Icon -> iconUri(art.icon)
        is NodeArt.OfSong -> ArtworkProvider.sizedUri(art.song.artworkUri, size)
        is NodeArt.OfAlbum -> ArtworkProvider.sizedUri(art.album.artworkUri, size)
        is NodeArt.OfArtist -> ArtworkProvider.sizedUri(art.artist.artworkUri, size) ?: iconUri(BrowseIcon.ARTISTS)
        is NodeArt.OfRadio ->
            if (art.station.favicon.isBlank()) iconUri(BrowseIcon.RADIOS)
            else ArtworkProvider.radioUri(art.station.stationUuid)
        is NodeArt.Mosaic ->
            if (art.songs.isEmpty()) iconUri(BrowseIcon.PLAYLISTS)
            else ArtworkProvider.mosaicUri(art.songs.map { it.id }, size)
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
        return resourceUri(res)
    }

    private fun resourceUri(res: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(context.resources.getResourceTypeName(res))
        .appendPath(context.resources.getResourceEntryName(res))
        .build()
}
