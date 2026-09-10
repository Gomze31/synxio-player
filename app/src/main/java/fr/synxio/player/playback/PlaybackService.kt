package fr.synxio.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.MainActivity
import fr.synxio.player.R
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.core.prefs.toBandLevels
import fr.synxio.player.data.db.PlaybackStateEntity
import fr.synxio.player.data.db.QueueDao
import fr.synxio.player.data.lastfm.LastFmScrobbler
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service de lecture. C'est lui qui possède l'instance ExoPlayer : l'UI s'y connecte
 * via un `MediaController`, ce qui garde la lecture vivante quand l'activité meurt et
 * fait fonctionner notification, Bluetooth, Android Auto et Assistant sans code en plus.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var libraryTree: MediaLibraryTree
    @Inject lateinit var equalizer: EqualizerController
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var scrobbler: LastFmScrobbler
    @Inject lateinit var queueDao: QueueDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var fade: FadeController

    // Suivi d'écoute pour les statistiques et le scrobbling.
    private var trackedSongId: Long = -1L
    private var listenedMs: Long = 0L
    private var lastResumeUptime: Long = 0L
    private var trackStartedAtSec: Long = 0L
    private var persistJob: Job? = null
    private var queueRestored = false
    private var headsetCallback: AudioDeviceCallback? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Coupe la lecture quand on débranche le casque, comme tout lecteur sérieux.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        fade = FadeController(player, serviceScope)
        player.addListener(PlayerListener())
        player.addAnalyticsListener(AudioSessionListener())

        // On impose notre propre identifiant de session audio au lieu d'attendre
        // onAudioSessionIdChanged : sur cet appareil le callback ne se déclenchait
        // jamais, et l'égaliseur restait donc éternellement « indisponible ».
        attachAudioEffects()
        registerHeadsetResume()

        session = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(sessionActivityIntent())
            .setCustomLayout(ImmutableList.of(favoriteButton(false)))
            .build()

        sleepTimer.setCallback { fadeOutAndPause() }

        observeSettings()
        observeLibraryForQueueRestore()
        observeFavoriteForCustomLayout()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si rien ne joue quand l'utilisateur balaie l'app, inutile de garder le service.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            persistQueue()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistQueue()
        finalizeCurrentTrack(skipped = false)
        headsetCallback?.let { callback ->
            runCatching {
                getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(callback)
            }
        }
        headsetCallback = null
        fade.stop()
        sleepTimer.cancel()
        equalizer.release()
        session.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Réglages appliqués à chaud ---------------------------------------------------

    private fun observeSettings() {
        settingsRepository.settings
            .onEach { s ->
                player.skipSilenceEnabled = s.skipSilence
                player.playbackParameters = PlaybackParameters(s.playbackSpeed, s.playbackPitch)
                // 0 ms = gapless natif d'ExoPlayer, sans manipulation de volume.
                fade.durationMs = if (s.gaplessEnabled) s.crossfadeMs else s.crossfadeMs.coerceAtLeast(300)

                equalizer.setEnabled(s.equalizerEnabled)
                if (s.equalizerEnabled) {
                    s.equalizerBands.toBandLevels().takeIf { it.isNotEmpty() }
                        ?.let(equalizer::setBandLevels)
                    equalizer.setBassBoost(s.bassBoost)
                    equalizer.setVirtualizer(s.virtualizer)
                    equalizer.setLoudnessGain(s.loudnessGain)
                }
            }
            .launchIn(serviceScope)
    }

    private fun observeLibraryForQueueRestore() {
        musicRepository.library
            .filter { it.hasScanned && it.songs.isNotEmpty() }
            .onEach { restoreQueueOnce() }
            .launchIn(serviceScope)
    }

    /** Le bouton « favori » de la notification doit refléter le morceau courant. */
    private fun observeFavoriteForCustomLayout() {
        musicRepository.favoriteIds
            .map { favorites -> player.currentMediaItem?.songId?.let { it in favorites } ?: false }
            .distinctUntilChanged()
            .onEach { isFavorite ->
                session.setCustomLayout(ImmutableList.of(favoriteButton(isFavorite)))
            }
            .launchIn(serviceScope)
    }

    private suspend fun restoreQueueOnce() {
        if (queueRestored || player.mediaItemCount > 0) return
        queueRestored = true

        val saved = queueDao.queue()
        if (saved.isEmpty()) return
        val songs = musicRepository.songsByIds(saved.sortedBy { it.position }.map { it.songId })
        if (songs.isEmpty()) return

        val state = queueDao.state()
        player.setMediaItems(songs.toMediaItems(), state?.currentIndex ?: 0, state?.positionMs ?: 0L)
        player.shuffleModeEnabled = state?.shuffle ?: false
        player.repeatMode = state?.repeatMode ?: Player.REPEAT_MODE_OFF
        player.prepare()
        // On restaure en pause : reprendre tout seul au démarrage est intrusif.
    }

    private fun persistQueue() {
        persistJob?.cancel()
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).songId }
        val state = PlaybackStateEntity(
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueTitle = player.currentMediaItem?.mediaMetadata?.albumTitle?.toString().orEmpty(),
        )
        persistJob = serviceScope.launch(Dispatchers.IO) { queueDao.persist(ids, state) }
    }

    // --- Suivi d'écoute ----------------------------------------------------------------

    private fun startTracking(item: MediaItem?) {
        trackedSongId = item?.songId ?: -1L
        listenedMs = 0L
        lastResumeUptime = if (player.isPlaying) SystemClock.elapsedRealtime() else 0L
        trackStartedAtSec = System.currentTimeMillis() / 1000

        val song = musicRepository.songById(trackedSongId) ?: return
        serviceScope.launch { scrobbler.updateNowPlaying(song) }
    }

    private fun accumulate() {
        if (lastResumeUptime > 0L) {
            listenedMs += SystemClock.elapsedRealtime() - lastResumeUptime
            lastResumeUptime = 0L
        }
    }

    /**
     * Un morceau écouté à plus de la moitié compte comme lu ; en dessous, c'est un skip.
     * C'est ce qui rend « Les plus écoutés » fiable au lieu de compter chaque effleurement.
     */
    private fun finalizeCurrentTrack(skipped: Boolean) {
        accumulate()
        val song: Song = musicRepository.songById(trackedSongId) ?: return
        val listened = listenedMs
        val startedAt = trackStartedAtSec
        if (listened < 3_000) return

        val counted = !skipped || listened >= song.durationMs / 2
        serviceScope.launch(Dispatchers.IO) {
            if (counted) musicRepository.registerPlay(song, listened)
            else musicRepository.registerSkip(song, listened)

            if (scrobbler.qualifies(song, listened)) scrobbler.scrobble(song, startedAt)
        }
        listenedMs = 0L
    }

    private fun fadeOutAndPause() {
        serviceScope.launch {
            val steps = 20
            val initial = player.volume
            repeat(steps) { i ->
                player.volume = initial * (1f - (i + 1f) / steps)
                delay(50)
            }
            player.pause()
            player.volume = initial
        }
    }

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val skipped = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            finalizeCurrentTrack(skipped)

            if (sleepTimer.shouldStopAtTrackEnd()) {
                sleepTimer.cancel()
                player.pause()
            }

            startTracking(mediaItem)
            fade.resetVolume()
            persistQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastResumeUptime = SystemClock.elapsedRealtime()
                if (trackedSongId < 0) startTracking(player.currentMediaItem)
            } else {
                accumulate()
                persistQueue()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) fade.resetVolume()
        }
    }

    /**
     * L'identifiant de session audio n'est publié que par `AnalyticsListener` ;
     * c'est lui qu'attendent Equalizer, BassBoost et LoudnessEnhancer.
     */
    /**
     * Reprend la lecture quand un casque est branché ou appairé.
     *
     * On écoute les périphériques audio plutôt que `ACTION_HEADSET_PLUG` : un seul
     * rappel couvre le jack, l'USB-C et le Bluetooth A2DP, et il ne demande pas la
     * permission BLUETOOTH_CONNECT.
     *
     * Garde-fou : on ne reprend que si une file existe et que la lecture avait été
     * mise en pause — sinon brancher un casque démarrerait de la musique alors que
     * l'utilisateur n'a jamais rien lancé.
     */
    private fun registerHeadsetResume() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return

        headsetCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val isHeadset = addedDevices.orEmpty().any { it.type in HEADSET_TYPES }
                if (!isHeadset) return

                serviceScope.launch {
                    if (!settingsRepository.settings.first().resumeOnHeadsetConnect) return@launch
                    if (player.mediaItemCount == 0 || player.playWhenReady) return@launch

                    // Court délai : la route audio n'est pas encore basculée sur le
                    // casque à l'instant où le périphérique est signalé.
                    delay(600)
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.play()
                }
            }
        }

        runCatching { audioManager.registerAudioDeviceCallback(headsetCallback, null) }
            .onFailure { headsetCallback = null }
    }

    /**
     * Crée une session audio explicite et y branche les effets.
     *
     * `generateAudioSessionId()` peut échouer (retourne ERROR) : dans ce cas on laisse
     * ExoPlayer choisir la sienne et on retombe sur le callback analytics.
     */
    private fun attachAudioEffects() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        val sessionId = runCatching { audioManager.generateAudioSessionId() }.getOrNull()
        if (sessionId == null || sessionId == AudioManager.ERROR) return

        runCatching { player.audioSessionId = sessionId }
            .onFailure { return }

        equalizer.attach(sessionId)
        serviceScope.launch { applyAudioEffectSettings() }
    }

    private suspend fun applyAudioEffectSettings() {
        val s = settingsRepository.settings.first()
        equalizer.setEnabled(s.equalizerEnabled)
        if (!s.equalizerEnabled) return
        s.equalizerBands.toBandLevels().takeIf { it.isNotEmpty() }?.let(equalizer::setBandLevels)
        equalizer.setBassBoost(s.bassBoost)
        equalizer.setVirtualizer(s.virtualizer)
        equalizer.setLoudnessGain(s.loudnessGain)
    }

    private inner class AudioSessionListener : AnalyticsListener {
        override fun onAudioSessionIdChanged(
            eventTime: AnalyticsListener.EventTime,
            audioSessionId: Int,
        ) {
            equalizer.attach(audioSessionId)
            serviceScope.launch {
                val s = settingsRepository.settings.first()
                equalizer.setEnabled(s.equalizerEnabled)
                if (s.equalizerEnabled) {
                    s.equalizerBands.toBandLevels().takeIf { it.isNotEmpty() }
                        ?.let(equalizer::setBandLevels)
                    equalizer.setBassBoost(s.bassBoost)
                    equalizer.setVirtualizer(s.virtualizer)
                    equalizer.setLoudnessGain(s.loudnessGain)
                }
            }
        }
    }

    // --- Session ------------------------------------------------------------------------

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_TOGGLE_FAVORITE -> {
                    val song = musicRepository.songById(player.currentMediaItem?.songId ?: -1L)
                    if (song != null) serviceScope.launch { musicRepository.toggleFavorite(song) }
                }

                CMD_SLEEP_TIMER -> sleepTimer.start(
                    durationMs = args.getLong(ARG_DURATION_MS, 30 * 60_000L),
                    finishCurrentTrack = args.getBoolean(ARG_FINISH_TRACK, false),
                )

                CMD_CANCEL_SLEEP_TIMER -> sleepTimer.cancel()

                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(libraryTree.rootItem, params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = libraryTree.children(parentId)
            val paged = children.drop(page * pageSize).take(pageSize)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = libraryTree.itemById(mediaId)
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
            )
        }

        /**
         * Les contrôleurs (notification, Auto, Assistant) n'envoient qu'un `mediaId` :
         * on le retransforme en `MediaItem` jouable avec son URI.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                if (item.localConfiguration != null) item
                else musicRepository.songById(item.songId)?.toMediaItem()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /** « Ok Google, joue du Nekfeu » depuis Android Auto. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val query = mediaItems.firstOrNull()?.requestMetadata?.searchQuery
            if (!query.isNullOrBlank()) {
                val songs = libraryTree.search(query)
                if (songs.isNotEmpty()) {
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(songs.toMediaItems(), 0, 0L)
                    )
                }
            }
            return super<MediaLibrarySession.Callback>.onSetMediaItems(
                mediaSession, controller, mediaItems, startIndex, startPositionMs
            )
        }

        /**
         * Reprise depuis un bouton Bluetooth ou la notification « reprendre » d'Android 13+ :
         * on repropose la file d'attente sauvegardée.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = com.google.common.util.concurrent.SettableFuture
                .create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val saved = queueDao.queue().sortedBy { it.position }
                val state = queueDao.state()
                val songs = musicRepository.songsByIds(saved.map { it.songId })
                if (songs.isEmpty()) {
                    future.setException(UnsupportedOperationException("Aucune file sauvegardée"))
                } else {
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            songs.toMediaItems(),
                            state?.currentIndex ?: 0,
                            state?.positionMs ?: 0L,
                        )
                    )
                }
            }
            return future
        }
    }

    private fun favoriteButton(isFavorite: Boolean) = CommandButton.Builder()
        .setDisplayName(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris")
        .setIconResId(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        .setSessionCommand(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
        .build()

    private fun sessionActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CMD_TOGGLE_FAVORITE = "fr.synxio.player.TOGGLE_FAVORITE"
        const val CMD_SLEEP_TIMER = "fr.synxio.player.SLEEP_TIMER"
        const val CMD_CANCEL_SLEEP_TIMER = "fr.synxio.player.CANCEL_SLEEP_TIMER"
        const val ARG_DURATION_MS = "duration_ms"
        const val ARG_FINISH_TRACK = "finish_track"

        /** Sorties considérées comme « un casque » pour la reprise automatique. */
        private val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
