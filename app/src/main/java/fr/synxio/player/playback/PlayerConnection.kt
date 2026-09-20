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
    /** Début de la boucle A-B, null si aucun point posé. */
    val loopStartMs: Long? = null,
    /** Fin de la boucle A-B, null tant que le second point n'est pas posé. */
    val loopEndMs: Long? = null,
    val audioSessionId: Int = 0,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val loopState: AbLoopState
        get() = when {
            loopStartMs != null && loopEndMs != null -> AbLoopState.LOOPING
            loopStartMs != null -> AbLoopState.START_SET
            else -> AbLoopState.OFF
        }
}

/** Étapes du cycle de la répétition A-B, dans l'ordre où le bouton les parcourt. */
enum class AbLoopState { OFF, START_SET, LOOPING }

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

    /**
     * Commande émise avant que le MediaController ne soit connecté.
     *
     * Cas concret : un raccourci d'écran d'accueil déclenche « Tout mélanger » dès le
     * démarrage à froid, alors que la connexion au service prend encore quelques
     * centaines de millisecondes. Sans mise en attente, la commande était perdue.
     */
    private var pendingCommand: (() -> Unit)? = null

    private inline fun whenConnected(crossinline block: () -> Unit) {
        if (controller != null) block() else pendingCommand = { block() }
    }

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
                    pendingCommand?.invoke()
                    pendingCommand = null
                }
            },
            androidx.core.content.ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * Position et buffer ne génèrent pas d'événements : on les échantillonne.
     *
     * L'échantillonnage sert aussi à refermer la boucle A-B. La cadence passe à
     * [LOOP_TICK_MS] quand une boucle est active : à 250 ms, on dépasserait le point B
     * d'un quart de seconde audible avant de revenir.
     */
    private suspend fun tickPosition() {
        while (scope.isActive) {
            val looping = _state.value.loopState == AbLoopState.LOOPING
            delay(
                when {
                    !_state.value.isPlaying -> 1_000
                    looping -> LOOP_TICK_MS
                    else -> 250
                }
            )
            withContext(Dispatchers.Main) {
                val c = controller ?: return@withContext
                val position = c.currentPosition.coerceAtLeast(0)

                val start = _state.value.loopStartMs
                val end = _state.value.loopEndMs
                if (start != null && end != null && position >= end) {
                    c.seekTo(start)
                    _state.value = _state.value.copy(positionMs = start)
                    return@withContext
                }

                _state.value = _state.value.copy(
                    positionMs = position,
                    bufferedMs = c.bufferedPosition.coerceAtLeast(0),
                )
            }
        }
    }

    /**
     * Parcourt le cycle : poser A, poser B, effacer.
     *
     * Si le second point tombe avant le premier, les deux sont échangés plutôt que
     * refusés : reculer puis marquer la fin est un geste naturel, et produire une boucle
     * vide serait une impasse silencieuse.
     */
    fun cycleAbLoop() {
        val c = controller ?: return
        val position = c.currentPosition.coerceAtLeast(0)
        val state = _state.value

        _state.value = when (state.loopState) {
            AbLoopState.OFF -> state.copy(loopStartMs = position, loopEndMs = null)

            AbLoopState.START_SET -> {
                val start = state.loopStartMs ?: 0L
                val low = minOf(start, position)
                val high = maxOf(start, position)
                // Une boucle plus courte qu'une seconde ne produirait qu'un hoquet.
                if (high - low < MIN_LOOP_MS) {
                    state.copy(loopStartMs = null, loopEndMs = null)
                } else {
                    state.copy(loopStartMs = low, loopEndMs = high)
                }
            }

            AbLoopState.LOOPING -> state.copy(loopStartMs = null, loopEndMs = null)
        }
    }

    private fun syncState() {
        val c = controller ?: return
        val previous = _state.value
        val queue = (0 until c.mediaItemCount).mapNotNull {
            val item = c.getMediaItemAt(it)
            musicRepository.songById(item.songId) ?: item.toSynthesizedSong()
        }
        val current = c.currentMediaItem?.let { item ->
            musicRepository.songById(item.songId) ?: item.toSynthesizedSong()
        }

        // Cet état est reconstruit à chaque événement du lecteur, y compris une simple
        // pause. La boucle A-B n'existe que côté contrôleur : sans ce report explicite,
        // le premier appui sur pause l'effacerait. Elle appartient en revanche à la
        // piste, et ne survit donc pas à un changement de morceau.
        val sameTrack = current != null && current.id == previous.currentSong?.id

        _state.value = PlayerUiState(
            connected = true,
            currentSong = current,
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
            loopStartMs = previous.loopStartMs.takeIf { sameTrack },
            loopEndMs = previous.loopEndMs.takeIf { sameTrack },
            audioSessionId = runCatching { (c as? androidx.media3.exoplayer.ExoPlayer)?.audioSessionId ?: 0 }.getOrDefault(0),
        )
    }

    private inner class ControllerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState()
    }

    // --- Commandes ----------------------------------------------------------------------

    /** Remplace la file par [songs] et démarre à [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        whenConnected {
            val c = controller ?: return@whenConnected
            c.setMediaItems(songs.toMediaItems(), startIndex.coerceIn(songs.indices), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playSong(song: Song, context: List<Song>? = null) {
        val queue = context ?: listOf(song)
        play(queue, queue.indexOf(song).coerceAtLeast(0))
    }

    /** Démarre la lecture d'une webradio. */
    fun playRadio(station: fr.synxio.player.data.model.RadioStation) {
        whenConnected {
            val c = controller ?: return@whenConnected
            val id = -(station.stationUuid.hashCode().toLong() and 0x7FFFFFFF)
            
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist(station.tags.ifBlank { station.country })
                .setAlbumTitle("Webradio")
                .setArtworkUri(if (station.favicon.isNotBlank()) android.net.Uri.parse(station.favicon) else null)
                .setIsPlayable(true)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(id.toString())
                .setUri(station.urlResolved)
                .setMediaMetadata(metadata)
                .build()

            c.setMediaItem(mediaItem)
            c.prepare()
            c.play()
        }
    }

    /** Lance la sélection en aléatoire, en démarrant sur un titre au hasard. */
    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        whenConnected {
            controller?.shuffleModeEnabled = true
            val c = controller ?: return@whenConnected
            val index = songs.indices.random()
            c.setMediaItems(songs.toMediaItems(), index, 0L)
            c.prepare()
            c.play()
        }
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

    fun togglePlayPause() = whenConnected {
        val c = controller ?: return@whenConnected
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

    private companion object {
        /** Cadence d'échantillonnage quand une boucle A-B est active. */
        const val LOOP_TICK_MS = 60L
        /** En dessous, la boucle ne produirait qu'un hoquet au lieu d'une phrase. */
        const val MIN_LOOP_MS = 1_000L
    }
}
