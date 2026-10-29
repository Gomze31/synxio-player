package fr.synxio.player.playback

import fr.synxio.player.core.util.fuzzyScore
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.normalizeForSearch
import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.BookProgress
import fr.synxio.player.data.model.EpisodeState
import fr.synxio.player.data.model.Genre
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.RadioStation
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.chapterLabel
import fr.synxio.player.data.model.episodeState
import fr.synxio.player.data.model.inBookOrder
import fr.synxio.player.data.model.remainingLabel
import fr.synxio.player.data.model.statusLabel
import fr.synxio.player.data.repo.SearchResults

/**
 * Identifiants de l'arborescence exposée à Android Auto, Assistant et aux `MediaBrowser`.
 *
 * Un titre parcouru porte l'identifiant de son parent : « album/12|345 » joue le titre 345
 * dans la file de l'album 12. Sans ce contexte, choisir un titre dans un album sur la
 * voiture lançait toute la bibliothèque à partir de lui.
 */
object BrowseIds {
    const val ROOT = "synxio://root"

    const val TAB_HOME = "synxio://tab/home"
    const val TAB_LIBRARY = "synxio://tab/library"
    const val TAB_PLAYLISTS = "synxio://tab/playlists"
    const val TAB_RADIOS = "synxio://tab/radios"

    const val CAT_SONGS = "synxio://songs"
    const val CAT_ALBUMS = "synxio://albums"
    const val CAT_ARTISTS = "synxio://artists"
    const val CAT_GENRES = "synxio://genres"
    const val CAT_FAVORITES = "synxio://favorites"
    const val CAT_RECENT = "synxio://recent"
    const val CAT_RECENTLY_PLAYED = "synxio://recently-played"
    const val CAT_MOST_PLAYED = "synxio://most-played"
    const val CAT_CONTINUE = "synxio://continue"
    const val CAT_AUDIOBOOKS = "synxio://audiobooks"
    const val CAT_PODCASTS = "synxio://podcasts"

    const val ALBUM_PREFIX = "synxio://album/"
    const val ARTIST_PREFIX = "synxio://artist/"
    const val GENRE_PREFIX = "synxio://genre/"
    const val PLAYLIST_PREFIX = "synxio://playlist/"
    /** Livre audio : `synxio://book/` suivi du chemin de son dossier. */
    const val BOOK_PREFIX = "synxio://book/"
    const val RADIO_PREFIX = "synxio://radio/"
    const val SEARCH_PREFIX = "synxio://search/"
    const val INFO_PREFIX = "synxio://info/"

    /** « Lecture aléatoire » d'un parent : `synxio://shuffle/` suivi de l'identifiant du parent. */
    const val SHUFFLE_PREFIX = "synxio://shuffle/"
    const val SHUFFLE_ALL = SHUFFLE_PREFIX + CAT_SONGS

    private const val CONTEXT_SEPARATOR = '|'

    fun songInContext(parentId: String, songId: Long): String = "$parentId$CONTEXT_SEPARATOR$songId"

    /** Parent et titre d'un identifiant contextuel, ou null pour tout autre identifiant. */
    fun parseSongInContext(id: String): Pair<String, Long>? {
        val cut = id.lastIndexOf(CONTEXT_SEPARATOR)
        if (cut <= 0) return null
        val songId = id.substring(cut + 1).toLongOrNull() ?: return null
        return id.substring(0, cut) to songId
    }
}

/** Icônes de catégorie, traduites en ressources par [MediaLibraryTree]. */
enum class BrowseIcon { HOME, LIBRARY, PLAYLISTS, RADIOS, ALBUMS, ARTISTS, GENRES, SONGS, FAVORITES, HISTORY, MOST_PLAYED, NEW, SHUFFLE, INFO, BOOKS, PODCASTS, RESUME }

/** Mise en page demandée à l'hôte pour les enfants d'un nœud. */
enum class ChildStyle { LIST, GRID, CATEGORY_LIST, CATEGORY_GRID }

/** D'où vient l'image d'un nœud. La résolution en URI se fait côté Android. */
sealed interface NodeArt {
    data class Icon(val icon: BrowseIcon) : NodeArt
    data class OfSong(val song: Song) : NodeArt
    data class OfAlbum(val album: Album) : NodeArt
    data class OfArtist(val artist: Artist) : NodeArt
    data class OfRadio(val station: RadioStation) : NodeArt
}

