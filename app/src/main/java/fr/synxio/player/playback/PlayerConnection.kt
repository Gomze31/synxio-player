package fr.synxio.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.RepeatMode
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUiState(
    val connected: Boolean = false,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Pont entre l'UI Compose et [PlaybackService].
 *
 * Toute la lecture passe par un `MediaController` : l'UI n'a jamais de référence
 * directe à ExoPlayer, ce qui évite les fuites et garde une seule source de vérité.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    init {
        scope.launch { connect() }
        scope.launch { tickPosition() }
    }

    private suspend fun connect() = withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(ControllerListener())
                    syncState()
                }
            },
            androidx.core.content.ContextCompat.getMainExecutor(context),
        )
    }

    /** Position et buffer ne génèrent pas d'événements : on les échantillonne. */
    private suspend fun tickPosition() {
        while (scope.isActive) {
            delay(if (_state.value.isPlaying) 250 else 1_000)
            withContext(Dispatchers.Main) {
                val c = controller ?: return@withContext
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0),
                    bufferedMs = c.bufferedPosition.coerceAtLeast(0),
                )
            }
        }
    }

    private fun syncState() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).mapNotNull {
            musicRepository.songById(c.getMediaItemAt(it).songId)
        }
        _state.value = PlayerUiState(
            connected = true,
            currentSong = c.currentMediaItem?.let { musicRepository.songById(it.songId) },
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            bufferedMs = c.bufferedPosition.coerceAtLeast(0),
            queue = queue,
            queueIndex = c.currentMediaItemIndex,
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            speed = c.playbackParameters.speed,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
        )
    }

    private inner class ControllerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState()
    }

    // --- Commandes ----------------------------------------------------------------------

    /** Remplace la file par [songs] et démarre à [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.setMediaItems(songs.toMediaItems(), startIndex.coerceIn(songs.indices), 0L)
        c.prepare()
        c.play()
    }

    fun playSong(song: Song, context: List<Song>? = null) {
        val queue = context ?: listOf(song)
        play(queue, queue.indexOf(song).coerceAtLeast(0))
    }

    /** Lance la sélection en aléatoire, en démarrant sur un titre au hasard. */
    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        val c = controller ?: return
        c.shuffleModeEnabled = true
        play(songs, songs.indices.random())
    }

    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) return play(songs)
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.toMediaItems())
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) return play(songs)
        c.addMediaItems(songs.toMediaItems())
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun pause() { controller?.pause() }

    fun next() { controller?.seekToNextMediaItem() }

    /** Retour arrière : redémarre le morceau si on est au-delà de 3 secondes. */
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun seekToProgress(progress: Float) {
        val duration = _state.value.durationMs
        if (duration > 0) seekTo((duration * progress).toLong())
    }

    fun seekForward() { controller?.seekForward() }
    fun seekBack() { controller?.seekBack() }

    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.seekTo(index, 0L)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) {
            c.moveMediaItem(from, to)
        }
    }

    fun clearQueue() { controller?.clearMediaItems() }

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        controller?.playbackParameters = PlaybackParameters(speed, pitch)
    }

    fun toggleFavorite() = sendCommand(PlaybackService.CMD_TOGGLE_FAVORITE)

    fun startSleepTimer(durationMs: Long, finishCurrentTrack: Boolean) = sendCommand(
        PlaybackService.CMD_SLEEP_TIMER,
        Bundle().apply {
            putLong(PlaybackService.ARG_DURATION_MS, durationMs)
            putBoolean(PlaybackService.ARG_FINISH_TRACK, finishCurrentTrack)
        },
    )

    fun cancelSleepTimer() = sendCommand(PlaybackService.CMD_CANCEL_SLEEP_TIMER)

    private fun sendCommand(action: String, args: Bundle = Bundle.EMPTY) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    /** L'identifiant de session audio courant, pour brancher un visualiseur. */
    val currentMediaItem: MediaItem? get() = controller?.currentMediaItem
}
