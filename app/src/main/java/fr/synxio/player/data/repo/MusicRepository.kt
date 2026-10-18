package fr.synxio.player.data.repo

import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.core.util.fuzzyScoreNormalized
import fr.synxio.player.core.util.newSortCollator
import fr.synxio.player.core.util.normalizeForSearch
import fr.synxio.player.data.db.ExcludedFolderDao
import fr.synxio.player.data.db.ExcludedFolderEntity
import fr.synxio.player.data.db.AudiobookFolderDao
import fr.synxio.player.data.db.AudiobookFolderEntity
import fr.synxio.player.data.db.FavoriteDao
import fr.synxio.player.data.db.FavoriteEntity
import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.media.DeleteOutcome
import fr.synxio.player.data.media.MediaStoreScanner
import fr.synxio.player.data.meta.EnrichProgress
import fr.synxio.player.data.meta.MetadataEnricher
import fr.synxio.player.data.meta.MetadataStats
import fr.synxio.player.data.meta.RefetchResult
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
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

    /**
     * Clés de recherche normalisées, construites une seule fois par bibliothèque.
     *
     * Hors du constructeur pour ne pas peser sur `equals`/`copy` ; [MusicRepository.refresh]
     * le force sur le thread d'E/S pour que la première frappe n'ait rien à construire.
     */
    val searchIndex: SearchIndex by lazy { SearchIndex.build(this) }
}

/**
 * Textes de la bibliothèque passés une fois par [normalizeForSearch].
 *
 * Avant, chaque frappe re-normalisait (NFD + regex) chaque titre, artiste et album :
 * plusieurs dizaines de milliers d'allocations par caractère tapé. Les tableaux sont
 * alignés sur les listes de [Library] : `songTitles[i]` est la clé de `songs[i]`.
 */
class SearchIndex private constructor(
    val songTitles: Array<String>,
    /** « artiste album », comparé avec une pénalité comme critère secondaire. */
    val songExtras: Array<String>,
    val albumTitles: Array<String>,
    val albumArtists: Array<String>,
    val artistNames: Array<String>,
    val genreNames: Array<String>,
) {
    companion object {
        fun build(library: Library): SearchIndex = SearchIndex(
            songTitles = Array(library.songs.size) { library.songs[it].title.normalizeForSearch() },
            songExtras = Array(library.songs.size) {
                val song = library.songs[it]
                "${song.displayArtist} ${song.displayAlbum}".normalizeForSearch()
            },
            albumTitles = Array(library.albums.size) { library.albums[it].title.normalizeForSearch() },
            albumArtists = Array(library.albums.size) { library.albums[it].artist.normalizeForSearch() },
            artistNames = Array(library.artists.size) { library.artists[it].name.normalizeForSearch() },
            genreNames = Array(library.genres.size) { library.genres[it].name.normalizeForSearch() },
        )
    }
}

/**
 * Classe [library] pour [query] ; cœur commun des recherches synchrone et suspendue.
 *
 * Barème identique à l'ancienne version : score du champ principal, ou score du champ
 * secondaire diminué de 100. Le tri est stable, donc à score égal l'ordre de la
 * bibliothèque est conservé. [checkpoint] est appelé régulièrement pour permettre
 * d'abandonner une recherche devenue obsolète.
 */
internal fun searchLibrary(
    library: Library,
    query: String,
    limitPerSection: Int,
    checkpoint: () -> Unit = {},
): SearchResults {
    if (query.isBlank()) return SearchResults()
    val needle = query.normalizeForSearch()
    if (needle.isEmpty()) return SearchResults()
    val index = library.searchIndex

    fun <T> rank(items: List<T>, keys: Array<String>, extras: Array<String>?): List<T> {
        val scored = ArrayList<Pair<T, Int>>()
        for (i in items.indices) {
            if (i and 0xFF == 0) checkpoint()
            val main = fuzzyScoreNormalized(keys[i], needle)
            val score = if (extras == null) main
            else maxOf(main, fuzzyScoreNormalized(extras[i], needle) - 100)
            if (score > 0) scored += items[i] to score
        }
        return scored.asSequence()
            .sortedByDescending { it.second }
            .take(limitPerSection)
            .map { it.first }
            .toList()
    }

    return SearchResults(
        songs = rank(library.songs, index.songTitles, index.songExtras),
        albums = rank(library.albums, index.albumTitles, index.albumArtists),
        artists = rank(library.artists, index.artistNames, null),
        genres = rank(library.genres, index.genreNames, null),
    )
}

/**
 * Filtre simple de la recherche avancée : titre, artiste ou album contient [query],
 * sans tenir compte des accents ni de la casse.
 */
