package fr.synxio.player.data.repo

import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.core.util.fuzzyScore
import fr.synxio.player.data.db.ExcludedFolderDao
import fr.synxio.player.data.db.ExcludedFolderEntity
import fr.synxio.player.data.db.AudiobookFolderDao
import fr.synxio.player.data.db.AudiobookFolderEntity
import fr.synxio.player.data.db.FavoriteDao
import fr.synxio.player.data.db.FavoriteEntity
import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.media.MediaStoreScanner
import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.Folder
import fr.synxio.player.data.model.Genre
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.data.model.splitArtistTag
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Bibliothèque complète en mémoire, dérivée d'un seul scan MediaStore. */
data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val podcasts: List<Song> = emptyList(),
    val audiobooks: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val hasScanned: Boolean = false,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && podcasts.isEmpty() && audiobooks.isEmpty()
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs } + podcasts.sumOf { it.durationMs } + audiobooks.sumOf { it.durationMs }
}

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && genres.isEmpty()
}

@Singleton
class MusicRepository @Inject constructor(
    private val scanner: MediaStoreScanner,
    private val settingsRepository: SettingsRepository,
    private val favoriteDao: FavoriteDao,
    private val playStatDao: PlayStatDao,
    private val excludedFolderDao: ExcludedFolderDao,
    private val audiobookFolderDao: AudiobookFolderDao,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    /** Index id -> morceau, pour résoudre une file d'attente ou une playlist en O(1). */
    private val _songsById = MutableStateFlow<Map<Long, Song>>(emptyMap())
    val songsById: StateFlow<Map<Long, Song>> = _songsById.asStateFlow()

    private var permissionGranted = false

    init {
        // Le service de lecture peut démarrer sans passer par l'UI (Bluetooth, Auto) :
        // si la permission est déjà accordée, on scanne sans attendre l'activité.
        if (scanner.hasAudioPermission()) onPermissionGranted()

        // MediaStore bouge (import, suppression, édition de tag) : on resynchronise.
        scanner.observeChanges()
            .debounce(1_500)
            .onEach { if (permissionGranted) refresh() }
            .launchIn(scope)
    }

    fun onPermissionGranted() {
        if (permissionGranted) return
        permissionGranted = true
        scope.launch { refresh() }
    }

    val hasPermission: Boolean get() = permissionGranted

    suspend fun refresh() {
        _library.value = _library.value.copy(isLoading = true)
        val library = withContext(Dispatchers.IO) {
            val minDurationSec = settingsRepository.settings.first().minDurationSec
            val excluded = excludedFolderDao.paths().toSet()
            val audiobooks = audiobookFolderDao.paths().toSet()
            val songs = scanner.scan(minDurationSec * 1000L, excluded, audiobooks)
            buildLibrary(songs)
        }
        _songsById.value = library.songs.associateBy { it.id }
        _library.value = library
        settingsRepository.setLastScan(System.currentTimeMillis())
    }

    private fun buildLibrary(allSongs: List<Song>): Library {
        val podcasts = allSongs.filter { it.isPodcast }.sortedBy { it.title.lowercase() }
        val audiobooks = allSongs.filter { it.isAudiobook }.sortedBy { it.title.lowercase() }
        val musicSongs = allSongs.filter { !it.isPodcast && !it.isAudiobook }

        val albums = musicSongs
            .groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val head = albumSongs.first()
                Album(
                    id = albumId,
                    title = head.displayAlbum,
                    artist = head.albumArtist?.takeIf { it.isNotBlank() } ?: head.displayArtist,
                    artistId = head.artistId,
                    year = albumSongs.maxOf { it.year },
                    // Ordre naturel d'écoute d'un album : disque puis piste.
                    songs = albumSongs.sortedWith(
                        compareBy({ it.disc }, { it.track }, { it.title.lowercase() })
                    ),
                )
            }

        // Un morceau peut compter pour plusieurs artistes : on indexe chaque nom
        // séparément plutôt que de créer un artiste « A/B/C » illisible.
        val albumsByArtistKey = HashMap<String, MutableSet<Album>>()
        val displayNameByKey = HashMap<String, String>()
        val artistIdByKey = HashMap<String, Long>()

        albums.forEach { album ->
            val names = album.songs
                .flatMap { song ->
                    splitArtistTag(song.albumArtist?.takeIf { it.isNotBlank() } ?: song.displayArtist)
                }
                .ifEmpty { listOf(album.artist) }

            names.distinctBy { it.lowercase() }.forEach { name ->
                val key = name.lowercase()
                albumsByArtistKey.getOrPut(key) { linkedSetOf() } += album
                displayNameByKey.putIfAbsent(key, name)
                artistIdByKey.putIfAbsent(key, album.artistId)
            }
        }

        val artists = albumsByArtistKey.map { (key, artistAlbums) ->
            Artist(
                id = artistIdByKey[key] ?: 0L,
                name = displayNameByKey[key] ?: key,
                albums = artistAlbums.sortedByDescending { it.year },
            )
        }

        val genres = musicSongs
            .groupBy { it.genre?.takeIf { g -> g.isNotBlank() } ?: Song.UNKNOWN_GENRE }
            .map { (name, genreSongs) -> Genre(name, genreSongs) }
            .sortedBy { it.name.lowercase() }

        val folders = musicSongs
            .groupBy { it.folderPath }
            .map { (path, folderSongs) -> Folder(path, folderSongs) }
            .sortedBy { it.path.lowercase() }
            
        return Library(
            songs = musicSongs,
            albums = albums,
            artists = artists,
            genres = genres,
            folders = folders,
            podcasts = podcasts,
            audiobooks = audiobooks,
            isLoading = false,
            hasScanned = true,
        )
    }

    // --- Résolution ------------------------------------------------------------------

    fun songById(id: Long): Song? = _songsById.value[id]

    /**
     * Résout un morceau même quand l'index en mémoire est vide.
     *
     * [songById] lit un index peuplé par le scan MediaStore, qui n'existe qu'une fois
     * l'application démarrée et la bibliothèque chargée. Le widget, lui, peut être
     * redessiné avant — il affichait alors « Aucune lecture » alors qu'un morceau était
     * bien en file, et le restait jusqu'au changement de piste suivant.
     *
     * Le repli relit MediaStore. C'est coûteux pour un seul morceau, mais il ne se
     * déclenche que dans cette fenêtre étroite, et réutiliser le scanner évite de
     * maintenir une seconde requête en parallèle.
     */
    suspend fun resolveSong(id: Long): Song? {
        songById(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val minDurationSec = settingsRepository.settings.first().minDurationSec
            scanner.scan(minDurationSec * 1000L, excludedFolderDao.paths().toSet())
                .firstOrNull { it.id == id }
        }
    }

    fun songsByIds(ids: List<Long>): List<Song> {
        val index = _songsById.value
        return ids.mapNotNull { index[it] }
    }

    fun albumById(id: Long): Album? = _library.value.albums.firstOrNull { it.id == id }

    fun artistByName(name: String): Artist? =
        _library.value.artists.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun genreByName(name: String): Genre? =
        _library.value.genres.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun folderByPath(path: String): Folder? {
        val lib = _library.value
        val musicFolder = lib.folders.firstOrNull { it.path == path }
        if (musicFolder != null) return musicFolder

        val audiobookSongs = lib.audiobooks.filter { it.folderPath == path }
        if (audiobookSongs.isNotEmpty()) {
            return Folder(path, audiobookSongs.sortedBy { it.track })
        }
        return null
    }

    // --- Favoris ---------------------------------------------------------------------

    val favoriteIds: StateFlow<Set<Long>> = favoriteDao.observeAll()
        .map { list -> list.map { it.songId }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val favoriteSongs: StateFlow<List<Song>> =
        combine(favoriteDao.observeAll(), _songsById) { favorites, index ->
            favorites.mapNotNull { index[it.songId] }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun toggleFavorite(song: Song): Boolean {
        val isFavorite = favoriteDao.isFavorite(song.id)
        if (isFavorite) {
            favoriteDao.remove(song.id)
        } else {
            favoriteDao.add(FavoriteEntity(song.id, song.path, System.currentTimeMillis()))
        }
        return !isFavorite
    }

    // --- Statistiques d'écoute -------------------------------------------------------

    val mostPlayed: StateFlow<List<Song>> =
        combine(playStatDao.observeMostPlayed(100), _songsById) { stats, index ->
            stats.mapNotNull { index[it.songId] }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val recentlyPlayed: StateFlow<List<Song>> =
        combine(playStatDao.observeRecentlyPlayed(100), _songsById) { stats, index ->
            stats.mapNotNull { index[it.songId] }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val allStats: StateFlow<Map<Long, PlayStatEntity>> = playStatDao.observeAll()
        .map { list -> list.associateBy { it.songId } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Les 25 derniers ajouts, base de la section « Nouveautés » de l'accueil. */
    val recentlyAdded: StateFlow<List<Song>> = _library
        .map { lib -> lib.songs.sortedByDescending { it.dateAddedSec }.take(25) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Retire les titres que l'on coupe systématiquement.
     *
     * Le critère est le même que celui de la sélection « Souvent zappés » : au moins
     * deux coupures, et plus de coupures que d'écoutes complètes. Un morceau zappé une
     * fois par hasard n'est pas écarté.
     *
     * La liste filtrée n'est retenue que si elle garde de quoi écouter : mieux vaut une
     * lecture aléatoire imparfaite qu'une file quasi vide sur une petite bibliothèque.
     */
    fun withoutDisliked(songs: List<Song>): List<Song> {
        val stats = allStats.value
        val kept = songs.filter { song ->
            val stat = stats[song.id] ?: return@filter true
            !(stat.skipCount >= 2 && stat.skipCount > stat.playCount)
        }
        return if (kept.size >= MIN_SHUFFLE_POOL) kept else songs
    }

    suspend fun registerPlay(song: Song, listenedMs: Long) =
        playStatDao.registerPlay(song.id, song.path, listenedMs, System.currentTimeMillis())

    suspend fun registerSkip(song: Song, listenedMs: Long) =
        playStatDao.registerSkip(song.id, song.path, listenedMs)

    suspend fun clearStats() = playStatDao.clear()

    // --- Dossiers exclus -------------------------------------------------------------

    val excludedFolders = excludedFolderDao.observeAll().map { list -> list.map { it.path } }

    suspend fun addExcludedFolder(path: String) {
        excludedFolderDao.add(ExcludedFolderEntity(path))
        refresh()
    }

    suspend fun removeExcludedFolder(path: String) {
        excludedFolderDao.remove(path)
        refresh()
    }

    // --- Dossiers de Livres Audio ----------------------------------------------------

    val audiobookFolders = audiobookFolderDao.observeAll().map { list -> list.map { it.path } }

    suspend fun addAudiobookFolder(path: String) {
        audiobookFolderDao.add(AudiobookFolderEntity(path))
        refresh()
    }

    suspend fun removeAudiobookFolder(path: String) {
        audiobookFolderDao.remove(path)
        refresh()
    }

    // --- Recherche -------------------------------------------------------------------

    fun search(query: String, limitPerSection: Int = 20): SearchResults {
        if (query.isBlank()) return SearchResults()
        val lib = _library.value

        fun <T> rank(items: List<T>, key: (T) -> String, extra: (T) -> String = { "" }): List<T> =
            items.asSequence()
                .map { it to maxOf(fuzzyScore(key(it), query), fuzzyScore(extra(it), query) - 100) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limitPerSection)
                .map { it.first }
                .toList()

        return SearchResults(
            songs = rank(lib.songs, { it.title }, { "${it.displayArtist} ${it.displayAlbum}" }),
            albums = rank(lib.albums, { it.title }, { it.artist }),
            artists = rank(lib.artists, { it.name }),
            genres = rank(lib.genres, { it.name }),
        )
    }

    // --- Tri -------------------------------------------------------------------------

    fun sortSongs(songs: List<Song>, sort: SongSort, descending: Boolean): List<Song> {
        val stats = allStats.value
        val comparator: Comparator<Song> = when (sort) {
            SongSort.TITLE -> compareBy { it.title.lowercase() }
            SongSort.ARTIST -> compareBy({ it.displayArtist.lowercase() }, { it.title.lowercase() })
            SongSort.ALBUM -> compareBy({ it.displayAlbum.lowercase() }, { it.disc }, { it.track })
            SongSort.DURATION -> compareBy { it.durationMs }
            SongSort.DATE_ADDED -> compareBy { it.dateAddedSec }
            SongSort.DATE_MODIFIED -> compareBy { it.dateModifiedSec }
            SongSort.YEAR -> compareBy { it.year }
            SongSort.TRACK -> compareBy({ it.disc }, { it.track })
            SongSort.SIZE -> compareBy { it.sizeBytes }
            SongSort.PLAY_COUNT -> compareBy { stats[it.id]?.playCount ?: 0 }
        }
        return songs.sortedWith(if (descending) comparator.reversed() else comparator)
    }

    fun sortAlbums(albums: List<Album>, sort: AlbumSort, descending: Boolean): List<Album> {
        val comparator: Comparator<Album> = when (sort) {
            AlbumSort.TITLE -> compareBy { it.title.lowercase() }
            AlbumSort.ARTIST -> compareBy({ it.artist.lowercase() }, { it.year })
            AlbumSort.YEAR -> compareBy { it.year }
            AlbumSort.SONG_COUNT -> compareBy { it.songCount }
            AlbumSort.DATE_ADDED -> compareBy { it.dateAddedSec }
        }
        return albums.sortedWith(if (descending) comparator.reversed() else comparator)
    }

    fun sortArtists(artists: List<Artist>, sort: ArtistSort, descending: Boolean): List<Artist> {
        val comparator: Comparator<Artist> = when (sort) {
            ArtistSort.NAME -> compareBy { it.name.lowercase() }
            ArtistSort.ALBUM_COUNT -> compareBy { it.albumCount }
            ArtistSort.SONG_COUNT -> compareBy { it.songCount }
        }
        return artists.sortedWith(if (descending) comparator.reversed() else comparator)
    }

    private companion object {
        /** En dessous, filtrer l'aléatoire appauvrirait trop la file. */
        const val MIN_SHUFFLE_POOL = 10
    }
}
