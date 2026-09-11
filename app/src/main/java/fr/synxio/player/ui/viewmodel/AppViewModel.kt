package fr.synxio.player.ui.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.prefs.Settings
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.data.repo.Library
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.PlaylistRepository
import fr.synxio.player.data.repo.SearchResults
import fr.synxio.player.data.repo.ShareCardRepository
import fr.synxio.player.data.repo.SmartPlaylist
import fr.synxio.player.data.repo.SmartPlaylistId
import fr.synxio.player.data.repo.SmartPlaylistRepository
import fr.synxio.player.playback.PlayerConnection
import fr.synxio.player.playback.PlayerUiState
import fr.synxio.player.playback.SleepTimer
import fr.synxio.player.playback.SleepTimerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel partagé par tout le graphe de navigation.
 *
 * Bibliothèque, lecteur, réglages et favoris sont utilisés par presque chaque écran :
 * les regrouper évite de re-câbler les mêmes dépôts dans huit ViewModels différents.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val player: PlayerConnection,
    private val sleepTimer: SleepTimer,
    private val shareCardRepository: ShareCardRepository,
    smartPlaylistRepository: SmartPlaylistRepository,
) : ViewModel() {

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = smartPlaylistRepository.playlists

    val library: StateFlow<Library> = musicRepository.library
    val playerState: StateFlow<PlayerUiState> = player.state
    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
    val favoriteIds: StateFlow<Set<Long>> = musicRepository.favoriteIds
    val favoriteSongs: StateFlow<List<Song>> = musicRepository.favoriteSongs
    val recentlyAdded: StateFlow<List<Song>> = musicRepository.recentlyAdded
    val mostPlayed: StateFlow<List<Song>> = musicRepository.mostPlayed
    val recentlyPlayed: StateFlow<List<Song>> = musicRepository.recentlyPlayed
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /**
     * Intents de partage à lancer.
     *
     * Le ViewModel ne connaît pas d'Activity : il produit l'intent, l'UI le démarre.
     * C'est ce qui permet de générer la carte depuis n'importe quel écran.
     */
    private val _shareIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareIntents: SharedFlow<Intent> = _shareIntents

    /** Titres triés selon la préférence courante, recalculés à chaque changement. */
    val sortedSongs: StateFlow<List<Song>> = combine(library, settings) { lib, s ->
        musicRepository.sortSongs(lib.songs, s.songSort, s.songSortDescending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortedAlbums = combine(library, settings) { lib, s ->
        musicRepository.sortAlbums(lib.albums, s.albumSort, s.albumSortDescending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortedArtists = combine(library, settings) { lib, s ->
        musicRepository.sortArtists(lib.artists, s.artistSort, s.artistSortDescending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Recherche -----------------------------------------------------------------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<SearchResults> = _query
        .debounce(180)
        .distinctUntilChanged()
        .flatMapLatest { q -> flowOf(musicRepository.search(q)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChange(value: String) { _query.value = value }

    // --- Permission ----------------------------------------------------------------------

    fun onPermissionGranted() = musicRepository.onPermissionGranted()

    fun rescan() = viewModelScope.launch {
        musicRepository.refresh()
        _messages.emit("Bibliothèque actualisée")
    }

    // --- Lecture -------------------------------------------------------------------------

    fun play(songs: List<Song>, index: Int = 0) = player.play(songs, index)
    fun playSong(song: Song, context: List<Song>) = player.playSong(song, context)
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

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        player.setSpeedAndPitch(speed, pitch)
        viewModelScope.launch {
            settingsRepository.setSpeed(speed)
            settingsRepository.setPitch(pitch)
        }
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
        playlistRepository.create(playlistRepository.uniqueName(name), songs)
        _messages.emit("Playlist « $name » créée")
    }

    fun addToPlaylist(playlistId: Long, songs: List<Song>) = viewModelScope.launch {
        playlistRepository.addSongs(playlistId, songs)
        _messages.emit(if (songs.size == 1) "Titre ajouté" else "${songs.size} titres ajoutés")
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

    private fun emit(message: String) {
        viewModelScope.launch { _messages.emit(message) }
    }
}