internal fun filterLibrarySongs(
    library: Library,
    query: String,
    checkpoint: () -> Unit = {},
): List<Song> {
    if (query.isBlank()) return library.songs
    val needle = query.trim().normalizeForSearch()
    val index = library.searchIndex
    val result = ArrayList<Song>()
    for (i in library.songs.indices) {
        if (i and 0xFF == 0) checkpoint()
        if (index.songTitles[i].contains(needle) || index.songExtras[i].contains(needle)) {
            result += library.songs[i]
        }
    }
    return result
}

// --- Tri ---------------------------------------------------------------------------

/**
 * Élément accompagné de ses clés de collation.
 *
 * Les clés sont calculées une fois par élément ; un comparateur qui appellerait le
 * collator (ou `lowercase()`) à chaque comparaison referait ce travail n·log n fois.
 */
private class Keyed<T>(val item: T, val k1: java.text.CollationKey, val k2: java.text.CollationKey?)

private fun <T> List<T>.sortedByCollation(
    descending: Boolean,
    primary: (T) -> String,
    secondary: ((T) -> String)? = null,
    tieBreak: Comparator<T>? = null,
): List<T> {
    val collator = newSortCollator()
    val keyed = map { item ->
        Keyed(item, collator.getCollationKey(primary(item)), secondary?.let { collator.getCollationKey(it(item)) })
    }
    var comparator: Comparator<Keyed<T>> = Comparator { a, b -> a.k1.compareTo(b.k1) }
    if (secondary != null) comparator = comparator.thenComparator { a, b -> a.k2!!.compareTo(b.k2!!) }
    if (tieBreak != null) comparator = comparator.thenComparator { a, b -> tieBreak.compare(a.item, b.item) }
    return keyed.sortedWith(if (descending) comparator.reversed() else comparator).map { it.item }
}

/** Tri des titres, indépendant du dépôt pour pouvoir être testé sur la JVM. */
internal fun sortSongsWith(
    songs: List<Song>,
    sort: SongSort,
    descending: Boolean,
    playCount: (Song) -> Int,
): List<Song> {
    fun plain(comparator: Comparator<Song>) =
        songs.sortedWith(if (descending) comparator.reversed() else comparator)
    return when (sort) {
        SongSort.TITLE -> songs.sortedByCollation(descending, { it.title })
        SongSort.ARTIST -> songs.sortedByCollation(descending, { it.displayArtist }, { it.title })
        SongSort.ALBUM -> songs.sortedByCollation(
            descending, { it.displayAlbum }, tieBreak = compareBy({ it.disc }, { it.track }),
        )
        SongSort.DURATION -> plain(compareBy { it.durationMs })
        SongSort.DATE_ADDED -> plain(compareBy { it.dateAddedSec })
        SongSort.DATE_MODIFIED -> plain(compareBy { it.dateModifiedSec })
        SongSort.YEAR -> plain(compareBy { it.year })
        SongSort.TRACK -> plain(compareBy({ it.disc }, { it.track }))
        SongSort.SIZE -> plain(compareBy { it.sizeBytes })
        SongSort.PLAY_COUNT -> plain(compareBy(playCount))
    }
}

internal fun sortAlbumsWith(albums: List<Album>, sort: AlbumSort, descending: Boolean): List<Album> {
    fun plain(comparator: Comparator<Album>) =
        albums.sortedWith(if (descending) comparator.reversed() else comparator)
    return when (sort) {
        AlbumSort.TITLE -> albums.sortedByCollation(descending, { it.title })
        AlbumSort.ARTIST -> albums.sortedByCollation(descending, { it.artist }, tieBreak = compareBy { it.year })
        AlbumSort.YEAR -> plain(compareBy { it.year })
        AlbumSort.SONG_COUNT -> plain(compareBy { it.songCount })
        AlbumSort.DATE_ADDED -> plain(compareBy { it.dateAddedSec })
    }
}

