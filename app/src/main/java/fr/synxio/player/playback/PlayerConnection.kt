package fr.synxio.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Util
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    /**
     * Lecture demandée : vrai aussi pendant la mise en mémoire tampon.
     *
     * C'est ce qui pilote l'icône lecture/pause. Basé sur `Player.isPlaying`, le bouton
     * clignotait sur « lecture » à chaque chargement et ne permettait pas de mettre en
     * pause une radio qui se connecte.
     */
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

    /**
     * Erreurs de lecture, à montrer à l'utilisateur.
     *
     * Sans message, un titre illisible passait au suivant (ou la lecture s'arrêtait) sans
     * que personne ne sache pourquoi. Le code d'erreur aide aussi au diagnostic.
     */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors

    private var controller: MediaController? = null

    /**
     * Commande émise avant que le MediaController ne soit connecté.
     *
     * Cas concret : un raccourci d'écran d'accueil déclenche « Tout mélanger » dès le
     * démarrage à froid, alors que la connexion au service prend encore quelques
     * centaines de millisecondes. Sans mise en attente, la commande était perdue.
     */
    private val pendingCommands = ArrayList<() -> Unit>()

    private inline fun whenConnected(crossinline block: () -> Unit) {
        if (controller != null) block() else pendingCommands += { block() }
    }

    /**
     * Commandes qui n'ont de sens que sur une file : suivant, précédent, aléatoire,
     * répétition, favori.
     *
     * Au démarrage à froid (appui sur le widget, processus éteint), la file sauvegardée
     * n'est relue qu'après le scan de la bibliothèque. Une commande arrivée avant agissait
     * sur un lecteur vide (« suivant » sans effet), ou était écrasée par la restauration
     * (l'aléatoire repassait à son ancienne valeur). Elle attend donc la file, au plus
     * [QUEUE_WAIT_MS] : sans file sauvegardée, elle s'exécute quand même.
     */
    private val pendingUntilQueue = ArrayList<() -> Unit>()
    private var queueWaitStarted = false

    private fun whenQueueReady(block: () -> Unit) = whenConnected {
        val c = controller ?: return@whenConnected
        if (c.mediaItemCount > 0) {
            block()
            return@whenConnected
        }
        pendingUntilQueue += block
        if (!queueWaitStarted) {
            queueWaitStarted = true
            scope.launch(Dispatchers.Main) {
                delay(QUEUE_WAIT_MS)
                flushQueueCommands()
            }
        }
    }

    private fun flushQueueCommands() {
        queueWaitStarted = false
        if (pendingUntilQueue.isEmpty()) return
        val commands = pendingUntilQueue.toList()
        pendingUntilQueue.clear()
        commands.forEach { it() }
    }

    init {
        scope.launch { connect() }
        scope.launch { tickPosition() }
        // La file affichée est reconstruite quand la bibliothèque change (tags édités,
        // rescan) : sinon elle gardait anciens titres et pochettes, ou les fiches
        // provisoires créées avant la fin du scan, jusqu'au prochain changement de file.
        scope.launch {
            musicRepository.library.collect {
                withContext(Dispatchers.Main) { syncState(rebuildQueue = true) }
            }
        }
    }

    private suspend fun connect(): Unit = withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                // Service tué (mémoire, mise à jour) : sans reconnexion, toutes les
                // commandes devenaient silencieusement sans effet jusqu'au redémarrage.
                override fun onDisconnected(controller: MediaController) {
                    this@PlayerConnection.controller = null
                    _state.value = _state.value.copy(connected = false, isPlaying = false)
                    scope.launch {
                        delay(RECONNECT_DELAY_MS)
                        connect()
                    }
                }
            })
            .buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(ControllerListener())
                    syncState(rebuildQueue = true)
                    // Toutes les commandes en attente, dans l'ordre : n'en garder qu'une
                    // perdait par exemple « lire » suivi de « ajouter à la file ».
                    val commands = pendingCommands.toList()
                    pendingCommands.clear()
                    commands.forEach { it() }
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

    /**
     * Reconstruit l'état depuis le contrôleur.
     *
     * La file n'est relue que si la timeline a changé : la reconstruire à chaque pause ou
     * avancée coûtait une recherche par morceau sur le thread principal, soit des milliers
     * d'opérations pour une grande file.
     */
    private fun syncState(rebuildQueue: Boolean) {
        val c = controller ?: return
        val previous = _state.value
        val queue = if (rebuildQueue || previous.queue.size != c.mediaItemCount) {
            (0 until c.mediaItemCount).mapNotNull {
                val item = c.getMediaItemAt(it)
                musicRepository.songById(item.songId) ?: item.toSynthesizedSong()
            }
        } else {
            previous.queue
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
            isPlaying = !Util.shouldShowPlayButton(c),
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
        )
    }

    private inner class ControllerListener : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val title = controller?.currentMediaItem?.mediaMetadata?.title ?: "ce titre"
            _errors.tryEmit("Impossible de lire « $title » (${error.errorCodeName})")
        }

        override fun onEvents(player: Player, events: Player.Events) {
            syncState(rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
            // File restaurée : les commandes du widget en attente peuvent partir.
            if (player.mediaItemCount > 0 && pendingUntilQueue.isNotEmpty()) flushQueueCommands()
        }
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

    /**
     * Lit un fichier ouvert depuis une autre app (« Ouvrir avec Synxio »).
     *
     * Si le fichier fait partie de la bibliothèque, on le joue comme tel (stats, pochette,
     * favoris). Sinon on le lit tel quel, avec son nom de fichier pour titre.
     */
    fun playExternalUri(uri: android.net.Uri, displayName: String?) {
        val known = uri.lastPathSegment?.toLongOrNull()
            ?.takeIf { uri.authority == android.provider.MediaStore.AUTHORITY }
            ?.let(musicRepository::songById)
        if (known != null) return play(listOf(known))

        whenConnected {
            val c = controller ?: return@whenConnected
            val title = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Fichier audio"
            val item = MediaItem.Builder()
                .setMediaId((-(uri.toString().hashCode().toLong() and 0x7FFFFFFF) - 1).toString())
                .setUri(uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    /** Lance la sélection en aléatoire, en démarrant sur un titre au hasard. */
    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        whenConnected {
            val c = controller ?: return@whenConnected
            // Aléatoire coupé puis rétabli après la nouvelle file : le service fige alors
            // le titre de départ en tête de l'ordre aléatoire. Activé avant, l'ordre
            // tiré au hasard sautait tous les titres placés avant le point de départ.
            c.shuffleModeEnabled = false
            c.setMediaItems(songs.toMediaItems(), songs.indices.random(), 0L)
            c.shuffleModeEnabled = true
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

    /**
     * Gère aussi les cas où `play()` seul ne fait rien : après une erreur (IDLE) il faut
     * préparer, et en fin de file (ENDED) revenir au début.
     */
    fun togglePlayPause() = whenConnected {
        val c = controller ?: return@whenConnected
        Util.handlePlayPauseButtonAction(c)
    }

    fun pause() { controller?.pause() }

    fun next() = whenQueueReady {
        val c = controller ?: return@whenQueueReady
        c.seekToNextMediaItem()
        // Après une erreur le lecteur reste IDLE : changer de piste ne relançait rien.
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
    }

    /**
     * Retour arrière : redémarre le morceau au-delà de 3 secondes, sinon piste précédente.
     * `seekToPrevious` redémarre aussi le premier morceau, où l'ancien code ne faisait rien.
     */
    fun previous() = whenQueueReady {
        val c = controller ?: return@whenQueueReady
        c.seekToPrevious()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
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

    fun toggleShuffle() = whenQueueReady {
        val c = controller ?: return@whenQueueReady
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() = whenQueueReady {
        val c = controller ?: return@whenQueueReady
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

    /** Retire de la file tous les éléments de ces morceaux (fichiers supprimés). */
    fun removeSongsFromQueue(songIds: Set<Long>) {
        val c = controller ?: return
        for (index in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(index).mediaId.toLongOrNull() in songIds) c.removeMediaItem(index)
        }
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

    fun toggleFavorite() = whenQueueReady { sendCommand(PlaybackService.CMD_TOGGLE_FAVORITE) }

    fun startSleepTimer(durationMs: Long, finishCurrentTrack: Boolean) = sendCommand(
        PlaybackService.CMD_SLEEP_TIMER,
        Bundle().apply {
            putLong(PlaybackService.ARG_DURATION_MS, durationMs)
            putBoolean(PlaybackService.ARG_FINISH_TRACK, finishCurrentTrack)
        },
    )

    fun cancelSleepTimer() = sendCommand(PlaybackService.CMD_CANCEL_SLEEP_TIMER)

    // En attente si le contrôleur se connecte encore : une commande du widget arrive
    // souvent processus froid, avant la fin de la connexion au service.
    private fun sendCommand(action: String, args: Bundle = Bundle.EMPTY) = whenConnected {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    /** L'identifiant de session audio courant, pour brancher un visualiseur. */
    val currentMediaItem: MediaItem? get() = controller?.currentMediaItem

    private companion object {
        /** Attente maximale de la file restaurée pour une commande du widget. */
        const val QUEUE_WAIT_MS = 6_000L

        /** Cadence d'échantillonnage quand une boucle A-B est active. */
        const val LOOP_TICK_MS = 60L
        /** En dessous, la boucle ne produirait qu'un hoquet au lieu d'une phrase. */
        const val MIN_LOOP_MS = 1_000L
        const val RECONNECT_DELAY_MS = 1_000L
    }
}