enum class NodeKind(val browsable: Boolean, val playable: Boolean) {
    FOLDER(true, false),
    ALBUM(true, false),
    ARTIST(true, false),
    GENRE(true, false),
    PLAYLIST(true, false),
    SONG(false, true),
    RADIO(false, true),
    /** Élément jouable qui lance une file calculée (lecture aléatoire). */
    ACTION(false, true),
    /** Message d'état (bibliothèque vide, permission manquante) : ni jouable ni navigable. */
    INFO(false, false),
}

data class BrowseNode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val kind: NodeKind,
    val art: NodeArt? = null,
    /** Mise en page des enfants navigables de ce nœud. */
    val browsableStyle: ChildStyle? = null,
    /** Mise en page des enfants jouables de ce nœud. */
    val playableStyle: ChildStyle? = null,
    /** En-tête de section, pour les éléments consécutifs qui le partagent. */
    val groupTitle: String? = null,
    val song: Song? = null,
    val station: RadioStation? = null,
)

/** Ce que l'arborescence lit de l'app : la bibliothèque, les playlists et les radios. */
interface BrowseSource {
    val hasPermission: Boolean
    val hasScanned: Boolean
    val songs: List<Song>
    val albums: List<Album>
    val artists: List<Artist>
    val genres: List<Genre>
    val playlists: List<Playlist>
    val favorites: List<Song>
    val recentlyPlayed: List<Song>
    val mostPlayed: List<Song>
    val recentlyAdded: List<Song>
    val radios: List<RadioStation>
    val audiobooks: List<Song> get() = emptyList()
    val podcasts: List<Song> get() = emptyList()
    /** Positions de reprise des podcasts et chapitres, par identifiant de morceau. */
    val savedPositions: Map<Long, PodcastProgressEntity> get() = emptyMap()
    fun search(query: String, limitPerSection: Int): SearchResults
}

/** Demande vocale : « Ok Google, joue … sur Synxio », avec les précisions de l'Assistant. */
data class VoiceRequest(
    val query: String,
    /** `MediaStore.EXTRA_MEDIA_FOCUS` : type de média que l'Assistant a reconnu. */
    val focus: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val genre: String? = null,
    val playlist: String? = null,
) {
    companion object {
        const val FOCUS_ARTIST = "vnd.android.cursor.item/artist"
        const val FOCUS_ALBUM = "vnd.android.cursor.item/album"
        const val FOCUS_GENRE = "vnd.android.cursor.item/genre"
        const val FOCUS_PLAYLIST = "vnd.android.cursor.item/playlist"
        const val FOCUS_TITLE = "vnd.android.cursor.item/audio"
        const val FOCUS_ANY = "vnd.android.cursor.item/*"
    }
}

/** File à lancer, titre de départ et position dans ce titre (reprise d'un livre ou d'un podcast). */
data class BrowseQueue(val songs: List<Song>, val startIndex: Int = 0, val startPositionMs: Long = 0L)

/**
 * Arborescence de navigation pour la voiture, sans dépendance à Android.
 *
 * Racine en quatre onglets (Pour toi, Bibliothèque, Playlists, Radios), comme le demande
 * Android Auto ; chaque catégorie précise si ses enfants s'affichent en liste ou en grille.
 */