internal fun sortArtistsWith(artists: List<Artist>, sort: ArtistSort, descending: Boolean): List<Artist> {
    return when (sort) {
        ArtistSort.NAME -> artists.sortedByCollation(descending, { it.name })
        ArtistSort.ALBUM_COUNT -> {
            val comparator = compareBy<Artist> { it.albumCount }
            artists.sortedWith(if (descending) comparator.reversed() else comparator)
        }
        // Le nombre de titres est une somme sur les albums : on le calcule une fois par artiste.
        ArtistSort.SONG_COUNT -> {
            val comparator = compareBy<Pair<Artist, Int>> { it.second }
            artists.map { it to it.songCount }
                .sortedWith(if (descending) comparator.reversed() else comparator)
                .map { it.first }
        }
    }
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
    private val enricher: MetadataEnricher,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    /** Index id -> morceau, pour résoudre une file d'attente ou une playlist en O(1). */
    private val _songsById = MutableStateFlow<Map<Long, Song>>(emptyMap())
    val songsById: StateFlow<Map<Long, Song>> = _songsById.asStateFlow()

    private var permissionGranted = false

    // Déclarés avant `init` : le premier refresh() peut partir sur un autre thread
    // avant la fin de la construction.

    /** Un seul scan à la fois : MediaStore, réglages et UI déclenchent tous des rafraîchissements. */
    private val refreshMutex = Mutex()

    /** Une demande de rafraîchissement attend d'être servie. */
    private val refreshRequested = AtomicBoolean(false)

    /** Dernier scan MediaStore brut, avant la cascade : la base des republications. */
    @Volatile private var scannedSongs: List<Song> = emptyList()

    init {
        // Le service de lecture peut démarrer sans passer par l'UI (Bluetooth, Auto) :
        // si la permission est déjà accordée, on scanne sans attendre l'activité.
        if (scanner.hasAudioPermission()) onPermissionGranted()

        // MediaStore bouge (import, suppression, édition de tag) : on resynchronise.
        scanner.observeChanges()
            .debounce(1_500)
            .onEach { if (permissionGranted) refresh() }
            .launchIn(scope)

        // La cascade de métadonnées avance par lots en arrière-plan : on republie la
        // bibliothèque à partir du dernier scan, sans relire MediaStore.
        enricher.revision
            .sample(1_200)
            .onEach { republish() }
            .launchIn(scope)
    }

    fun onPermissionGranted() {
        if (permissionGranted) return
        permissionGranted = true
        scope.launch { refresh() }
    }

    val hasPermission: Boolean get() = permissionGranted

    /**
     * Rescanne MediaStore et republie la bibliothèque.
     *
     * Les demandes concurrentes sont fusionnées : pendant un scan, N nouvelles demandes
     * donnent lieu à un seul scan supplémentaire, pas à N. Chaque appelant ne reprend la
     * main qu'une fois servi par un scan démarré après sa demande.
     */
    suspend fun refresh() {
        refreshRequested.set(true)
        refreshMutex.withLock {
            // Un scan lancé après notre demande vient de se terminer : il l'a couverte.
            if (!refreshRequested.getAndSet(false)) return
            doRefresh()
        }
    }

    private suspend fun doRefresh() {
        // Le drapeau n'est lu par l'accueil que tant que la bibliothèque est vide :
        // republier une bibliothèque pleine pour lui ferait tout recalculer en aval.
        val current = _library.value
        if (current.isEmpty && !current.isLoading) _library.value = current.copy(isLoading = true)
        val library = withContext(Dispatchers.IO) {
            val minDurationSec = settingsRepository.settings.first().minDurationSec
            val excluded = excludedFolderDao.paths().toSet()
            val audiobooks = audiobookFolderDao.paths().toSet()
            val songs = scanner.scan(minDurationSec * 1000L, excluded, audiobooks)
            enricher.prepare()
            scannedSongs = songs
            buildLibrary(enricher.overlay(songs)).also { it.searchIndex }
        }
        publish(library)
        settingsRepository.setLastScan(System.currentTimeMillis())
        enricher.onLibraryScanned(scannedSongs)
    }

    /** Réapplique la cascade au dernier scan, quand elle a progressé. */
    private suspend fun republish() {
        if (scannedSongs.isEmpty()) return
        refreshMutex.withLock {
            val songs = scannedSongs
            if (songs.isEmpty()) return
            val library = withContext(Dispatchers.Default) {
                buildLibrary(enricher.overlay(songs)).also { it.searchIndex }
            }
            publish(library)
        }
    }

    private fun publish(library: Library) {
        _songsById.value = library.songs.associateBy { it.id }
        _library.value = library
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
                    artworkOverride = albumSongs.firstNotNullOfOrNull { it.artworkOverride },
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
        findInMemory(id)?.let { return it }
        // Le scan a eu lieu et ne contient pas ce morceau : le relire avec les mêmes
        // filtres ne le trouverait pas davantage.
        if (_library.value.hasScanned) return null
        // Démarrage à froid avec permission : le scan est déjà en route, on l'attend
        // plutôt que d'en lancer un second en parallèle.
        if (permissionGranted) {
            withTimeoutOrNull(RESOLVE_SCAN_WAIT_MS) { _library.first { it.hasScanned } }
            if (_library.value.hasScanned) return findInMemory(id)
        }
        return withContext(Dispatchers.IO) {
            val minDurationSec = settingsRepository.settings.first().minDurationSec
            enricher.prepare()
            scanner.scan(minDurationSec * 1000L, excludedFolderDao.paths().toSet())
                .firstOrNull { it.id == id }
                ?.let(enricher::overlay)
        }
    }

    /** Index des titres, puis podcasts et livres audio, qui n'y figurent pas. */
    private fun findInMemory(id: Long): Song? {
        songById(id)?.let { return it }
        val lib = _library.value
        return lib.podcasts.firstOrNull { it.id == id } ?: lib.audiobooks.firstOrNull { it.id == id }
    }

    fun deleteSongs(songs: List<Song>): DeleteOutcome = scanner.deleteSongs(songs)

    // --- Métadonnées -----------------------------------------------------------------

    val metadataProgress: StateFlow<EnrichProgress> get() = enricher.progress
    val metadataStats: StateFlow<MetadataStats?> get() = enricher.stats

    /**
     * Relance la cascade pour ces morceaux, cache ignoré.
     *
     * Les morceaux affichés portent déjà les valeurs de la cascade : on repart des
     * versions MediaStore brutes, sinon le résultat précédent serait pris pour un tag.
     */
    suspend fun refetchMetadata(songs: List<Song>, label: String): RefetchResult {
        val byPath = scannedSongs.associateBy { it.path }
        val raw = songs.mapNotNull { byPath[it.path] }
        if (raw.isEmpty()) return RefetchResult(0, 0, 0, 0, onlineSkipped = true)
        return enricher.refetch(raw, label)
    }

    fun reanalyseMetadata() {
        enricher.reanalyseAll()
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

    /**
     * Toutes les statistiques indexées par morceau.
     *
     * Flux partagé de référence : statistiques, playlists intelligentes et playlists à
     * règles s'y abonnent plutôt que d'ouvrir chacun leur propre observateur Room.
     */
    val allStats: StateFlow<Map<Long, PlayStatEntity>> = playStatDao.observeAll()
        .map { list -> list.associateBy { it.songId } }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Les 25 derniers ajouts, base de la section « Nouveautés » de l'accueil. */
    val recentlyAdded: StateFlow<List<Song>> = _library
        .map { lib -> lib.songs }
        .distinctUntilChanged { a, b -> a === b }
        .map { songs -> songs.sortedByDescending { it.dateAddedSec }.take(25) }
        .flowOn(Dispatchers.Default)
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

    /**
     * Recherche synchrone sur la bibliothèque courante.
     *
     * Gardée pour les appelants hors coroutine (Assistant, Android Auto) ; l'UI passe par
     * [searchCancellable], qui s'interrompt dès qu'une frappe plus récente arrive.
     */
    fun search(query: String, limitPerSection: Int = 20): SearchResults =
        searchLibrary(_library.value, query, limitPerSection)

    /** Variante annulable de [search], à appeler hors du thread principal. */
    suspend fun searchCancellable(
        query: String,
        limitPerSection: Int = 20,
        library: Library = _library.value,
    ): SearchResults {
        val context = currentCoroutineContext()
        return searchLibrary(library, query, limitPerSection) { context.ensureActive() }
    }

    /** Filtre insensible aux accents de la recherche avancée, annulable. */
    suspend fun filterSongs(query: String, library: Library = _library.value): List<Song> {
        val context = currentCoroutineContext()
        return filterLibrarySongs(library, query) { context.ensureActive() }
    }

    // --- Tri -------------------------------------------------------------------------

    /**
     * Les tris alphabétiques passent par un `Collator` de la locale : « Édith » se range
     * avec les E, et non après « Zazie » comme avec une simple comparaison de chaînes.
     */
    fun sortSongs(songs: List<Song>, sort: SongSort, descending: Boolean): List<Song> {
        val stats = allStats.value
        return sortSongsWith(songs, sort, descending) { stats[it.id]?.playCount ?: 0 }
    }

    fun sortAlbums(albums: List<Album>, sort: AlbumSort, descending: Boolean): List<Album> =
        sortAlbumsWith(albums, sort, descending)

    fun sortArtists(artists: List<Artist>, sort: ArtistSort, descending: Boolean): List<Artist> =
        sortArtistsWith(artists, sort, descending)

    private companion object {
        /** En dessous, filtrer l'aléatoire appauvrirait trop la file. */
        const val MIN_SHUFFLE_POOL = 10

        /** Attente maximale d'un scan déjà lancé avant de relire MediaStore soi-même. */
        const val RESOLVE_SCAN_WAIT_MS = 5_000L
    }
}
