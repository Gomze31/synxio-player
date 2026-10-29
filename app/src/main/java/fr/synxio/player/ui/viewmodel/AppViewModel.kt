package fr.synxio.player.ui.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.prefs.Settings
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.media.DeleteOutcome
import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.inBookOrder
import fr.synxio.player.data.repo.Library
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.PlaylistRepository
import fr.synxio.player.data.repo.RulePlaylist
import fr.synxio.player.data.repo.RulePlaylistRepository
import fr.synxio.player.data.repo.SearchResults
import fr.synxio.player.data.repo.ShareCardRepository
import fr.synxio.player.data.repo.SimilarityRepository
import fr.synxio.player.data.repo.TrimRepository
import fr.synxio.player.data.repo.SmartPlaylist
import fr.synxio.player.data.repo.SmartPlaylistId
import fr.synxio.player.data.repo.SmartPlaylistRepository
import fr.synxio.player.playback.AbLoopState
import fr.synxio.player.playback.PlayerConnection
import fr.synxio.player.playback.PlayerUiState
import fr.synxio.player.playback.SleepTimer
import fr.synxio.player.playback.SleepTimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel partagé par tout le graphe de navigation.
 *
 * Bibliothèque, lecteur, réglages et favoris sont utilisés par presque chaque écran :
 * les regrouper évite de re-câbler les mêmes dépôts dans huit ViewModels différents.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val player: PlayerConnection,
    private val sleepTimer: SleepTimer,
    private val trimRepository: TrimRepository,
    private val downloadRepository: fr.synxio.player.data.repo.DownloadRepository,
    private val shareCardRepository: ShareCardRepository,
    private val similarityRepository: SimilarityRepository,
    private val rulePlaylistRepository: RulePlaylistRepository,
    private val radioRepository: fr.synxio.player.data.repo.RadioRepository,
    private val moodRadioRepository: fr.synxio.player.data.repo.MoodRadioRepository,
    private val equalizerController: fr.synxio.player.playback.EqualizerController,
    audioOutputMonitor: fr.synxio.player.playback.AudioOutputMonitor,
    smartPlaylistRepository: SmartPlaylistRepository,
    dailyMixRepository: fr.synxio.player.data.repo.DailyMixRepository,
    budsMonitor: fr.synxio.player.buds.BudsMonitor,
    private val ratingRepository: fr.synxio.player.data.repo.RatingRepository,
    private val showSpeedDao: fr.synxio.player.data.db.ShowSpeedDao,
    private val podcastProgressDao: fr.synxio.player.data.db.PodcastProgressDao,
    private val chapterRepository: fr.synxio.player.data.repo.ChapterRepository,
    private val bookmarkDao: fr.synxio.player.data.db.BookmarkDao,
) : ViewModel() {

    /** Positions de reprise des podcasts et chapitres, par identifiant de morceau. */
    val listeningPositions: StateFlow<Map<Long, fr.synxio.player.data.db.PodcastProgressEntity>> =
        podcastProgressDao.observeAll()
            .map { list -> list.associateBy { it.songId } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Progression de chaque livre audio, par chemin de dossier (un dossier = un livre).
     *
     * Tirée des positions enregistrées par le service : elle suit la lecture à dix
     * secondes près, et chaque pause.
     */
    /**
     * Chapitres intégrés aux fichiers de livres audio (m4b, mp3 à trames CHAP), par
     * identifiant de morceau. Seuls les fichiers qui en déclarent figurent ici.
     */
    val embeddedChapters: StateFlow<Map<Long, List<fr.synxio.player.data.media.Chapter>>> =
        musicRepository.library.map { it.audiobooks }.distinctUntilChanged()
            .mapLatest { books -> chapterRepository.chaptersOf(books) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val bookProgress: StateFlow<Map<String, fr.synxio.player.data.model.BookProgress>> = combine(
        musicRepository.library.map { it.audiobooks }.distinctUntilChanged(),
        listeningPositions,
        embeddedChapters,
    ) { books, byId, embedded ->
        books.groupBy { it.folderPath }.mapNotNull { (path, chapters) ->
            fr.synxio.player.data.model.BookProgress.of(chapters.inBookOrder(), byId, embedded)?.let { path to it }
        }.toMap()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Reprend un livre au fichier en cours ; le service y replace la position enregistrée.
     * Un livre fini repart du premier chapitre.
     */
    fun resumeBook(chapters: List<Song>) {
        if (chapters.isEmpty()) return
        val ordered = chapters.inBookOrder()
        val progress = bookProgress.value[ordered.first().folderPath]
        val index = if (progress == null || progress.finished) 0 else progress.fileIndex
        player.play(ordered, index.coerceIn(0, ordered.lastIndex))
    }

    /**
     * Saute à [positionMs] dans le fichier [songId] d'un livre (chapitre, signet).
     *
     * Fichier déjà en lecture : simple recherche. Sinon la position est enregistrée avant
     * de lancer le livre, et le service la retrouve au démarrage du fichier, comme une
     * reprise : un seekTo lancé ici se ferait écraser par cette reprise.
     */
    fun jumpInBook(files: List<Song>, songId: Long, positionMs: Long) {
        val ordered = files.inBookOrder()
        val index = ordered.indexOfFirst { it.id == songId }
        if (index < 0) return
        if (player.state.value.currentSong?.id == songId) {
            player.seekTo(positionMs)
            player.resume()
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                podcastProgressDao.saveProgress(
                    fr.synxio.player.data.db.PodcastProgressEntity(
                        songId = songId,
                        positionMs = positionMs.coerceAtLeast(0L),
                        lastPlayedSec = System.currentTimeMillis() / 1000,
                    )
                )
            }
            player.play(ordered, index)
        }
    }

    /** Signets du livre rangé dans [bookPath], dans l'ordre de création. */
    fun bookmarks(bookPath: String): kotlinx.coroutines.flow.Flow<List<fr.synxio.player.data.db.BookmarkEntity>> =
        bookmarkDao.observeFor(bookPath)

    /** Pose un signet dans le fichier [song] d'un livre, à [positionMs], avec une note facultative. */
    fun addBookmark(song: Song, positionMs: Long, note: String) {
        if (!song.isAudiobook) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarkDao.insert(
                    fr.synxio.player.data.db.BookmarkEntity(
                        songId = song.id,
                        songPath = song.path,
                        bookPath = song.folderPath,
                        positionMs = positionMs.coerceAtLeast(0L),
                        note = note.trim(),
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            _messages.emit("Signet ajouté à ${positionMs.asDuration()}")
        }
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) { bookmarkDao.delete(id) }
    }

    /**
     * Saute à un signet. Le fichier est retrouvé par son identifiant, sinon par son chemin
     * (identifiant changé après une réindexation).
     */
    fun openBookmark(files: List<Song>, bookmark: fr.synxio.player.data.db.BookmarkEntity) {
        val file = files.firstOrNull { it.id == bookmark.songId } ?: files.firstOrNull { it.path == bookmark.songPath }
        if (file == null) {
            viewModelScope.launch { _messages.emit("Le fichier de ce signet n'est plus dans le livre.") }
            return
        }
        jumpInBook(files, file.id, bookmark.positionMs)
    }

    /**
     * Marque des épisodes ou les chapitres d'un livre comme lus (chacun à sa fin) ou non
     * lus (position oubliée). [label] nomme ce qui a été marqué dans la confirmation.
     */
    fun setPlayed(songs: List<Song>, played: Boolean, label: String) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (played) {
                    podcastProgressDao.saveAll(
                        fr.synxio.player.data.model.markedPlayed(songs, System.currentTimeMillis() / 1000)
                    )
                } else {
                    podcastProgressDao.deleteFor(songs.map { it.id })
                }
            }
            _messages.emit(if (played) "« $label » marqué comme lu" else "« $label » marqué comme non lu")
        }
    }

    val similarityProgress = similarityRepository.progress
    val similarityAnalysedCount = similarityRepository.analysedCount
    
    fun analyseLibrary() = similarityRepository.analyseLibrary(library.value.songs)
    
    fun playMoodRadio(mood: fr.synxio.player.data.repo.Mood) {
        val mix = moodRadioRepository.generateMix(mood, library.value.songs)
        if (mix.isNotEmpty()) {
            player.play(mix, 0)
        } else {
            viewModelScope.launch { _messages.emit("Pas assez de données pour générer cette radio.") }
        }
    }

    val topRadios: StateFlow<List<fr.synxio.player.data.model.RadioStation>> = kotlinx.coroutines.flow.flow {
        emit(radioRepository.getTopRadios(100))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteRadios: StateFlow<List<fr.synxio.player.data.model.RadioStation>> = radioRepository.favoriteStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Sortie audio active (casque, Bluetooth, voiture). Le rappel système n'est inscrit
     * que pendant que le lecteur l'affiche.
     */
    val audioOutput: StateFlow<fr.synxio.player.playback.AudioOutput> = audioOutputMonitor.output
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), fr.synxio.player.playback.AudioOutput.Speaker)

    /** Galaxy Buds connectés (modèle, batterie), pour la pastille de sortie du lecteur. */
    val buds: StateFlow<fr.synxio.player.buds.BudsState?> = budsMonitor.state

    fun toggleRadioFavorite(station: fr.synxio.player.data.model.RadioStation) {
        radioRepository.toggleFavorite(station)
    }

    suspend fun searchRadios(query: String): List<fr.synxio.player.data.model.RadioStation> {
        return radioRepository.searchRadios(query)
    }

    suspend fun getRadiosByTag(tag: String): List<fr.synxio.player.data.model.RadioStation> {
        return radioRepository.getRadiosByTag(tag)
    }

    suspend fun getTopWorldRadios(): List<fr.synxio.player.data.model.RadioStation> {
        return radioRepository.getTopWorldRadios()
    }

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = smartPlaylistRepository.playlists
    val dailyMixes: StateFlow<List<fr.synxio.player.data.model.DailyMix>> = dailyMixRepository.mixes
    val rulePlaylists: StateFlow<List<RulePlaylist>> = rulePlaylistRepository.rulePlaylists

    val library: StateFlow<Library> = musicRepository.library
    val playerState: StateFlow<PlayerUiState> = player.state

    /**
     * Identifiant du titre en cours, seul.
     *
     * Les listes n'ont besoin que de lui pour surligner la ligne active : en lisant tout
     * [playerState], chaque ligne visible se recomposait à chaque tic de position (4×/s).
     */
    val currentSongId: StateFlow<Long?> = player.state
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Lecture demandée (vrai aussi pendant le chargement), sans la position. */
    val isPlaying: StateFlow<Boolean> = player.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Titre du morceau en cours, sans la position (utilisé notamment pour identifier la radio active). */
    val currentSongTitle: StateFlow<String?> = player.state
        .map { it.currentSong?.title }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
    val favoriteIds: StateFlow<Set<Long>> = musicRepository.favoriteIds
    val favoriteSongs: StateFlow<List<Song>> = musicRepository.favoriteSongs
    val recentlyAdded: StateFlow<List<Song>> = musicRepository.recentlyAdded
    val mostPlayed: StateFlow<List<Song>> = musicRepository.mostPlayed
    val recentlyPlayed: StateFlow<List<Song>> = musicRepository.recentlyPlayed
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state
    val fftFlow: StateFlow<ByteArray> = equalizerController.fftFlow

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Messages porteurs d'une action (« Annuler »…), affichés plus longtemps. */
    private val _actionMessages = MutableSharedFlow<ActionMessage>(extraBufferCapacity = 4)
    val actionMessages: SharedFlow<ActionMessage> = _actionMessages

    /**
     * Intents de partage à lancer.
     *
     * Le ViewModel ne connaît pas d'Activity : il produit l'intent, l'UI le démarre.
     * C'est ce qui permet de générer la carte depuis n'importe quel écran.
     */
    private val _shareIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareIntents: SharedFlow<Intent> = _shareIntents

    private val _deleteIntentSenders = MutableSharedFlow<android.content.IntentSender>(extraBufferCapacity = 1)
    val deleteIntentSenders: SharedFlow<android.content.IntentSender> = _deleteIntentSenders

    // Les listes de la bibliothèque, ne réémises que si le scan en a produit de nouvelles :
    // une bibliothèque recopiée (drapeau de chargement) ne relance aucun tri.
    private val librarySongs = library.map { it.songs }.distinctUntilChanged { a, b -> a === b }
    private val libraryAlbums = library.map { it.albums }.distinctUntilChanged { a, b -> a === b }
    private val libraryArtists = library.map { it.artists }.distinctUntilChanged { a, b -> a === b }

    /**
     * Titres triés selon la préférence courante.
     *
     * Seuls les champs de tri sont suivis dans les réglages : changer de thème ou de
     * vitesse ne doit pas retrier toute la bibliothèque. Le tri tourne hors du thread
     * principal.
     */
    val sortedSongs: StateFlow<List<Song>> = combine(
        librarySongs,
        settings.map { it.songSort to it.songSortDescending }.distinctUntilChanged(),
    ) { songs, (sort, descending) ->
        musicRepository.sortSongs(songs, sort, descending)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    
    val podcasts: StateFlow<List<Song>> = library
        .map { it.podcasts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val audiobooks: StateFlow<List<Song>> = library
        .map { it.audiobooks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val audiobookFolders: StateFlow<List<String>> = musicRepository.audiobookFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addAudiobookFolder(path: String) = viewModelScope.launch {
        musicRepository.addAudiobookFolder(path)
    }

    fun removeAudiobookFolder(path: String) = viewModelScope.launch {
        musicRepository.removeAudiobookFolder(path)
    }

    val sortedAlbums: StateFlow<List<Album>> = combine(
        libraryAlbums,
        settings.map { it.albumSort to it.albumSortDescending }.distinctUntilChanged(),
    ) { albums, (sort, descending) ->
        musicRepository.sortAlbums(albums, sort, descending)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortedArtists: StateFlow<List<Artist>> = combine(
        libraryArtists,
        settings.map { it.artistSort to it.artistSortDescending }.distinctUntilChanged(),
    ) { artists, (sort, descending) ->
        musicRepository.sortArtists(artists, sort, descending)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Section « Albums » de l'accueil : les 20 derniers ajouts, triés hors composition. */
    val recentAlbums: StateFlow<List<Album>> = libraryAlbums
        .map { albums -> albums.sortedByDescending { it.dateAddedSec }.take(HOME_SECTION_SIZE) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Section « Artistes » de l'accueil : les 20 artistes les plus fournis. */
    val topArtists: StateFlow<List<Artist>> = libraryArtists
        .map { artists ->
            musicRepository.sortArtists(artists, ArtistSort.SONG_COUNT, descending = true)
                .take(HOME_SECTION_SIZE)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Recherche -----------------------------------------------------------------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Résultats de recherche, calculés sur Dispatchers.Default.
     *
     * `mapLatest` annule le classement en cours dès qu'une frappe plus récente arrive ;
     * suivre la bibliothèque rafraîchit les résultats après un rescan.
     */
    val searchResults: StateFlow<SearchResults> = combine(
        _query.debounce(180).distinctUntilChanged(),
        library,
    ) { q, lib -> q to lib }
        .mapLatest { (q, lib) -> musicRepository.searchCancellable(q, library = lib) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChange(value: String) { _query.value = value }

    /** Filtre de la recherche avancée : insensible aux accents, hors du thread principal. */
    suspend fun filterSongs(query: String, lib: Library = library.value): List<Song> =
        withContext(Dispatchers.Default) { musicRepository.filterSongs(query, lib) }

    // --- Permission ----------------------------------------------------------------------

    fun onPermissionGranted() = musicRepository.onPermissionGranted()

    fun rescan() = viewModelScope.launch {
        musicRepository.refresh()
        _messages.emit("Bibliothèque actualisée")
    }

    // --- Lecture -------------------------------------------------------------------------

    fun play(songs: List<Song>, index: Int = 0) = player.play(songs, index)
    fun playSong(song: Song, context: List<Song>) = player.playSong(song, context)
    fun playRadio(station: fr.synxio.player.data.model.RadioStation) = player.playRadio(station)
    /**
     * Le filtrage se fait ici et non dans [PlayerConnection] : c'est un choix de
     * bibliothèque, pas une mécanique de lecteur, et il dépend des réglages.
     */
    fun shufflePlay(songs: List<Song>) {
        val pool = if (settings.value.shuffleSkipsDisliked) {
            musicRepository.withoutDisliked(songs)
        } else {
            songs
        }
        val removed = songs.size - pool.size
        if (removed > 0) emit("$removed titre${if (removed > 1) "s" else ""} souvent zappé" +
            "${if (removed > 1) "s" else ""} écarté${if (removed > 1) "s" else ""}")
        player.shufflePlay(pool)
    }
    fun togglePlayPause() = player.togglePlayPause()

    /**
     * Parcourt la répétition A-B : poser A, poser B, effacer.
     *
     * Le message dit à chaque étape ce qui vient de se passer — sans lui, poser « A »
     * ne produit aucun retour visible hors du bouton lui-même.
     */
    fun cycleAbLoop() {
        player.cycleAbLoop()
        emit(
            when (playerState.value.loopState) {
                AbLoopState.START_SET -> "Début de boucle posé — appuie à nouveau pour la fin"
                AbLoopState.LOOPING -> "Boucle active"
                AbLoopState.OFF -> "Boucle effacée"
            }
        )
    }
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun seekToProgress(progress: Float) = player.seekToProgress(progress)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun skipToQueueIndex(index: Int) = player.skipTo(index)
    fun removeFromQueue(index: Int) = player.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = player.moveInQueue(from, to)
    fun clearQueue() = player.clearQueue()

    fun playNext(songs: List<Song>) {
        player.playNext(songs)
        emit("${songs.size.let { if (it == 1) "Titre ajouté" else "$it titres ajoutés" }} à la suite")
    }

    fun addToQueue(songs: List<Song>) {
        player.addToQueue(songs)
        emit(if (songs.size == 1) "Ajouté à la file" else "${songs.size} titres ajoutés à la file")
    }

    /**
     * Sur un podcast ou un livre audio, la vitesse est mémorisée pour toute l'émission et
     * la vitesse globale (musique) ne bouge pas.
     */
    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        player.setSpeedAndPitch(speed, pitch)
        val showKey = fr.synxio.player.data.model.ShowKeys.of(player.state.value.currentSong)
        viewModelScope.launch {
            if (showKey != null) showSpeedDao.put(fr.synxio.player.data.db.ShowSpeedEntity(showKey, speed))
            else settingsRepository.setSpeed(speed)
            settingsRepository.setPitch(pitch)
        }
    }

    /** Note du titre en cours, 0 s'il n'est pas noté. */
    val currentRating: StateFlow<Int> = combine(currentSongId, ratingRepository.ratings) { id, ratings ->
        id?.let { ratings[it] } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Toucher la note déjà attribuée l'efface. */
    fun rateCurrent(stars: Int) {
        val song = player.state.value.currentSong ?: return
        val next = if (stars == currentRating.value) 0 else stars
        viewModelScope.launch { ratingRepository.setRating(song, next) }
    }

    /** La file d'attente devient une playlist (nom rendu unique au besoin). */
    fun saveQueueAsPlaylist(name: String) {
        val queue = player.state.value.queue
        if (queue.isEmpty()) {
            emit("La file d'attente est vide")
            return
        }
        createPlaylist(name, queue)
    }

    // --- Radio : morceaux au son proche ---------------------------------------------------

    /**
     * Lance une file de morceaux qui ressemblent a [seed].
     *
     * Le morceau de depart ouvre la file : partir directement sur un voisin donnerait
     * l'impression d'avoir saute une piste.
     */
    fun startRadio(seed: Song) = viewModelScope.launch {
        if (!similarityRepository.hasFingerprint(seed)) {
            _messages.emit("Ce morceau n'a pas encore ete analyse")
            return@launch
        }

        val similar = similarityRepository.similarTo(seed, library.value.songs)
        if (similar.isEmpty()) {
            _messages.emit("Analyse trop peu de morceaux pour trouver des voisins")
            return@launch
        }

        player.play(listOf(seed) + similar, 0)
        _messages.emit("Radio lancee : ${similar.size} titres proches")
    }

    // --- Partage -------------------------------------------------------------------------

    fun shareSong(song: Song) = viewModelScope.launch {
        val intent = shareCardRepository.createShareIntent(song)
        if (intent != null) _shareIntents.emit(intent)
        else _messages.emit("Impossible de générer la carte de partage")
    }

    // --- Playlists intelligentes ----------------------------------------------------------

    fun smartPlaylist(id: SmartPlaylistId): SmartPlaylist? =
        smartPlaylists.value.firstOrNull { it.id == id }

    // --- Playlists à règles ----------------------------------------------------------

    fun rulePlaylist(id: Long): RulePlaylist? =
        rulePlaylists.value.firstOrNull { it.id == id }

    fun deleteRulePlaylist(playlist: RulePlaylist) = viewModelScope.launch {
        rulePlaylistRepository.delete(playlist.id)
        _messages.emit("Règle « ${playlist.name} » supprimée")
    }

    // --- Favoris -------------------------------------------------------------------------

    fun toggleFavorite(song: Song) = viewModelScope.launch {
        val added = musicRepository.toggleFavorite(song)
        _messages.emit(if (added) "Ajouté aux favoris" else "Retiré des favoris")
    }

    fun isFavorite(song: Song?): Boolean = song != null && song.id in favoriteIds.value

    // --- Minuterie -----------------------------------------------------------------------

    fun startSleepTimer(minutes: Int, finishTrack: Boolean) {
        player.startSleepTimer(minutes * 60_000L, finishTrack)
        emit("Veille dans $minutes min")
    }

    fun cancelSleepTimer() {
        player.cancelSleepTimer()
        emit("Minuterie annulée")
    }

    // --- Playlists -----------------------------------------------------------------------

    fun createPlaylist(name: String, songs: List<Song> = emptyList()) = viewModelScope.launch {
        // Le nom réellement retenu : « Mix (2) » si « Mix » existait déjà.
        val unique = playlistRepository.uniqueName(name)
        playlistRepository.create(unique, songs)
        _messages.emit("Playlist « $unique » créée")
    }

    /**
     * Ajoute [songs] à une playlist en sautant ceux qui y sont déjà. Le message propose
     * d'annuler l'ajout, ou, si tout était déjà là, de l'ajouter quand même.
     */
    fun addToPlaylist(playlistId: Long, songs: List<Song>, skipExisting: Boolean = true): Job = viewModelScope.launch {
        if (songs.isEmpty()) return@launch
        val name = playlists.value.firstOrNull { it.id == playlistId }?.name ?: "la playlist"
        val result = playlistRepository.addSongs(playlistId, songs, skipExisting)
        if (result.added == 0) {
            _actionMessages.emit(
                ActionMessage(
                    text = if (result.skipped == 1) "Déjà dans « $name »" else "Déjà tous dans « $name »",
                    actionLabel = "Ajouter quand même",
                ) { addToPlaylist(playlistId, songs, skipExisting = false) }
            )
            return@launch
        }
        val text = buildString {
            append(if (result.added == 1) "Ajouté à « $name »" else "${result.added} titres ajoutés à « $name »")
            if (result.skipped > 0) append(" · ${result.skipped} déjà présent${if (result.skipped > 1) "s" else ""}")
        }
        _actionMessages.emit(
            ActionMessage(text, actionLabel = "Annuler") {
                viewModelScope.launch { playlistRepository.undoAdd(playlistId, result) }
            }
        )
    }

    fun moveInPlaylist(playlistId: Long, from: Int, to: Int) = viewModelScope.launch {
        playlistRepository.moveSong(playlistId, from, to)
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch {
        playlistRepository.removeSong(playlistId, songId)
    }

    fun renamePlaylist(playlistId: Long, name: String) = viewModelScope.launch {
        playlistRepository.rename(playlistId, name)
    }

    fun deletePlaylist(playlist: Playlist) = viewModelScope.launch {
        playlistRepository.delete(playlist.id)
        _messages.emit("Playlist « ${playlist.name} » supprimée")
    }

    fun reorderPlaylist(playlistId: Long, songIds: List<Long>) = viewModelScope.launch {
        playlistRepository.reorder(playlistId, songIds)
    }

    fun observePlaylist(id: Long) = playlistRepository.observePlaylist(id)

    fun exportPlaylist(playlist: Playlist, target: Uri) = viewModelScope.launch {
        playlistRepository.exportM3u(playlist, target)
            .onSuccess { _messages.emit("$it titres exportés") }
            .onFailure { _messages.emit("Export impossible : ${it.message}") }
    }

    fun importPlaylist(source: Uri) = viewModelScope.launch {
        playlistRepository.importM3u(source, "Playlist importée")
            .onSuccess { _messages.emit("Playlist importée") }
            .onFailure { _messages.emit("Import impossible : ${it.message}") }
    }

    // --- Réglages ------------------------------------------------------------------------

    fun setSongSort(sort: SongSort, descending: Boolean) = viewModelScope.launch {
        settingsRepository.setSongSort(sort, descending)
    }

    fun setAlbumSort(sort: AlbumSort, descending: Boolean) = viewModelScope.launch {
        settingsRepository.setAlbumSort(sort, descending)
    }

    fun setArtistSort(sort: ArtistSort, descending: Boolean) = viewModelScope.launch {
        settingsRepository.setArtistSort(sort, descending)
    }

    fun setVisualizerEnabled(enabled: Boolean) {
        equalizerController.setVisualizerEnabled(enabled)
    }

    fun toggleKaraoke() = viewModelScope.launch {
        val current = settings.value.karaokeEnabled
        settingsRepository.setKaraokeEnabled(!current)
        _messages.emit(if (!current) "Mode Karaoké activé" else "Mode Karaoké désactivé")
    }

    fun togglePartyMode() = viewModelScope.launch {
        val current = settings.value.partyEnabled
        settingsRepository.setPartyEnabled(!current)
        _messages.emit(if (!current) "Mode Party activé" else "Mode Party désactivé")
    }

    // --- Résolution pour les écrans de détail ---------------------------------------------

    fun albumById(id: Long) = musicRepository.albumById(id)
    fun artistByName(name: String) = musicRepository.artistByName(name)
    fun genreByName(name: String) = musicRepository.genreByName(name)
    fun folderByPath(path: String) = musicRepository.folderByPath(path)
    fun songById(id: Long) = musicRepository.songById(id)

    /** Pochette du morceau courant : source des couleurs dynamiques du thème. */
    val currentArtworkUri: StateFlow<Uri?> = playerState
        .map { it.currentSong?.artworkUri }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Point d'entrée public du bandeau de message.
     *
     * Les écrans imbriqués n'ont pas de `Scaffold` à eux : leur afficher un
     * `SnackbarHost` local le rendrait au fil de la liste, et il disparaîtrait au
     * défilement. Le seul host correctement positionné est celui de `MainScaffold`.
     */
    fun showMessage(text: String) = emit(text)

    // --- Métadonnées -----------------------------------------------------------------

    val metadataProgress: StateFlow<fr.synxio.player.data.meta.EnrichProgress> = musicRepository.metadataProgress
    val metadataStats: StateFlow<fr.synxio.player.data.meta.MetadataStats?> = musicRepository.metadataStats

    /** « Récupérer les infos et la pochette » d'un titre ou d'un album. */
    fun refetchMetadata(songs: List<Song>, label: String) = viewModelScope.launch {
        if (songs.isEmpty()) return@launch
        val result = musicRepository.refetchMetadata(songs, label)
        emit(refetchMessage(label, result))
    }

    fun reanalyseMetadata() {
        musicRepository.reanalyseMetadata()
        emit("Analyse des tags et pochettes relancée")
    }

    /** Morceaux en attente du consentement système pour leur suppression. */
    private var pendingDeletion: List<Song> = emptyList()

    fun deleteSong(song: Song) = deleteSongs(listOf(song))

    fun deleteSongs(songs: List<Song>) = viewModelScope.launch {
        if (songs.isEmpty()) return@launch
        pendingDeletion = songs
        handleDeleteOutcome(withContext(Dispatchers.IO) { musicRepository.deleteSongs(songs) })
    }

    /** Retour de la boîte de confirmation système. */
    fun onDeleteConsentResult(granted: Boolean) = viewModelScope.launch {
        if (!granted) {
            pendingDeletion = emptyList()
            emit("Suppression annulée")
            return@launch
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // La corbeille système a déjà agi en acceptant.
            onSongsDeleted()
        } else {
            // Android 10 : le consentement ouvre seulement l'accès, la suppression reste à faire.
            handleDeleteOutcome(withContext(Dispatchers.IO) { musicRepository.deleteSongs(pendingDeletion) })
        }
    }

    private suspend fun handleDeleteOutcome(outcome: DeleteOutcome) {
        when (outcome) {
            DeleteOutcome.Deleted -> onSongsDeleted()
            is DeleteOutcome.NeedsConsent -> _deleteIntentSenders.emit(outcome.intentSender)
            DeleteOutcome.Failed -> {
                pendingDeletion = emptyList()
                emit("Impossible de supprimer le fichier")
            }
        }
    }

    private suspend fun onSongsDeleted() {
        val deleted = pendingDeletion
        pendingDeletion = emptyList()
        player.removeSongsFromQueue(deleted.mapTo(HashSet()) { it.id })
        musicRepository.refresh()
        emit(if (deleted.size > 1) "${deleted.size} titres supprimés" else "Titre supprimé")
    }
    
    fun trimSong(song: Song, startMs: Long, endMs: Long) = viewModelScope.launch {
        emit("Découpage en cours...")
        val outPath = trimRepository.trimAudio(song, startMs, endMs)
        if (outPath != null) {
            emit("Découpage terminé : $outPath")
            // Rescan library to find the new file
            rescan()
        } else {
            emit("Échec du découpage")
        }
    }

    val downloadProgress = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val isDownloading = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun downloadYoutube(url: String) = viewModelScope.launch {
        isDownloading.value = true
        emit("Démarrage du téléchargement...")
        val result = downloadRepository.downloadAudio(url) { progress ->
            downloadProgress.value = progress
        }
        isDownloading.value = false
        if (result.isSuccess) {
            emit(result.getOrDefault("Téléchargement terminé"))
            rescan()
        } else {
            emit("Erreur de téléchargement: ${result.exceptionOrNull()?.message}")
        }
    }

    private fun emit(message: String) {
        viewModelScope.launch { _messages.emit(message) }
    }

    private companion object {
        /** Nombre de cartes des carrousels de l'accueil. */
        const val HOME_SECTION_SIZE = 20
    }
}

/** Bilan affiché après une récupération manuelle. */
internal fun refetchMessage(label: String, result: fr.synxio.player.data.meta.RefetchResult): String {
    if (result.total == 0) return "Titre introuvable dans la bibliothèque"
    val online = if (result.onlineSkipped) " · recherche en ligne indisponible" else ""
    if (result.total == 1) {
        val art = if (result.withArtwork > 0) "pochette trouvée" else "aucune pochette"
        val artist = if (result.withArtist > 0) "artiste identifié" else "artiste toujours inconnu"
        return "« $label » : $art, $artist$online"
    }
    return "« $label » : ${result.withArtwork}/${result.total} pochettes, " +
        "${result.withArtist}/${result.total} artistes$online"
}

/** Message de snackbar avec un bouton d'action ; [onAction] n'est appelé que si on le touche. */
class ActionMessage(val text: String, val actionLabel: String, val onAction: () -> Unit)