class BrowseTree(
    private val source: BrowseSource,
    private val shuffle: (List<Song>) -> List<Song> = { it.shuffled() },
) {

    fun root(): List<BrowseNode> = listOf(
        BrowseNode(BrowseIds.TAB_HOME, "Pour toi", kind = NodeKind.FOLDER, art = NodeArt.Icon(BrowseIcon.HOME),
            browsableStyle = ChildStyle.CATEGORY_LIST, playableStyle = ChildStyle.LIST),
        BrowseNode(BrowseIds.TAB_LIBRARY, "Bibliothèque", kind = NodeKind.FOLDER, art = NodeArt.Icon(BrowseIcon.LIBRARY),
            browsableStyle = ChildStyle.CATEGORY_LIST, playableStyle = ChildStyle.LIST),
        BrowseNode(BrowseIds.TAB_PLAYLISTS, "Playlists", kind = NodeKind.FOLDER, art = NodeArt.Icon(BrowseIcon.PLAYLISTS),
            browsableStyle = ChildStyle.GRID, playableStyle = ChildStyle.LIST),
        BrowseNode(BrowseIds.TAB_RADIOS, "Radios", kind = NodeKind.FOLDER, art = NodeArt.Icon(BrowseIcon.RADIOS),
            browsableStyle = ChildStyle.GRID, playableStyle = ChildStyle.GRID),
    )

    fun children(parentId: String): List<BrowseNode> {
        if (parentId == BrowseIds.ROOT) return root()
        // Les radios sont des flux réseau : elles se passent de la permission audio.
        if (parentId == BrowseIds.TAB_RADIOS) return radios()
        if (!source.hasPermission) return listOf(permissionNeeded())

        return when {
            parentId == BrowseIds.TAB_HOME -> home()
            parentId == BrowseIds.TAB_LIBRARY -> library()
            parentId == BrowseIds.TAB_PLAYLISTS -> playlists()
            parentId == BrowseIds.CAT_ALBUMS -> albums(source.albums.sortedBy { it.title.lowercase() })
            parentId == BrowseIds.CAT_ARTISTS -> artists()
            parentId == BrowseIds.CAT_GENRES -> genres()
            parentId == BrowseIds.CAT_AUDIOBOOKS -> books()
            parentId == BrowseIds.CAT_CONTINUE -> continueListening().map { it.node }
            // Un livre ou des podcasts se lisent dans l'ordre : pas de lecture aléatoire.
            parentId == BrowseIds.CAT_PODCASTS -> source.podcasts.map { episodeNode(BrowseIds.CAT_PODCASTS, it) }
            parentId.startsWith(BrowseIds.BOOK_PREFIX) -> songsOf(parentId).orEmpty().map { songNode(parentId, it) }
            parentId.startsWith(BrowseIds.ARTIST_PREFIX) -> artistDetail(parentId)
            else -> songsOf(parentId)?.let { songs ->
                if (songs.isEmpty()) listOf(emptyFor(parentId)) else withShuffle(parentId, songs) + songNodes(parentId, songs)
            } ?: emptyList()
        }.ifEmpty { listOf(emptyFor(parentId)) }
    }

    /** Nœud isolé : catégorie, album, playlist, titre en contexte. */
    fun item(id: String): BrowseNode? {
        if (id == BrowseIds.ROOT) return BrowseNode(BrowseIds.ROOT, "Synxio", kind = NodeKind.FOLDER)
        root().firstOrNull { it.id == id }?.let { return it }
        BrowseIds.parseSongInContext(id)?.let { (parent, songId) ->
            if (parent == BrowseIds.CAT_CONTINUE) return continueListening().firstOrNull { it.song.id == songId }?.node
            val song = songsOf(parent)?.firstOrNull { it.id == songId } ?: return null
            return songNode(parent, song)
        }
        id.toLongOrNull()?.let { songId ->
            val song = source.songs.firstOrNull { it.id == songId } ?: return null
            return songNode(BrowseIds.CAT_SONGS, song).copy(id = id)
        }
        // Les catégories et dossiers se retrouvent dans leur parent.
        return listOf(BrowseIds.TAB_HOME, BrowseIds.TAB_LIBRARY, BrowseIds.TAB_PLAYLISTS, BrowseIds.TAB_RADIOS,
            BrowseIds.CAT_ALBUMS, BrowseIds.CAT_ARTISTS, BrowseIds.CAT_GENRES, BrowseIds.CAT_AUDIOBOOKS)
            .asSequence()
            .flatMap { children(it).asSequence() }
            .firstOrNull { it.id == id }
    }

    /**
     * File à lancer pour un élément jouable choisi dans l'arborescence.
     *
     * Un titre en contexte joue la liste de son parent à partir de lui ; une lecture
     * aléatoire mélange cette liste. Null pour un identifiant inconnu.
     */
    fun queueFor(id: String): BrowseQueue? {
        if (id.startsWith(BrowseIds.SHUFFLE_PREFIX)) {
            val songs = songsOf(id.removePrefix(BrowseIds.SHUFFLE_PREFIX))?.takeIf { it.isNotEmpty() } ?: return null
            return BrowseQueue(shuffle(songs), 0)
        }
        val (parent, songId) = BrowseIds.parseSongInContext(id) ?: return null
        if (parent == BrowseIds.CAT_CONTINUE) return continueListening().firstOrNull { it.song.id == songId }?.queue
        val songs = songsOf(parent) ?: return null
        val index = songs.indexOfFirst { it.id == songId }
        return if (index >= 0) BrowseQueue(songs, index) else null
    }

    fun radioFor(id: String): RadioStation? =
        id.removePrefix(BrowseIds.RADIO_PREFIX).takeIf { id.startsWith(BrowseIds.RADIO_PREFIX) }
            ?.let { uuid -> source.radios.firstOrNull { it.stationUuid == uuid } }

    /** Résultats de la recherche tapée ou dictée sur l'écran de la voiture, par sections. */
    fun search(query: String): List<BrowseNode> {
        val q = query.trim()
        if (q.isEmpty() || !source.hasPermission) return emptyList()
        val results = source.search(q, SEARCH_LIMIT)
        val parent = BrowseIds.SEARCH_PREFIX + q
        val artists = results.artists.take(5).map { artistNode(it).copy(groupTitle = "Artistes") }
        val albums = results.albums.take(8).map { albumNode(it).copy(groupTitle = "Albums") }
        val playlists = matchingPlaylists(q).take(5).map { playlistNode(it).copy(groupTitle = "Playlists") }
        val radios = source.radios.filter { fuzzyScore(it.name, q) >= MATCH_SCORE }.take(5)
            .map { radioNode(it).copy(groupTitle = "Radios") }
        val songs = results.songs.take(SEARCH_LIMIT).map { songNode(parent, it).copy(groupTitle = "Titres") }
        return songs.take(3) + artists + albums + playlists + radios + songs.drop(3)
    }

    /**
     * Résout « Joue … » dicté à l'Assistant.
     *
     * Le type reconnu par l'Assistant passe en premier ; à défaut on essaie les mots-clés
     * (favoris, aléatoire), puis artiste, album, playlist, genre, et enfin les titres.
     * Une demande vide (« Joue de la musique ») mélange toute la bibliothèque.
     */
    fun resolveVoice(request: VoiceRequest): BrowseQueue? {
        if (!source.hasPermission || source.songs.isEmpty()) return null
        val query = request.query.trim()

        when (request.focus) {
            VoiceRequest.FOCUS_ARTIST -> bestArtist(request.artist ?: query)?.let { return BrowseQueue(it.songs) }
            VoiceRequest.FOCUS_ALBUM -> bestAlbum(request.album ?: query, request.artist)?.let { return BrowseQueue(it.songs) }
            VoiceRequest.FOCUS_GENRE -> bestGenre(request.genre ?: query)?.let { return BrowseQueue(shuffle(it.songs)) }
            VoiceRequest.FOCUS_PLAYLIST -> bestPlaylist(request.playlist ?: query)?.let { return BrowseQueue(it.songs) }
            VoiceRequest.FOCUS_TITLE -> bestSong(request.title ?: query, request.artist)?.let { return songWithAlbum(it) }
        }

        if (query.isEmpty()) return BrowseQueue(shuffle(source.songs))
        val normalized = query.normalizeForSearch()
        if (normalized in FAVORITE_WORDS && source.favorites.isNotEmpty()) return BrowseQueue(source.favorites)
        if (normalized in SHUFFLE_WORDS) return BrowseQueue(shuffle(source.songs))

        bestArtist(query)?.let { return BrowseQueue(it.songs) }
        bestAlbum(query, null)?.let { return BrowseQueue(it.songs) }
        bestPlaylist(query)?.let { return BrowseQueue(it.songs) }
        bestGenre(query)?.let { return BrowseQueue(shuffle(it.songs)) }

        val results = source.search(query, 50)
        if (results.songs.isNotEmpty()) return BrowseQueue(results.songs)
        return results.albums.flatMap { it.songs }.takeIf { it.isNotEmpty() }?.let { BrowseQueue(it) }
    }

    // --- Onglets --------------------------------------------------------------------

    private fun home(): List<BrowseNode> = buildList {
        // En voiture, reprendre un livre ou un podcast entamé passe avant tout le reste.
        val resumable = continueListening()
        if (resumable.isNotEmpty()) {
            add(BrowseNode(BrowseIds.CAT_CONTINUE, "Continuer l'écoute", resumable.first().node.title,
                NodeKind.FOLDER, NodeArt.Icon(BrowseIcon.RESUME), playableStyle = ChildStyle.LIST))
        }
        if (source.songs.isNotEmpty()) {
            add(BrowseNode(BrowseIds.SHUFFLE_ALL, "Lecture aléatoire", titles(source.songs.size),
                NodeKind.ACTION, NodeArt.Icon(BrowseIcon.SHUFFLE)))
        }
        category(BrowseIds.CAT_FAVORITES, "Favoris", source.favorites, BrowseIcon.FAVORITES)
        category(BrowseIds.CAT_RECENTLY_PLAYED, "Écoutés récemment", source.recentlyPlayed, BrowseIcon.HISTORY)
        category(BrowseIds.CAT_MOST_PLAYED, "Les plus écoutés", source.mostPlayed, BrowseIcon.MOST_PLAYED)
        category(BrowseIds.CAT_RECENT, "Ajoutés récemment", source.recentlyAdded, BrowseIcon.NEW)
    }

    private fun MutableList<BrowseNode>.category(id: String, title: String, songs: List<Song>, icon: BrowseIcon) {
        if (songs.isEmpty()) return
        add(BrowseNode(id, title, titles(songs.size), NodeKind.FOLDER, NodeArt.Icon(icon), playableStyle = ChildStyle.LIST))
    }

    private fun library(): List<BrowseNode> = buildList {
        if (source.songs.isNotEmpty()) addAll(musicCategories())
        val books = source.audiobooks.groupBy { it.folderPath }
        if (books.isNotEmpty()) {
            add(BrowseNode(BrowseIds.CAT_AUDIOBOOKS, "Livres audio", plural(books.size, "livre"), NodeKind.FOLDER,
                NodeArt.Icon(BrowseIcon.BOOKS), browsableStyle = ChildStyle.LIST))
        }
        if (source.podcasts.isNotEmpty()) {
            add(BrowseNode(BrowseIds.CAT_PODCASTS, "Podcasts", plural(source.podcasts.size, "épisode"), NodeKind.FOLDER,
                NodeArt.Icon(BrowseIcon.PODCASTS), playableStyle = ChildStyle.LIST))
        }
    }

    private fun musicCategories(): List<BrowseNode> {
        return listOf(
            BrowseNode(BrowseIds.CAT_ALBUMS, "Albums", plural(source.albums.size, "album"), NodeKind.FOLDER,
                NodeArt.Icon(BrowseIcon.ALBUMS), browsableStyle = ChildStyle.GRID),
            BrowseNode(BrowseIds.CAT_ARTISTS, "Artistes", plural(source.artists.size, "artiste"), NodeKind.FOLDER,
                NodeArt.Icon(BrowseIcon.ARTISTS), browsableStyle = ChildStyle.GRID),
            BrowseNode(BrowseIds.CAT_GENRES, "Genres", plural(source.genres.size, "genre"), NodeKind.FOLDER,
                NodeArt.Icon(BrowseIcon.GENRES), browsableStyle = ChildStyle.LIST),
            BrowseNode(BrowseIds.CAT_SONGS, "Tous les titres", titles(source.songs.size), NodeKind.FOLDER,
                NodeArt.Icon(BrowseIcon.SONGS), playableStyle = ChildStyle.LIST),
        )
    }

    private fun playlists(): List<BrowseNode> =
        source.playlists.filter { it.songs.isNotEmpty() }.map(::playlistNode)

    private fun radios(): List<BrowseNode> = source.radios.map(::radioNode)
        .ifEmpty { listOf(info("radios", "Aucune radio favorite", "Ajoute des radios en favori dans Synxio")) }

    // --- Catégories -----------------------------------------------------------------

    private fun albums(albums: List<Album>): List<BrowseNode> = albums.map(::albumNode)

    private fun artists(): List<BrowseNode> = source.artists.sortedBy { it.name.lowercase() }.map(::artistNode)

    private fun genres(): List<BrowseNode> = source.genres.sortedBy { it.name.lowercase() }.map { genre ->
        BrowseNode(BrowseIds.GENRE_PREFIX + genre.name, genre.name, titles(genre.songCount), NodeKind.GENRE,
            genre.songs.firstOrNull()?.let(NodeArt::OfSong), playableStyle = ChildStyle.LIST)
    }

    /** Page d'artiste : lecture aléatoire, ses albums s'il en a plusieurs, puis ses titres. */
    private fun artistDetail(parentId: String): List<BrowseNode> {
        val artist = source.artists.firstOrNull { it.name == parentId.removePrefix(BrowseIds.ARTIST_PREFIX) }
            ?: return emptyList()
        val songs = artist.songs
        return buildList {
            addAll(withShuffle(parentId, songs))
            if (artist.albums.size > 1) {
                addAll(artist.albums.sortedByDescending { it.year }.map { albumNode(it).copy(groupTitle = "Albums") })
            }
            addAll(songNodes(parentId, songs).map { it.copy(groupTitle = "Titres") })
        }
    }

    /** Titres d'un parent jouable, dans l'ordre de la file qu'il lancera. Null si inconnu. */
    private fun songsOf(parentId: String): List<Song>? = when {
        parentId == BrowseIds.CAT_SONGS -> source.songs.sortedBy { it.title.lowercase() }
        parentId == BrowseIds.CAT_FAVORITES -> source.favorites
        parentId == BrowseIds.CAT_RECENT -> source.recentlyAdded
        parentId == BrowseIds.CAT_RECENTLY_PLAYED -> source.recentlyPlayed.take(HISTORY_LIMIT)
        parentId == BrowseIds.CAT_MOST_PLAYED -> source.mostPlayed.take(HISTORY_LIMIT)
        parentId.startsWith(BrowseIds.ALBUM_PREFIX) ->
            parentId.removePrefix(BrowseIds.ALBUM_PREFIX).toLongOrNull()
                ?.let { id -> source.albums.firstOrNull { it.id == id } }
                ?.songs?.sortedWith(compareBy({ it.disc }, { it.track }))
        parentId.startsWith(BrowseIds.ARTIST_PREFIX) ->
            source.artists.firstOrNull { it.name == parentId.removePrefix(BrowseIds.ARTIST_PREFIX) }?.songs
        parentId.startsWith(BrowseIds.GENRE_PREFIX) ->
            source.genres.firstOrNull { it.name == parentId.removePrefix(BrowseIds.GENRE_PREFIX) }?.songs
        parentId.startsWith(BrowseIds.PLAYLIST_PREFIX) ->
            parentId.removePrefix(BrowseIds.PLAYLIST_PREFIX).toLongOrNull()
                ?.let { id -> source.playlists.firstOrNull { it.id == id } }?.songs
        parentId.startsWith(BrowseIds.SEARCH_PREFIX) ->
            source.search(parentId.removePrefix(BrowseIds.SEARCH_PREFIX), SEARCH_LIMIT).songs
        parentId == BrowseIds.CAT_PODCASTS -> source.podcasts
        parentId == BrowseIds.CAT_CONTINUE -> continueListening().map { it.song }
        parentId.startsWith(BrowseIds.BOOK_PREFIX) -> parentId.removePrefix(BrowseIds.BOOK_PREFIX).let { path ->
            source.audiobooks.filter { it.folderPath == path }.inBookOrder().takeIf { it.isNotEmpty() }
        }
        else -> null
    }

    private fun withShuffle(parentId: String, songs: List<Song>): List<BrowseNode> =
        if (songs.size < 3) emptyList()
        else listOf(BrowseNode(BrowseIds.SHUFFLE_PREFIX + parentId, "Lecture aléatoire", titles(songs.size),
            NodeKind.ACTION, NodeArt.Icon(BrowseIcon.SHUFFLE)))

    private fun songNodes(parentId: String, songs: List<Song>): List<BrowseNode> = songs.map { songNode(parentId, it) }

    // --- Livres audio et podcasts ----------------------------------------------------

    /** Élément de « Continuer l'écoute » : son nœud, et la file qui reprend au bon endroit. */
    private class Resumable(val node: BrowseNode, val song: Song, val queue: BrowseQueue, val lastPlayedSec: Long)

    /**
     * Livres entamés et pas finis, puis épisodes de podcast en cours, le plus récent en tête.
     * Chaque élément reprend le fichier en cours à sa position enregistrée.
     */
    private fun continueListening(): List<Resumable> {
        val saved = source.savedPositions
        val books = source.audiobooks.groupBy { it.folderPath }.mapNotNull { (path, songs) ->
            val files = songs.inBookOrder()
            val progress = BookProgress.of(files, saved) ?: return@mapNotNull null
            if (!progress.started || progress.finished) return@mapNotNull null
            val file = files[progress.fileIndex]
            val start = BookProgress.resumePosition(saved[file.id]?.positionMs ?: 0L, file.durationMs)
            val node = BrowseNode(
                BrowseIds.songInContext(BrowseIds.CAT_CONTINUE, file.id), bookName(path),
                "${progress.chapterLabel()} · ${progress.remainingLabel()}", NodeKind.SONG, NodeArt.OfSong(file),
                groupTitle = "Livres audio", song = file,
            )
            Resumable(node, file, BrowseQueue(files, progress.fileIndex, start), progress.lastPlayedSec)
        }
        val episodes = source.podcasts.mapNotNull { episode ->
            val entry = saved[episode.id] ?: return@mapNotNull null
            if (episodeState(entry, episode.durationMs) != EpisodeState.IN_PROGRESS) return@mapNotNull null
            val index = source.podcasts.indexOf(episode)
            val node = episodeNode(BrowseIds.CAT_CONTINUE, episode).copy(groupTitle = "Podcasts")
            Resumable(node, episode, BrowseQueue(source.podcasts, index, entry.positionMs), entry.lastPlayedSec)
        }
        return books.sortedByDescending { it.lastPlayedSec } + episodes.sortedByDescending { it.lastPlayedSec }
    }

    private fun books(): List<BrowseNode> = source.audiobooks.groupBy { it.folderPath }
        .map { (path, songs) ->
            val files = songs.inBookOrder()
            val progress = BookProgress.of(files, source.savedPositions)
            BrowseNode(
                BrowseIds.BOOK_PREFIX + path, bookName(path),
                progress?.statusLabel() ?: "${plural(files.size, "chapitre")} · ${files.sumOf { it.durationMs }.asLongDuration()}",
                NodeKind.ALBUM, files.firstOrNull()?.let(NodeArt::OfSong), playableStyle = ChildStyle.LIST,
            )
        }
        .sortedBy { it.title.lowercase() }

    /** Épisode : son émission, et où en est l'écoute s'il est entamé ou fini. */
    private fun episodeNode(parentId: String, episode: Song): BrowseNode {
        val entry = source.savedPositions[episode.id]
        val state = when (episodeState(entry, episode.durationMs)) {
            EpisodeState.PLAYED -> "Lu"
            EpisodeState.IN_PROGRESS -> "encore " +
                (episode.durationMs - (entry?.positionMs ?: 0L)).coerceAtLeast(0L).asLongDuration()
            EpisodeState.NEW -> null
        }
        val show = episode.album.ifBlank { episode.displayArtist }.cleanTitle()
        return BrowseNode(BrowseIds.songInContext(parentId, episode.id), episode.title.cleanTitle(),
            listOfNotNull(show, state).joinToString(" · "), NodeKind.SONG, NodeArt.OfSong(episode), song = episode)
    }

    private fun bookName(path: String) = path.substringAfterLast('/', path)

    // --- Nœuds ----------------------------------------------------------------------

    private fun songNode(parentId: String, song: Song): BrowseNode {
        // Dans un album, l'album est déjà à l'écran : l'artiste suffit.
        val subtitle = if (parentId.startsWith(BrowseIds.ALBUM_PREFIX)) song.displayArtist.cleanTitle()
        else "${song.displayArtist.cleanTitle()} · ${song.displayAlbum}"
        return BrowseNode(BrowseIds.songInContext(parentId, song.id), song.title.cleanTitle(), subtitle,
            NodeKind.SONG, NodeArt.OfSong(song), song = song)
    }

    private fun albumNode(album: Album) = BrowseNode(
        BrowseIds.ALBUM_PREFIX + album.id, album.title, album.artist, NodeKind.ALBUM,
        NodeArt.OfAlbum(album), playableStyle = ChildStyle.LIST,
    )

    private fun artistNode(artist: Artist) = BrowseNode(
        BrowseIds.ARTIST_PREFIX + artist.name, artist.name,
        if (artist.albumCount > 1) "${plural(artist.albumCount, "album")} · ${titles(artist.songCount)}" else titles(artist.songCount),
        NodeKind.ARTIST, NodeArt.OfArtist(artist), browsableStyle = ChildStyle.GRID, playableStyle = ChildStyle.LIST,
    )

    private fun playlistNode(playlist: Playlist) = BrowseNode(
        BrowseIds.PLAYLIST_PREFIX + playlist.id, playlist.name, titles(playlist.songCount), NodeKind.PLAYLIST,
        playlist.songs.firstOrNull()?.let(NodeArt::OfSong) ?: NodeArt.Icon(BrowseIcon.PLAYLISTS),
        playableStyle = ChildStyle.LIST,
    )

    private fun radioNode(station: RadioStation) = BrowseNode(
        BrowseIds.RADIO_PREFIX + station.stationUuid, station.name,
        station.tags.split(',').firstOrNull { it.isNotBlank() }?.trim()?.replaceFirstChar { it.uppercase() }
            ?: station.country.ifBlank { "Webradio" },
        NodeKind.RADIO, NodeArt.OfRadio(station), station = station,
    )

    private fun permissionNeeded() = info(
        "permission", "Autorise l'accès à ta musique", "Ouvre Synxio sur ton téléphone pour donner l'accès",
    )

    private fun emptyFor(parentId: String): BrowseNode = when {
        !source.hasScanned -> info("loading", "Bibliothèque en cours de chargement", "Encore un instant…")
        parentId == BrowseIds.TAB_PLAYLISTS -> info("playlists", "Aucune playlist", "Crée une playlist dans Synxio pour la retrouver ici")
        parentId == BrowseIds.CAT_FAVORITES -> info("favorites", "Aucun favori", "Touche le cœur d'un titre pour l'ajouter")
        source.songs.isEmpty() -> info("empty", "Aucun titre trouvé", "Ajoute de la musique sur ton téléphone")
        else -> info("nothing", "Rien à afficher ici", null)
    }

    private fun info(key: String, title: String, subtitle: String?) =
        BrowseNode(BrowseIds.INFO_PREFIX + key, title, subtitle, NodeKind.INFO, NodeArt.Icon(BrowseIcon.INFO))

    // --- Recherche ------------------------------------------------------------------

    private fun bestArtist(name: String): Artist? = best(source.artists, name) { it.name }
    private fun bestGenre(name: String): Genre? = best(source.genres, name) { it.name }
    private fun bestPlaylist(name: String): Playlist? = best(source.playlists.filter { it.songs.isNotEmpty() }, name) { it.name }

    private fun bestAlbum(title: String, artist: String?): Album? {
        val candidates = if (artist.isNullOrBlank()) source.albums
        else source.albums.filter { fuzzyScore(it.artist, artist) >= MATCH_SCORE }.ifEmpty { source.albums }
        return best(candidates, title) { it.title }
    }

    private fun bestSong(title: String, artist: String?): Song? {
        val candidates = if (artist.isNullOrBlank()) source.songs
        else source.songs.filter { fuzzyScore(it.displayArtist, artist) >= MATCH_SCORE }.ifEmpty { source.songs }
        return best(candidates, title) { it.title.cleanTitle() }
    }

    /** Un titre précis : on le joue dans son album, pour que la suite ait du sens. */
    private fun songWithAlbum(song: Song): BrowseQueue {
        // L'album qui contient le titre : l'id MediaStore d'album peut regrouper des homonymes.
        val album = source.albums.firstOrNull { album -> album.songs.any { it.id == song.id } }
            ?.songs?.sortedWith(compareBy({ it.disc }, { it.track }))
        val index = album?.indexOfFirst { it.id == song.id } ?: -1
        return if (album != null && index >= 0) BrowseQueue(album, index) else BrowseQueue(listOf(song))
    }

    private fun matchingPlaylists(query: String): List<Playlist> =
        source.playlists.filter { it.songs.isNotEmpty() && fuzzyScore(it.name, query) >= MATCH_SCORE }

    private inline fun <T> best(items: List<T>, query: String, name: (T) -> String): T? {
        if (query.isBlank()) return null
        var bestItem: T? = null
        var bestScore = 0
        for (item in items) {
            val score = fuzzyScore(name(item), query)
            if (score > bestScore) {
                bestScore = score
                bestItem = item
            }
        }
        return bestItem.takeIf { bestScore >= MATCH_SCORE }
    }

    companion object {
        /** Seuil de [fuzzyScore] au-delà duquel un nom correspond à la demande. */
        const val MATCH_SCORE = 400
        private const val SEARCH_LIMIT = 30
        private const val HISTORY_LIMIT = 50

        private val FAVORITE_WORDS = setOf("favoris", "mes favoris", "mes titres favoris", "mes coups de coeur", "coups de coeur")
        private val SHUFFLE_WORDS = setOf("aleatoire", "lecture aleatoire", "au hasard", "shuffle", "de la musique", "musique")

        fun titles(count: Int): String = plural(count, "titre")

        fun plural(count: Int, word: String): String = if (count <= 1) "$count $word" else "$count ${word}s"
    }
}
