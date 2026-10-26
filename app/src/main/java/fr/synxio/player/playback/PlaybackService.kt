package fr.synxio.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
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
import fi.iki.elonen.NanoHTTPD
import fr.synxio.player.MainActivity
import fr.synxio.player.R
import fr.synxio.player.core.prefs.Settings
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.core.prefs.toBandLevels
import fr.synxio.player.data.db.PlaybackStateEntity
import fr.synxio.player.data.db.DeviceProfileDao
import fr.synxio.player.data.db.QueueDao
import fr.synxio.player.data.model.EqCurves
import fr.synxio.player.data.lastfm.LastFmScrobbler
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.discord.DiscordPresenceRepository
import fr.synxio.player.data.repo.DiscordRepository
import fr.synxio.player.data.repo.LoudnessRepository
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.widget.WidgetSnapshot
import fr.synxio.player.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
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
    @Inject lateinit var deviceProfileDao: DeviceProfileDao
    @Inject lateinit var loudnessRepository: LoudnessRepository
    @Inject lateinit var discord: DiscordRepository
    @Inject lateinit var discordPresence: DiscordPresenceRepository
    @Inject lateinit var podcastProgressDao: fr.synxio.player.data.db.PodcastProgressDao
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var historyRepository: fr.synxio.player.data.repo.HistoryRepository
    @Inject lateinit var budsMonitor: fr.synxio.player.buds.BudsMonitor
    @Inject lateinit var dailyMixes: fr.synxio.player.data.repo.DailyMixRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Écritures en base qui doivent survivre à la destruction du service.
     *
     * `serviceScope` est annulé dans `onDestroy` : les dernières écritures (écoute en
     * cours, scrobble, progression de podcast) lancées juste avant y étaient annulées
     * avant d'aboutir.
     */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Écritures de file / position, appliquées une à une dans l'ordre d'émission.
     *
     * Annuler une écriture en cours pour la remplacer annulait aussi sa transaction Room :
     * une file modifiée pouvait alors ne jamais être enregistrée si l'écriture suivante ne
     * portait que sur la position. Ici rien n'est annulé, et la dernière photo gagne.
     */
    private val queueWrites = Channel<QueueSnapshot>(Channel.UNLIMITED)
    private val queueWriter: Job = writeScope.launch {
        for (snapshot in queueWrites) {
            runCatching { writeSnapshot(snapshot) }
                .onFailure { android.util.Log.w(TAG, "File non enregistrée", it) }
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private var castPlayer: androidx.media3.cast.CastPlayer? = null
    private val activePlayer: Player get() = session.player
    private lateinit var fade: FadeController

    /**
     * Réveil en cours : la lecture passe sur le flux « alarme » le temps qu'il sonne.
     *
     * Le volume musique peut être à zéro la veille au soir, et Android 15+ interdit à une
     * appli en arrière-plan de le relever : le flux alarme suit le volume des réveils, que
     * l'utilisateur règle pour être réveillé. La montée progressive passe par le gain du
     * lecteur, que rien ne bride.
     */
    private var alarmRamp: Job? = null
    private var alarmActive = false
    private var alarmHasPlayed = false

    val vocalRemover = fr.synxio.player.playback.effects.VocalRemoverAudioProcessor()
    private var partyServer: PartyServer? = null
    private var castServer: CastMediaServer? = null

    // Suivi d'écoute pour les statistiques et le scrobbling.
    private var trackedSongId: Long = -1L
    private var listenedMs: Long = 0L
    private var lastResumeUptime: Long = 0L
    private var trackStartedAtSec: Long = 0L
    private var persistSoonJob: Job? = null
    private var positionSaveJob: Job? = null
    private var widgetJob: Job? = null

    /**
     * Dernier drapeau de lecture écrit en base, par la persistance ou par le widget.
     *
     * La position est sauvegardée toutes les dix secondes pendant la lecture, et chaque
     * sauvegarde redemande le widget : sans ce repère, le drapeau était réécrit à
     * l'identique à chaque fois. Null tant que rien n'a été écrit.
     */
    @Volatile
    private var storedPlaying: Boolean? = null

    /** Vrai quand la composition de la file a changé depuis la dernière écriture. */
    private var queueDirty = true

    /** Neutralise la persistance pendant la restauration : l'état n'est pas encore complet. */
    private var restoring = false

    /**
     * Vrai une fois la file sauvegardée relue (ou une file présente dans le lecteur).
     *
     * Avant cela, un lecteur vide ne veut pas dire « file vidée » : la restauration attend
     * la fin du scan. Écrire ce vide effaçait la file sauvegardée si le service s'arrêtait
     * avant (connexion brève de la reprise système après un redémarrage, app balayée
     * pendant le démarrage).
     */
    private var queueLoaded = false

    /** Décide entre passer au titre suivant et s'arrêter quand un titre est illisible. */
    private val errorPolicy = PlaybackErrorPolicy()

    /** Dernier morceau annoncé à Last.fm / Discord : une annonce par morceau, en lecture seulement. */
    private var announcedSongId: Long = -1L

    /** Instant et morceau de la dernière pause, pour le retour en arrière automatique. */
    private var pausedAtUptime: Long = 0L
    private var pausedMediaId: String? = null
    private var inactivityJob: Job? = null

    /**
     * Derniers réglages connus.
     *
     * Le changement de piste est un callback synchrone : il ne peut pas attendre un
     * `first()` sur le flux sans retarder l'application du gain d'une piste entière.
     */
    private var latestSettings: Settings? = null
    private var queueRestored = false
    private var headsetCallback: AudioDeviceCallback? = null

    /** Galaxy Buds connectés, suivis tant que le service vit (gestes, alerte batterie). */
    private var latestBuds: fr.synxio.player.buds.BudsState? = null

    /** Gestes des boutons de casque (double, triple appui, appui long). */
    private val mediaButtons = MediaButtonInterpreter()
    private val buttonHandler = Handler(Looper.getMainLooper())
    private val resolveButtons = Runnable {
        mediaButtons.onTimeout(SystemClock.uptimeMillis())?.let(::runButtonCommand)
    }

    /** Dossiers que les navigateurs (Android Auto) affichent, à prévenir quand ils changent. */
    private val subscribedParents = HashSet<String>()
    private var lastSearch: Pair<String, List<MediaItem>>? = null

    private var sensorManager: android.hardware.SensorManager? = null
    private var shakeDetector: fr.synxio.player.core.util.ShakeDetector? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        _stopRequested.value = false

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("SynxioMediaPlayer/${fr.synxio.player.BuildConfig.VERSION_NAME}")

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(vocalRemover))
                    .build()
            }
        }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(mediaAttributes(spatial = true), /* handleAudioFocus = */ true)
            // Coupe la lecture quand on débranche le casque, comme tout lecteur sérieux.
            .setHandleAudioBecomingNoisy(true)
            // NETWORK et non LOCAL : webradios et podcasts sont lus en flux, et le Wi-Fi
            // se mettait en veille écran éteint jusqu'à faire caler le flux. Les verrous ne
            // sont tenus que pendant la lecture.
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
            .setCustomLayout(customLayout(LayoutState(favorite = null, shuffle = false, repeatMode = Player.REPEAT_MODE_OFF)))
            .build()

        sleepTimer.setCallback(sleepTimerCallback)

        observeSettings()
        observeLibraryForQueueRestore()
        observeFavoriteForCustomLayout()
        observeBrowseChanges()
        observeBuds()

        serviceScope.launch {
            sleepTimer.state.collect { state ->
                fade.sleepGain = state.fadeMultiplier
            }
        }

        sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        shakeDetector = fr.synxio.player.core.util.ShakeDetector {
            if (activePlayer.hasNextMediaItem()) {
                activePlayer.seekToNextMediaItem()
            }
        }

        initCastPlayer()
    }

    private fun initCastPlayer() {
        try {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this, executor).addOnSuccessListener { castContext ->
                castPlayer = androidx.media3.cast.CastPlayer(castContext).apply {
                    // Un seul listener pour toute la vie du CastPlayer : l'ajouter à chaque
                    // connexion Cast doublait stats et scrobbles.
                    addListener(PlayerListener())
                    setSessionAvailabilityListener(object : androidx.media3.cast.SessionAvailabilityListener {
                        override fun onCastSessionAvailable() {
                            val server = startCastServer() ?: return
                            val pos = player.currentPosition.coerceAtLeast(0)
                            val playWhenReady = player.playWhenReady
                            val idx = player.currentMediaItemIndex.coerceAtLeast(0)

                            player.pause()

                            val items = (0 until player.mediaItemCount)
                                .map { server.toCastItem(player.getMediaItemAt(it)) }
                            this@apply.setMediaItems(items, idx, pos)
                            this@apply.playWhenReady = playWhenReady
                            this@apply.prepare()

                            session.player = this@apply
                        }

                        override fun onCastSessionUnavailable() {
                            val cp = castPlayer ?: return
                            val pos = cp.currentPosition.coerceAtLeast(0)
                            val playWhenReady = cp.playWhenReady
                            val idx = cp.currentMediaItemIndex.coerceAtLeast(0)

                            // La file a pu changer pendant le Cast : on la rapatrie, en
                            // reconvertissant chaque morceau vers son URI locale.
                            val items = (0 until cp.mediaItemCount).map { i ->
                                val item = cp.getMediaItemAt(i)
                                musicRepository.songById(item.songId)?.toMediaItem() ?: item
                            }
                            cp.stop()
                            stopCastServer()

                            if (items.isNotEmpty()) player.setMediaItems(items, idx, pos)
                            else player.seekTo(idx, pos)
                            player.playWhenReady = playWhenReady
                            player.prepare()

                            session.player = player
                        }
                    })
                }
            }
        } catch (e: Exception) {
            // Ignorer si Google Play Services n'est pas dispo
        }
    }

    private fun startCastServer(): CastMediaServer? {
        castServer?.let { return it }
        val server = CastMediaServer(this, musicRepository::songById)
        runCatching { server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }
            .onFailure { android.util.Log.w("PlaybackService", "Serveur Cast indisponible", it) }
        if (!server.isReachable) {
            server.stop()
            return null
        }
        castServer = server
        return server
    }

    private fun stopCastServer() {
        castServer?.stop()
        castServer = null
    }

    /** Pendant un Cast, les morceaux doivent pointer vers le serveur local. */
    private fun forActivePlayer(items: List<MediaItem>): List<MediaItem> {
        val server = castServer
        return if (server != null && activePlayer === castPlayer) items.map(server::toCastItem) else items
    }

    private val sleepTimerCallback: () -> Unit = { pauseForSleepTimer() }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si rien ne joue quand l'utilisateur balaie l'app, inutile de garder le service.
        // shouldShowPlayButton couvre aussi la fin de file et l'erreur, où
        // playWhenReady reste vrai alors que plus rien ne joue.
        if (Util.shouldShowPlayButton(activePlayer) || activePlayer.mediaItemCount == 0) {
            persistQueue()
            requestStop()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Écriture synchrone et bornée : lancée en tâche de fond, elle était annulée par
        // serviceScope.cancel() quelques lignes plus bas, et la dernière position perdue.
        persistSoonJob?.cancel()
        if (canPersist()) queueWrites.trySend(queueSnapshot(includeQueue = queueDirty))
        queueWrites.close()
        runCatching {
            runBlocking { withTimeoutOrNull(FINAL_WRITE_TIMEOUT_MS) { queueWriter.join() } }
        }
        // Le widget ne doit pas rester sur « lecture » une fois le service parti.
        isRunning = false
        val (lastSnapshot, lastArt) = widgetSnapshot()
        val updater = widgetUpdater
        writeScope.launch { runCatching { updater.publish(lastSnapshot.copy(isPlaying = false), lastArt) } }
        // Fin de service : la piste en cours n'est ni terminée ni passée, juste interrompue.
        finalizeCurrentTrack(skipped = false, finished = false)
        positionSaveJob?.cancel()
        sleepTimer.clearCallback(sleepTimerCallback)
        headsetCallback?.let { callback ->
            runCatching {
                getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(callback)
            }
        }
        headsetCallback = null
        buttonHandler.removeCallbacksAndMessages(null)
        fade.release()
        sleepTimer.cancel()
        sensorManager?.unregisterListener(shakeDetector)

        // Sans cet effacement, le statut Discord resterait figé sur le dernier morceau
        // bien après l'arrêt de Synxio. Exécuté hors du scope du service, qui est annulé
        // quelques lignes plus bas.
        val presence = discordPresence
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { presence.disconnect() }
        equalizer.release()
        session.release()
        castPlayer?.release()
        player.release()
        partyServer?.stop()
        stopCastServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Réglages appliqués à chaud ---------------------------------------------------

    private fun observeSettings() {
        settingsRepository.settings
            .onEach { s ->
                val previous = latestSettings
                latestSettings = s
                player.skipSilenceEnabled = s.skipSilence
                activePlayer.playbackParameters = PlaybackParameters(s.playbackSpeed, s.playbackPitch)
                // 0 ms = gapless natif d'ExoPlayer, sans manipulation de volume.
                fade.durationMs = if (s.gaplessEnabled) s.crossfadeMs else s.crossfadeMs.coerceAtLeast(300)

                equalizer.setEnabled(s.equalizerEnabled)
                if (s.equalizerEnabled) {
                    s.equalizerBands.toBandLevels().takeIf { it.isNotEmpty() }
                        ?.let(equalizer::setBandLevels)
                    equalizer.setBassBoost(s.bassBoost)
                    equalizer.setVirtualizer(s.virtualizer)
                    equalizer.setReverbPreset(s.reverbPreset.toShort())
                    equalizer.setLoudnessGain(s.loudnessGain)
                }

                // La normalisation se réapplique dès que son réglage change, sans
                // attendre le morceau suivant.
                if (previous == null ||
                    previous.normalizeVolume != s.normalizeVolume ||
                    previous.normalizeTargetDbfs != s.normalizeTargetDbfs
                ) {
                    applyNormalization()
                }

                if (s.shakeToSkip && previous?.shakeToSkip != true) {
                    sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
                        sensorManager?.registerListener(shakeDetector, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
                    }
                } else if (!s.shakeToSkip && previous?.shakeToSkip == true) {
                    sensorManager?.unregisterListener(shakeDetector)
                }

                if (previous != null && previous.themeMode != s.themeMode) refreshWidgetSoon()
                vocalRemover.setEnabled(s.karaokeEnabled)
                mediaButtons.gesturesEnabled = s.headsetMultiClick
                // Pas pendant un réveil : il impose ses propres attributs (usage alarme).
                if (previous != null && previous.spatialAudio != s.spatialAudio && !alarmActive) {
                    player.setAudioAttributes(mediaAttributes(s.spatialAudio), /* handleAudioFocus = */ true)
                }
                fade.volumeCap = if (s.maxVolumeLimit) s.maxVolumeValue else 1f

                if (s.partyEnabled && partyServer == null) {
                    partyServer = runCatching { PartyServer(8080).apply { start() } }.getOrNull()
                    updatePartyServerState()
                } else if (!s.partyEnabled && partyServer != null) {
                    partyServer?.stop()
                    partyServer = null
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
            .onEach {
                refreshCustomLayout(it)
                refreshWidgetSoon()
            }
            .launchIn(serviceScope)
    }

    /**
     * Prévient les navigateurs abonnés (Android Auto) quand la bibliothèque, les playlists,
     * les favoris ou les radios changent : sans cela, l'écran de la voiture restait sur
     * « bibliothèque en cours de chargement » jusqu'à ce qu'on y revienne.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeBrowseChanges() {
        libraryTree.changes
            .debounce(BROWSE_NOTIFY_DEBOUNCE_MS)
            .onEach {
                lastSearch = null
                if (subscribedParents.isEmpty()) return@onEach
                for (parentId in subscribedParents.toList()) {
                    val count = runCatching { libraryTree.tree.children(parentId).size }.getOrDefault(0)
                    session.notifyChildrenChanged(parentId, count, null)
                }
            }
            .launchIn(serviceScope)
    }

    /** État des boutons personnalisés : favori (null hors bibliothèque), aléatoire, répétition. */
    private data class LayoutState(val favorite: Boolean?, val shuffle: Boolean, val repeatMode: Int)

    private var layoutState: LayoutState? = null

    /**
     * Recalculé à chaque changement de piste, de favoris, d'aléatoire et de répétition :
     * n'écouter que les favoris laissait le cœur de la notification et de la voiture dans
     * l'état du morceau précédent.
     */
    private fun refreshCustomLayout(favorites: Set<Long> = musicRepository.favoriteIds.value) {
        val p = activePlayer
        val songId = p.currentMediaItem?.songId?.takeIf { it >= 0 }
        val state = LayoutState(
            favorite = songId?.let { it in favorites },
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
        )
        if (layoutState == state) return
        layoutState = state
        session.setCustomLayout(customLayout(state))
    }

    private suspend fun restoreQueueOnce() {
        if (queueRestored || player.mediaItemCount > 0) return
        queueRestored = true
        try {
            restoreSavedQueue()
        } finally {
            queueLoaded = true
        }
    }

    private suspend fun restoreSavedQueue() {
        // « Mémoriser la file » désactivé : on démarre à vide, comme demandé.
        if (!settingsRepository.settings.first().rememberQueue) return

        val restored = runCatching { loadSavedQueue() }
            .onFailure { android.util.Log.w(TAG, "File sauvegardée illisible", it) }
            .getOrNull() ?: return
        val (queue, state) = restored

        // Les lectures en base ci-dessus suspendent : une commande arrivée entre-temps
        // (« tout lire au hasard » depuis un raccourci) a pu remplir la file. On ne
        // l'écrase pas avec l'ancienne.
        if (activePlayer.mediaItemCount > 0) return

        restoring = true
        try {
            activePlayer.setMediaItems(forActivePlayer(queue.items), queue.index, queue.positionMs)
            activePlayer.shuffleModeEnabled = state?.shuffle ?: false
            activePlayer.repeatMode = state?.repeatMode ?: Player.REPEAT_MODE_OFF
            activePlayer.prepare()
            // On restaure en pause : reprendre tout seul au démarrage est intrusif.
        } catch (e: RuntimeException) {
            // Une file corrompue ne doit jamais empêcher l'app de démarrer.
            android.util.Log.w(TAG, "Restauration de la file impossible", e)
        } finally {
            restoring = false
        }
        // Des titres disparus ont pu être écartés : on réécrit la file réelle.
        queueDirty = true
        queueLoaded = true
        persistQueue()
    }

    /** Relit la file et l'état sauvegardés, en écartant proprement les morceaux disparus. */
    private suspend fun loadSavedQueue(): Pair<RestoredQueue<MediaItem>, PlaybackStateEntity?>? {
        val saved = queueDao.queue().sortedBy { it.position }
        if (saved.isEmpty()) return null
        val ids = saved.map { it.songId }
        val byId = musicRepository.songsByIds(ids).associateBy { it.id }
        val state = queueDao.state()
        val queue = restoreQueue(
            savedIds = ids,
            savedIndex = state?.currentIndex ?: 0,
            savedPositionMs = state?.positionMs ?: 0L,
            available = byId,
        ) ?: return null
        return RestoredQueue(queue.items.toMediaItems(), queue.index, queue.positionMs) to state
    }

    /** Photo de l'état du lecteur, prise sur le thread principal. */
    private class QueueSnapshot(val ids: List<Long>?, val state: PlaybackStateEntity?)

    private fun queueSnapshot(includeQueue: Boolean): QueueSnapshot {
        val p = activePlayer
        val count = p.mediaItemCount
        if (count == 0) return QueueSnapshot(ids = emptyList(), state = null)
        val ids = if (includeQueue) (0 until count).map { p.getMediaItemAt(it).songId } else null
        val state = PlaybackStateEntity(
            currentIndex = p.currentMediaItemIndex,
            positionMs = p.currentPosition.coerceAtLeast(0),
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            queueTitle = p.currentMediaItem?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            isPlaying = p.isPlaying,
        )
        return QueueSnapshot(ids, state)
    }

    private suspend fun writeSnapshot(snapshot: QueueSnapshot) {
        val ids = snapshot.ids
        val state = snapshot.state
        when {
            // File vidée : sans cet effacement, l'ancienne file revenait au démarrage suivant.
            ids != null && ids.isEmpty() -> queueDao.clear()
            ids != null && state != null -> queueDao.persist(ids, state)
            state != null -> queueDao.saveState(state)
        }
        storedPlaying = state?.isPlaying
    }

    /**
     * Enregistre la file et la position.
     *
     * La file complète n'est réécrite que si sa composition a changé ([queueDirty]) :
     * la réécrire à chaque pause ou changement de piste coûtait un DELETE + N INSERT,
     * soit des dizaines de milliers de lignes après un « tout lire au hasard ».
     */
    /** Un lecteur vide n'est enregistré qu'une fois la file sauvegardée relue. */
    private fun canPersist(): Boolean {
        if (activePlayer.mediaItemCount > 0) queueLoaded = true
        return queueLoaded
    }

    private fun persistQueue() {
        if (restoring || !canPersist()) return
        val includeQueue = queueDirty || activePlayer.mediaItemCount == 0
        queueDirty = false
        queueWrites.trySend(queueSnapshot(includeQueue))
        refreshWidgetSoon()
    }

    /**
     * Sauvegarde la position régulièrement pendant la lecture.
     *
     * Si le système tue le processus, la reprise se fait à quelques secondes près au lieu
     * de repartir du début du morceau.
     */
    private fun startPositionSaver() {
        if (positionSaveJob?.isActive == true) return
        positionSaveJob = serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (!activePlayer.isPlaying) break
                // Dix secondes de lecture continue : la lecture fonctionne vraiment.
                errorPolicy.onListened(POSITION_SAVE_INTERVAL_MS)
                persistQueue()
            }
        }
    }

    /**
     * Redessine le widget d'écran d'accueil, une fois la rafale retombée.
     *
     * Ce travail a sa propre tâche, distincte de celle de persistance.
     *
     * Il y vivait au départ, et n'aboutissait presque jamais : `persistQueue` annule sa
     * tâche précédente à chaque appel, et un changement de piste en déclenche plusieurs
     * coup sur coup. Le rafraîchissement partait donc systématiquement à la poubelle
     * avec elle — « v1 was cancelled » dans le journal.
     *
     * Le délai sert aussi de temporisation : il laisse la persistance écrire l'état que
     * le widget va relire, et absorbe les appels groupés.
     */
    private fun refreshWidgetSoon() {
        widgetJob?.cancel()
        widgetJob = serviceScope.launch {
            delay(WIDGET_REFRESH_DELAY_MS)

            // L'état est relu ici, au dernier moment, et non repris de l'instantané pris
            // par le callback déclencheur : une transition de piste peut suivre le passage
            // en lecture, et l'on publiait parfois un état transitoire faux.
            val playing = activePlayer.isPlaying
            val (snapshot, artworkUri) = widgetSnapshot()
            runCatching {
                if (playing != storedPlaying) {
                    queueDao.setPlaying(playing)
                    storedPlaying = playing
                }
                widgetUpdater.publish(snapshot, artworkUri)
            }.onFailure { android.util.Log.d("SynxioWidget", "Widget non rafraichi", it) }
        }
    }

    /**
     * Photo de la lecture pour les widgets, avec l'URI de la pochette à préparer.
     *
     * Titre et artiste viennent des métadonnées du lecteur : elles couvrent aussi les
     * webradios, absentes de la bibliothèque, et leurs titres ICY qui changent en direct.
     */
    private fun widgetSnapshot(): Pair<WidgetSnapshot, android.net.Uri?> {
        val p = activePlayer
        val item = p.currentMediaItem ?: return WidgetSnapshot() to null
        val metadata = p.mediaMetadata
        val song = musicRepository.songById(item.songId)
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: song?.durationMs ?: 0L
        val snapshot = WidgetSnapshot(
            mediaId = item.mediaId.ifEmpty { item.localConfiguration?.uri?.toString().orEmpty() },
            title = metadata.title?.toString() ?: song?.title.orEmpty(),
            artist = metadata.artist?.toString() ?: song?.displayArtist.orEmpty(),
            album = metadata.albumTitle?.toString() ?: song?.album.orEmpty(),
            isPlaying = !Util.shouldShowPlayButton(p),
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            capturedAtMs = System.currentTimeMillis(),
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            favorite = song != null && song.id in musicRepository.favoriteIds.value,
            hasNext = p.hasNextMediaItem(),
            live = p.isCurrentMediaItemLive || (song == null && duration <= 0L),
        )
        return snapshot to (metadata.artworkUri ?: song?.artworkUri)
    }

    // --- Réveil en musique ---------------------------------------------------------------

    private fun enterAlarmMode(rampSeconds: Int) {
        if (activePlayer !== player) return
        alarmActive = true
        alarmHasPlayed = false
        player.setAudioAttributes(ALARM_ATTRIBUTES, /* handleAudioFocus = */ true)
        alarmRamp?.cancel()
        if (rampSeconds <= 0) {
            fade.alarmGain = 1f
            return
        }
        fade.alarmGain = ALARM_START_GAIN
        alarmRamp = serviceScope.launch {
            val steps = rampSeconds * 1_000L / ALARM_RAMP_TICK_MS
            for (step in 1..steps) {
                delay(ALARM_RAMP_TICK_MS)
                fade.alarmGain = ALARM_START_GAIN + (1f - ALARM_START_GAIN) * step / steps
            }
        }
    }

    private fun exitAlarmMode() {
        alarmActive = false
        alarmRamp?.cancel()
        fade.alarmGain = 1f
        player.setAudioAttributes(mediaAttributes(latestSettings?.spatialAudio ?: true), /* handleAudioFocus = */ true)
        // Pause depuis le casque, l'écran de verrouillage ou l'appli : la notification
        // « Arrêter / Répéter » n'a plus d'objet.
        getSystemService(android.app.NotificationManager::class.java)
            .cancel(fr.synxio.player.alarm.AlarmReceiver.NOTIFICATION_ID)
    }

    // --- Suivi d'écoute ----------------------------------------------------------------

    private fun startTracking(item: MediaItem?) {
        trackedSongId = item?.songId ?: -1L
        listenedMs = 0L
        lastResumeUptime = if (activePlayer.isPlaying) SystemClock.elapsedRealtime() else 0L
        trackStartedAtSec = System.currentTimeMillis() / 1000

        val song = musicRepository.songById(trackedSongId) ?: return
        announceIfPlaying(song)
        publishPresence(song)
    }

    /**
     * Annonce « en écoute » à Last.fm et au webhook Discord, une fois par morceau et
     * seulement quand il joue vraiment : restaurer la file en pause au démarrage, ou
     * zapper en pause, publiait sinon des écoutes qui n'avaient pas lieu.
     */
    private fun announceIfPlaying(song: Song? = musicRepository.songById(trackedSongId)) {
        if (song == null || !activePlayer.isPlaying || announcedSongId == song.id) return
        announcedSongId = song.id
        serviceScope.launch { scrobbler.updateNowPlaying(song) }
        serviceScope.launch { discord.announce(song) }
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
    private fun finalizeCurrentTrack(skipped: Boolean, finished: Boolean = !skipped) {
        accumulate()
        persistPodcastProgress(finished = finished)

        val song: Song = musicRepository.songById(trackedSongId) ?: return
        val listened = listenedMs
        val startedAt = trackStartedAtSec
        if (listened < 3_000) return

        val counted = !skipped || listened >= song.durationMs / 2
        writeScope.launch {
            if (counted) {
                musicRepository.registerPlay(song, listened)
                // Le journal d'écoute n'était jamais alimenté : l'historique restait vide
                // et les statistiques par période ne voyaient aucune lecture.
                historyRepository.add(song.id, song.path, listened, song.durationMs)
            } else {
                musicRepository.registerSkip(song, listened)
            }

            if (scrobbler.qualifies(song, listened)) scrobbler.scrobble(song, startedAt)
        }
        listenedMs = 0L
    }

    private fun persistPodcastProgress(finished: Boolean = false) {
        val song = musicRepository.songById(trackedSongId) ?: return
        if (!song.isPodcast) return

        val position = if (finished) 0L else activePlayer.currentPosition.coerceAtLeast(0)
        writeScope.launch {
            podcastProgressDao.saveProgress(
                fr.synxio.player.data.db.PodcastProgressEntity(
                    songId = song.id,
                    positionMs = position,
                    lastPlayedSec = System.currentTimeMillis() / 1000
                )
            )
        }
    }

    /**
     * Fin de la minuterie de veille.
     *
     * Le fondu est déjà fait : `sleepGain` descend à 0 sur la dernière minute via
     * [FadeController]. Un second fondu ici écrasait `player.volume` hors de ce contrôleur.
     */
    private fun pauseForSleepTimer() {
        serviceScope.launch { activePlayer.pause() }
    }

    /**
     * Applique le gain de normalisation de la piste qui commence.
     *
     * Recalculé à chaque transition plutôt qu'une fois pour la file : la mesure d'un
     * morceau peut arriver pendant la lecture, si l'analyse tourne en arrière-plan.
     */
    private fun applyNormalization() {
        val settings = latestSettings ?: return
        if (!settings.normalizeVolume) {
            fade.normalizationGain = 1f
            return
        }
        val song = currentSong() ?: return
        fade.normalizationGain = loudnessRepository.gainFor(song, settings.normalizeTargetDbfs)
    }

    private fun currentSong(): Song? {
        val id = activePlayer.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        return musicRepository.songById(id)
    }

    /**
     * Publie le statut d'activité Discord.
     *
     * La position est lue au moment de l'appel pour que la barre de progression du statut
     * reflète l'endroit réel du morceau — reprendre une lecture à mi-parcours afficherait
     * sinon une progression repartie de zéro.
     */
    private fun publishPresence(song: Song?) {
        val current = song ?: currentSong() ?: return
        val position = activePlayer.currentPosition.coerceAtLeast(0)
        val playing = activePlayer.isPlaying
        serviceScope.launch { discordPresence.update(current, position, playing) }
    }

    private fun updatePartyServerState() {
        partyServer?.updateState(
            song = currentSong(),
            playing = activePlayer.isPlaying,
            position = activePlayer.currentPosition.coerceAtLeast(0)
        )
    }

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Seule une fin naturelle (ou une répétition) termine la piste. Choisir un autre
            // titre (PLAYLIST_CHANGED) ou zapper (SEEK) l'interrompt : compter cela comme
            // « terminé » remettait à zéro la progression d'un podcast à moitié écouté.
            val endedNaturally = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            finalizeCurrentTrack(skipped = !endedNaturally, finished = endedNaturally)

            // Nouvelle file lancée alors que l'aléatoire était déjà actif : l'ordre tiré
            // par ExoPlayer ne commence pas forcément par le titre choisi.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                activePlayer.shuffleModeEnabled
            ) {
                anchorShuffleOrderOnCurrent()
            }

            if (sleepTimer.shouldStopAtTrackEnd()) {
                sleepTimer.cancel()
                activePlayer.pause()
            }

            startTracking(mediaItem)

            // Restore podcast progress
            val current = currentSong()
            if (current != null && current.isPodcast && reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                val mediaId = mediaItem?.mediaId
                serviceScope.launch {
                    val progress = withContext(Dispatchers.IO) { podcastProgressDao.getProgress(current.id) }
                    // L'utilisateur a pu zapper pendant la lecture en base : on ne saute
                    // jamais dans un autre morceau à la position du podcast.
                    if (progress != null && progress.positionMs > 0 &&
                        activePlayer.currentMediaItem?.mediaId == mediaId
                    ) {
                        activePlayer.seekTo(progress.positionMs)
                    }
                }
            }

            // Auto-DJ : prolonge la file avec des titres proches, seulement si l'utilisateur
            // l'a demandé — sinon un album ou une playlist ne s'arrêterait jamais.
            if (latestSettings?.autoDjEnabled == true &&
                activePlayer.mediaItemCount > 0 &&
                isNearEndOfPlaybackOrder()
            ) {
                if (current != null && !current.isPodcast) {
                    val queued = (0 until activePlayer.mediaItemCount)
                        .mapTo(HashSet()) { activePlayer.getMediaItemAt(it).songId }
                    val similarSongs = musicRepository.library.value.songs.filter {
                        (it.genre == current.genre || it.artistId == current.artistId) && it.id !in queued
                    }.shuffled().take(5)

                    if (similarSongs.isNotEmpty()) {
                        activePlayer.addMediaItems(forActivePlayer(similarSongs.toMediaItems()))
                    }
                }
            }

            applyNormalization()
            fade.resetVolume()
            persistQueue()
            updatePartyServerState()
            refreshCustomLayout()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                queueDirty = true
                persistQueueSoon()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) anchorShuffleOrderOnCurrent()
            persistQueue()
            refreshCustomLayout()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            persistQueue()
            refreshCustomLayout()
        }

        /** Titre ICY d'une webradio, pochette chargée tard : le widget suit. */
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            refreshWidgetSoon()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // « Fin du morceau » sur la dernière piste : aucune transition ne suit, et la
            // minuterie restait active à 0:00 indéfiniment.
            if (playbackState == Player.STATE_ENDED && sleepTimer.shouldStopAtTrackEnd()) {
                sleepTimer.cancel()
            }
        }

        /**
         * Erreur de lecture (fichier supprimé, corrompu, radio hors ligne).
         *
         * Sans traitement, ExoPlayer s'arrêtait net sur le morceau fautif : un seul fichier
         * illisible au milieu d'un album ou d'une lecture aléatoire coupait la musique.
         * On passe au suivant, avec une limite pour ne pas boucler sur une file entièrement
         * illisible.
         */
        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.w(TAG, "Lecture impossible : ${error.errorCodeName}", error)
            val p = activePlayer
            if (p.mediaItemCount == 0) return
            when (errorPolicy.onError(p.mediaItemCount, p.hasNextMediaItem())) {
                PlaybackErrorPolicy.Action.SKIP -> {
                    p.seekToNextMediaItem()
                    p.prepare()
                }
                // Trop d'échecs d'affilée : on s'arrête net au lieu de faire défiler toute
                // la file. Le bouton lecture réessaiera à la demande de l'utilisateur.
                PlaybackErrorPolicy.Action.STOP -> p.playWhenReady = false
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                errorPolicy.onUserAction()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Le réveil s'arrête à la première pause : on rend le flux musique et le gain.
            if (alarmActive) {
                if (isPlaying) alarmHasPlayed = true else if (alarmHasPlayed) exitAlarmMode()
            }
            if (isPlaying) {
                // Pas de remise à zéro du compteur d'erreurs ici : un titre qui démarre puis
                // échoue aussitôt relançait sinon le saut automatique à l'infini.
                inactivityJob?.cancel()
                // Lecture relancée après un arrêt demandé : le service redevient démarré
                // (notification au premier plan), le contrôleur n'a plus à le lâcher.
                _stopRequested.value = false
                applyAutoRewind()
                lastResumeUptime = SystemClock.elapsedRealtime()
                if (trackedSongId < 0) startTracking(activePlayer.currentMediaItem)
                announceIfPlaying()
                startPositionSaver()
            } else {
                accumulate()
                persistPodcastProgress()
                pausedAtUptime = SystemClock.elapsedRealtime()
                pausedMediaId = activePlayer.currentMediaItem?.mediaId
                scheduleInactivityStop()
            }

            // Persisté dans les deux sens, et non à la seule pause.
            //
            // C'est ici que `isPlaying` est écrit, et le widget s'en sert pour choisir
            // entre l'icône lecture et l'icône pause. En ne persistant qu'à la pause, la
            // valeur ne repassait jamais à vrai : appuyer sur lecture depuis le widget
            // démarrait bien la musique, mais le bouton restait « lecture ».
            persistQueue()

            // Le statut doit suivre la pause, sinon Discord continue d'afficher une
            // progression qui avance alors que la lecture est arrêtée.
            publishPresence(null)
            updatePartyServerState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                fade.resetVolume()
                // Une recherche manuelle est un point de reprise que l'on veut retrouver.
                if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) persistQueueSoon()
            }
            updatePartyServerState()
        }
    }

    /**
     * « Retour en arrière à la reprise » : après une vraie pause sur le même morceau, on
     * recule de quelques secondes pour retrouver le fil (livres audio, podcasts).
     */
    private fun applyAutoRewind() {
        val seconds = latestSettings?.autoRewindSec ?: 0
        val pausedFor = SystemClock.elapsedRealtime() - pausedAtUptime
        val sameTrack = pausedMediaId != null && pausedMediaId == activePlayer.currentMediaItem?.mediaId
        if (seconds <= 0 || pausedAtUptime == 0L || !sameTrack || pausedFor < AUTO_REWIND_MIN_PAUSE_MS) return
        pausedAtUptime = 0L
        activePlayer.seekTo((activePlayer.currentPosition - seconds * 1_000L).coerceAtLeast(0L))
    }

    /** « Arrêter après inactivité » : libère le service si la pause dure trop longtemps. */
    private fun scheduleInactivityStop() {
        inactivityJob?.cancel()
        val minutes = latestSettings?.stopAfterInactivityMin ?: 0
        if (minutes <= 0) return
        inactivityJob = serviceScope.launch {
            delay(minutes * 60_000L)
            if (!activePlayer.isPlaying) {
                persistQueue()
                // stop() passe le lecteur au repos, ce qui retire la notification et libère
                // le décodeur, même si l'écran affiché garde le service en vie.
                activePlayer.stop()
                requestStop()
            }
        }
    }

    /**
     * stopSelf, en prévenant le contrôleur de l'app.
     *
     * Tant qu'un contrôleur reste lié, stopSelf ne détruit pas le service. Celui de l'app
     * vivait aussi longtemps que le processus : le service survivait à l'app balayée comme
     * à l'arrêt après inactivité. [stopRequested] lui dit de se libérer une fois l'écran
     * quitté.
     */
    private fun requestStop() {
        _stopRequested.value = true
        stopSelf()
    }

    /** Regroupe les rafales (ajouts multiples, glissements de curseur) en une écriture. */
    private fun persistQueueSoon() {
        if (restoring) return
        persistSoonJob?.cancel()
        persistSoonJob = serviceScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistQueue()
        }
    }

    /**
     * Fige le morceau courant en tête de l'ordre aléatoire.
     *
     * L'ordre aléatoire par défaut d'ExoPlayer ne place pas l'index de départ en premier :
     * activer l'aléatoire (ou « tout lire au hasard ») sautait alors tous les titres
     * tirés avant lui, soit en moyenne la moitié de la sélection.
     */
    private fun anchorShuffleOrderOnCurrent() {
        if (activePlayer !== player) return
        val count = player.mediaItemCount
        if (count < 2) return
        val current = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val rest = (0 until count).filter { it != current }.shuffled()
        val order = IntArray(count)
        order[0] = current
        rest.forEachIndexed { i, index -> order[i + 1] = index }
        player.setShuffleOrder(DefaultShuffleOrder(order, Random.nextLong()))
    }

    /** Vrai s'il reste au plus un morceau à jouer, dans l'ordre réel (aléatoire compris). */
    private fun isNearEndOfPlaybackOrder(): Boolean {
        val p = activePlayer
        val next = p.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return true
        val afterNext = p.currentTimeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
        return afterNext == C.INDEX_UNSET
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
        // À l'enregistrement, Android rejoue « ajouté » pour chaque sortie déjà branchée.
        // Ces sorties-là ne sont pas des connexions : sans ce filtre, chaque démarrage du
        // service avec un casque branché réécrivait la courbe d'égaliseur choisie à la main.
        val alreadyConnected = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }.toMutableSet()
        }.getOrDefault(mutableSetOf())

        headsetCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                removedDevices.orEmpty().forEach { alreadyConnected.remove(it.id) }
            }

            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val headset = addedDevices.orEmpty()
                    .filterNot { alreadyConnected.remove(it.id) }
                    .firstOrNull { it.type in AudioOutputs.HEADSET_TYPES }
                    ?: return

                // Chaque casque peut avoir sa propre correction : on l'applique dès
                // qu'il se connecte, avant même de reprendre la lecture.
                applyProfileForDevice(headset.productName?.toString().orEmpty())

                val isBuds = fr.synxio.player.buds.GalaxyBuds.detect(headset.productName?.toString()) != null
                serviceScope.launch {
                    val s = settingsRepository.settings.first()
                    // La source au branchement est propre aux Galaxy Buds : une enceinte de
                    // voiture ou un casque filaire ne lancent jamais de musique d'eux-mêmes.
                    val source = if (isBuds) s.budsConnectSource else fr.synxio.player.buds.BudsConnectSource.NONE
                    if (connectAction(s.resumeOnHeadsetConnect, source) == fr.synxio.player.buds.BudsConnect.Action.NOTHING) return@launch

                    // Court délai : la route audio n'est pas encore basculée sur le
                    // casque à l'instant où le périphérique est signalé.
                    delay(600)
                    // L'utilisateur a pu lancer la lecture pendant ce délai.
                    when (connectAction(s.resumeOnHeadsetConnect, source)) {
                        fr.synxio.player.buds.BudsConnect.Action.NOTHING -> Unit
                        fr.synxio.player.buds.BudsConnect.Action.RESUME -> {
                            if (activePlayer.playbackState == Player.STATE_IDLE) activePlayer.prepare()
                            activePlayer.play()
                        }
                        fr.synxio.player.buds.BudsConnect.Action.START_SOURCE -> startConnectSource(source)
                    }
                }
            }
        }

        runCatching { audioManager.registerAudioDeviceCallback(headsetCallback, null) }
            .onFailure { headsetCallback = null }
    }

    /** Au branchement : reprendre, lancer une source, ou rien (pendant un appel, une lecture). */
    private fun connectAction(
        resumeEnabled: Boolean,
        source: fr.synxio.player.buds.BudsConnectSource,
    ): fr.synxio.player.buds.BudsConnect.Action {
        val mode = getSystemService(AudioManager::class.java)?.mode ?: AudioManager.MODE_NORMAL
        return fr.synxio.player.buds.BudsConnect.decide(
            resumeEnabled = resumeEnabled,
            source = source,
            hasQueue = activePlayer.mediaItemCount > 0,
            playWhenReady = activePlayer.playWhenReady,
            inCall = mode != AudioManager.MODE_NORMAL,
        )
    }

    /**
     * Lance la source choisie pour la connexion des Buds. Une source vide (aucun favori,
     * mix pas encore calculé) ne lance rien : mieux vaut le silence qu'une surprise.
     */
    private suspend fun startConnectSource(source: fr.synxio.player.buds.BudsConnectSource) {
        val library = musicRepository.library.value.songs
        if (library.isEmpty()) return
        val songs = when (source) {
            fr.synxio.player.buds.BudsConnectSource.NONE -> emptyList()
            fr.synxio.player.buds.BudsConnectSource.DAILY_MIX -> withTimeoutOrNull(CONNECT_SOURCE_WAIT_MS) {
                dailyMixes.mixes.first { it.isNotEmpty() }
            }?.firstOrNull { it.kind == fr.synxio.player.data.model.DailyMixKind.DAILY }?.songs.orEmpty()
            fr.synxio.player.buds.BudsConnectSource.FAVORITES -> {
                val ids = musicRepository.favoriteIds.value
                library.filter { it.id in ids }.shuffled()
            }
            fr.synxio.player.buds.BudsConnectSource.SHUFFLE ->
                musicRepository.withoutDisliked(library).shuffled().take(CONNECT_SHUFFLE_SIZE)
        }
        if (songs.isEmpty() || activePlayer.playWhenReady) return
        queueLoaded = true
        activePlayer.setMediaItems(forActivePlayer(songs.toMediaItems()), 0, 0L)
        activePlayer.prepare()
        activePlayer.play()
    }

    /**
     * Galaxy Buds : suivis pour les gestes, et pour prévenir quand leur batterie passe
     * sous 15 puis 5 %. Chaque nouvelle paire repart de sa première mesure.
     */
    private fun observeBuds() {
        var lastName: String? = null
        var lastBattery: Int? = null
        budsMonitor.state
            .onEach { buds ->
                latestBuds = buds
                val battery = buds?.batteryPercent
                val crossed = if (buds?.name == lastName) fr.synxio.player.buds.BudsAlerts.crossed(lastBattery, battery) else null
                lastName = buds?.name
                lastBattery = battery
                if (crossed != null && buds != null && latestSettings?.budsBatteryAlert != false) {
                    notifyBudsBattery(buds.model.displayName, battery ?: crossed)
                }
            }
            .launchIn(serviceScope)
    }

    private fun notifyBudsBattery(model: String, percent: Int) {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        if (manager.getNotificationChannel(BUDS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(BUDS_CHANNEL_ID, "Écouteurs", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Batterie faible des Galaxy Buds" }
            )
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, BUDS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle("$model : batterie à $percent %")
            .setContentText(if (percent <= 5) "Remets-les dans leur boîtier, la lecture va bientôt s'arrêter" else "Pense à les recharger")
            .setContentIntent(sessionActivityIntent())
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(BUDS_NOTIFICATION_ID, notification) }
    }

    /**
     * Toucher des Buds remappé dans les réglages. Les touches suivant / précédent ne
     * sont détournées que si des Galaxy Buds sont la sortie connue : un volant de voiture
     * garde son comportement.
     */
    private fun budsTapFor(event: KeyEvent): fr.synxio.player.buds.BudsTapAction? {
        val s = latestSettings ?: return null
        return fr.synxio.player.buds.BudsGestures.remap(
            keyCode = event.keyCode,
            onBuds = latestBuds != null,
            doubleTap = s.budsDoubleTap,
            tripleTap = s.budsTripleTap,
        )
    }

    private fun runBudsTap(action: fr.synxio.player.buds.BudsTapAction) {
        val p = activePlayer
        if (p.mediaItemCount == 0) return
        when (action) {
            fr.synxio.player.buds.BudsTapAction.DEFAULT -> Unit
            fr.synxio.player.buds.BudsTapAction.FAVORITE -> {
                val song = musicRepository.songById(p.currentMediaItem?.songId ?: -1L)
                if (song != null) serviceScope.launch { musicRepository.toggleFavorite(song) }
            }
            fr.synxio.player.buds.BudsTapAction.SEEK_FORWARD -> runButtonCommand(MediaButtonInterpreter.Command.SeekBy(10_000))
            fr.synxio.player.buds.BudsTapAction.SEEK_BACK -> runButtonCommand(MediaButtonInterpreter.Command.SeekBy(-10_000))
            fr.synxio.player.buds.BudsTapAction.RESTART -> if (p.isCurrentMediaItemSeekable) p.seekTo(0L)
            fr.synxio.player.buds.BudsTapAction.SHUFFLE -> p.shuffleModeEnabled = !p.shuffleModeEnabled
        }
    }

    /**
     * Applique la courbe mémorisée pour ce périphérique, ou celle du modèle pour des Galaxy
     * Buds sans profil (réglage « Correction auto »).
     */
    private fun applyProfileForDevice(deviceName: String) {
        if (deviceName.isBlank()) return
        serviceScope.launch {
            val curveId = deviceProfileDao.forDevice(deviceName)?.curveId
                ?: fr.synxio.player.buds.GalaxyBuds.detect(deviceName)?.curveId
                    ?.takeIf { settingsRepository.settings.first().budsAutoEq }
                ?: return@launch
            val curve = EqCurves.byId(curveId) ?: return@launch
            val caps = equalizer.capabilities
            if (!caps.available || caps.bands.isEmpty()) return@launch

            val levels = curve.toBandLevels(
                bandFrequenciesHz = caps.bands.map { it.centerFreqHz },
                minMb = caps.minLevel,
                maxMb = caps.maxLevel,
            )
            equalizer.setEnabled(true)
            equalizer.setBandLevels(levels)
            // Les bandes sont enregistrées aussi : sinon observeSettings réappliquait
            // aussitôt les anciennes et la courbe du casque était écrasée.
            settingsRepository.setEqualizerBands(levels)
            settingsRepository.setEqualizerEnabled(true)
            settingsRepository.setEqualizerCurve(curve.id)
        }
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
        equalizer.setReverbPreset(s.reverbPreset.toShort())
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
                    equalizer.setReverbPreset(s.reverbPreset.toShort())
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
            // Les commandes maison (favori, minuterie) ne sont offertes qu'à l'app elle-même
            // et aux contrôleurs de confiance (notification, système, Android Auto). Le
            // service est exporté : n'importe quelle app installée pouvait sinon modifier
            // les favoris.
            val trusted = controller.packageName == packageName || controller.isTrusted ||
                session.isMediaNotificationController(controller) ||
                session.isAutoCompanionController(controller) ||
                session.isAutomotiveController(controller)
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .apply {
                    if (trusted) {
                        add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                        add(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                        add(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
                        add(SessionCommand(CMD_SLEEP_TIMER, Bundle.EMPTY))
                        add(SessionCommand(CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                        add(SessionCommand(CMD_ALARM_MODE, Bundle.EMPTY))
                    }
                }
                .build()

            return MediaSession.ConnectionResult.accept(
                commands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            // Plus aucun navigateur : rien à prévenir des changements de bibliothèque.
            val browsers = session.connectedControllers.count { it.packageName != packageName }
            if (browsers == 0) {
                subscribedParents.clear()
                lastSearch = null
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_TOGGLE_FAVORITE -> {
                    val song = musicRepository.songById(activePlayer.currentMediaItem?.songId ?: -1L)
                    if (song != null) serviceScope.launch { musicRepository.toggleFavorite(song) }
                }

                CMD_TOGGLE_SHUFFLE -> activePlayer.shuffleModeEnabled = !activePlayer.shuffleModeEnabled

                CMD_CYCLE_REPEAT -> activePlayer.repeatMode = when (activePlayer.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }

                CMD_SLEEP_TIMER -> sleepTimer.start(
                    durationMs = args.getLong(ARG_DURATION_MS, 30 * 60_000L),
                    finishCurrentTrack = args.getBoolean(ARG_FINISH_TRACK, false),
                )

                CMD_CANCEL_SLEEP_TIMER -> sleepTimer.cancel()

                CMD_ALARM_MODE -> enterAlarmMode(args.getInt(ARG_RAMP_SECONDS, 0))

                else -> return Futures.immediateFuture(
                    SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Styles par défaut de l'hôte ; la recherche est annoncée par Media3 lui-même,
            // puisque la commande de recherche est accordée.
            val rootParams = LibraryParams.Builder()
                .setOffline(params?.isOffline ?: false)
                .setRecent(params?.isRecent ?: false)
                .setSuggested(params?.isSuggested ?: false)
                .setExtras(libraryTree.rootExtras())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(libraryTree.rootItem, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            fun result() = LibraryResult.ofItemList(
                ImmutableList.copyOf(libraryTree.children(parentId, page, pageSize.coerceAtLeast(1))),
                params,
            )
            // Au démarrage à froid depuis la voiture, le scan n'est pas fini : on l'attend
            // un peu plutôt que d'afficher une bibliothèque vide.
            if (musicRepository.library.value.hasScanned || parentId == BrowseIds.ROOT) {
                return Futures.immediateFuture(result())
            }
            val future = com.google.common.util.concurrent.SettableFuture
                .create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                withTimeoutOrNull(CHILDREN_SCAN_WAIT_MS) { musicRepository.library.first { it.hasScanned } }
                future.set(result())
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = libraryTree.itemById(mediaId)
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
            )
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            subscribedParents += parentId
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onUnsubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
        ): ListenableFuture<LibraryResult<Void>> {
            subscribedParents -= parentId
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        /** Recherche tapée ou dictée depuis l'écran de la voiture. */
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val results = searchResults(query)
            session.notifySearchResultChanged(browser, query, results.size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val results = searchResults(query)
            val size = pageSize.coerceAtLeast(1)
            val from = (page.toLong() * size).coerceAtMost(results.size.toLong()).toInt()
            val to = (from.toLong() + size).coerceAtMost(results.size.toLong()).toInt()
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(results.subList(from, to)), params)
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
            val resolved = mediaItems.flatMap { item ->
                when {
                    item.localConfiguration != null -> listOf(item)
                    item.mediaId.startsWith(BrowseIds.RADIO_PREFIX) -> listOfNotNull(libraryTree.radioItem(item.mediaId))
                    // Ajout à la file depuis la voiture : un titre en contexte reste un titre.
                    else -> {
                        val songId = BrowseIds.parseSongInContext(item.mediaId)?.second ?: item.songId
                        listOfNotNull(musicRepository.songById(songId)?.toMediaItem())
                    }
                }
            }
            return Futures.immediateFuture(forActivePlayer(resolved).toMutableList())
        }

        /** « Ok Google, joue du Nekfeu » et les choix faits dans l'arborescence de la voiture. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val first = mediaItems.firstOrNull()

            // Recherche vocale : la requête peut être vide (« joue de la musique »).
            val request = first?.requestMetadata
            if (mediaItems.size == 1 && request?.searchQuery != null) {
                val queue = libraryTree.resolveVoice(request.toVoiceRequest())
                    ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Aucun titre pour cette demande"))
                return Futures.immediateFuture(queue.toStartPosition())
            }

            if (mediaItems.size == 1 && first != null && first.localConfiguration == null) {
                val mediaId = first.mediaId
                if (mediaId.startsWith(BrowseIds.RADIO_PREFIX)) {
                    val radio = libraryTree.radioItem(mediaId)
                        ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Radio introuvable"))
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(forActivePlayer(listOf(radio)), 0, C.TIME_UNSET)
                    )
                }
                // Titre choisi dans un album, une playlist, une recherche, ou « lecture aléatoire ».
                libraryTree.queueFor(mediaId)?.let { queue ->
                    return Futures.immediateFuture(queue.toStartPosition())
                }
            }

            if (mediaItems.size == 1 && controller.packageName != packageName) {
                val songId = first?.mediaId?.toLongOrNull()
                if (songId != null) {
                    val allSongs = musicRepository.library.value.songs
                    val index = allSongs.indexOfFirst { it.id == songId }
                    if (index != -1) {
                        return Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(forActivePlayer(allSongs.toMediaItems()), index, startPositionMs)
                        )
                    }
                }
            }

            return super<MediaLibrarySession.Callback>.onSetMediaItems(
                mediaSession, controller, mediaItems, startIndex, startPositionMs
            )
        }

        /**
         * Boutons du casque, filaire ou Bluetooth : double et triple appui, appui long.
         *
         * La notification envoie ses propres touches (lecture, pause) : elles suivent le
         * traitement par défaut, comme les touches que l'interprète ne connaît pas.
         */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            if (session.isMediaNotificationController(controllerInfo)) return false
            val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                ?: return false
            budsTapFor(event)?.let { action ->
                // L'appui seul déclenche ; relâchement et répétitions sont absorbés.
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) runBudsTap(action)
                return true
            }
            val outcome = mediaButtons.onKeyEvent(event.keyCode, event.action, event.repeatCount, SystemClock.uptimeMillis())
            if (!outcome.handled) return false
            if (outcome.cancelPending || outcome.resolveAt != null) buttonHandler.removeCallbacks(resolveButtons)
            outcome.resolveAt?.let { buttonHandler.postAtTime(resolveButtons, it) }
            outcome.command?.let(::runButtonCommand)
            return true
        }

        /**
         * Reprise depuis un bouton Bluetooth ou la notification « reprendre » d'Android 13+ :
         * on repropose la file d'attente sauvegardée.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = com.google.common.util.concurrent.SettableFuture
                .create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                // Même règle que la restauration au démarrage : « Mémoriser la file »
                // désactivé, rien à reprendre.
                val remember = settingsRepository.settings.first().rememberQueue
                val restored = if (remember) runCatching { loadSavedQueue() }.getOrNull() else null
                if (restored == null) {
                    future.setException(UnsupportedOperationException("Aucune file sauvegardée"))
                } else {
                    val (queue, state) = restored
                    // Aléatoire et répétition sont des propriétés du lecteur, conservées par
                    // setMediaItems : posées avant, elles survivent à la reprise au lieu
                    // d'être réécrites à « désactivé » à la sauvegarde suivante.
                    activePlayer.shuffleModeEnabled = state?.shuffle ?: false
                    activePlayer.repeatMode = state?.repeatMode ?: Player.REPEAT_MODE_OFF
                    queueLoaded = true
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            forActivePlayer(queue.items),
                            queue.index,
                            queue.positionMs,
                        )
                    )
                }
            }
            return future
        }
    }

    /** Résultats de recherche, gardés pour la requête en cours : l'hôte les demande page par page. */
    private fun searchResults(query: String): List<MediaItem> {
        lastSearch?.takeIf { it.first == query }?.let { return it.second }
        val results = libraryTree.search(query)
        lastSearch = query to results
        return results
    }

    private fun BrowseQueue.toStartPosition() = MediaSession.MediaItemsWithStartPosition(
        forActivePlayer(songs.toMediaItems()),
        startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
        0L,
    )

    /** Précisions de l'Assistant (`MediaStore.EXTRA_MEDIA_*`) jointes à la requête vocale. */
    private fun MediaItem.RequestMetadata.toVoiceRequest(): VoiceRequest {
        val extras = extras
        return VoiceRequest(
            query = searchQuery.orEmpty(),
            focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS),
            artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST),
            album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM),
            title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE),
            genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE),
            // MediaStore.EXTRA_MEDIA_PLAYLIST, dépréciée mais toujours envoyée par l'Assistant.
            playlist = extras?.getString("android.intent.extra.playlist"),
        )
    }

    /** Exécute un geste de casque tranché par [MediaButtonInterpreter]. */
    private fun runButtonCommand(command: MediaButtonInterpreter.Command) {
        val p = activePlayer
        when (command) {
            MediaButtonInterpreter.Command.PlayPause -> {
                if (p.mediaItemCount == 0) resumeSavedQueueAndPlay()
                else Util.handlePlayPauseButtonAction(p)
            }
            MediaButtonInterpreter.Command.Next -> if (p.mediaItemCount > 0) p.seekToNext()
            MediaButtonInterpreter.Command.Previous -> if (p.mediaItemCount > 0) p.seekToPrevious()
            is MediaButtonInterpreter.Command.SeekBy -> {
                if (!p.isCurrentMediaItemSeekable) return
                val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
                p.seekTo((p.currentPosition + command.deltaMs).coerceIn(0L, duration))
            }
        }
    }

    /** Appui sur lecture alors que rien n'est chargé : on reprend la file sauvegardée. */
    private fun resumeSavedQueueAndPlay() {
        serviceScope.launch {
            if (activePlayer.mediaItemCount == 0) restoreQueueOnce()
            val p = activePlayer
            if (p.mediaItemCount == 0) return@launch
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }

    /**
     * Boutons personnalisés de la notification, de l'écran de verrouillage et d'Android
     * Auto : favori (titres de la bibliothèque seulement), aléatoire, répétition.
     */
    private fun customLayout(state: LayoutState): ImmutableList<CommandButton> {
        val buttons = ImmutableList.builder<CommandButton>()
        state.favorite?.let { buttons.add(favoriteButton(it)) }
        buttons.add(
            CommandButton.Builder(if (state.shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(if (state.shuffle) "Désactiver la lecture aléatoire" else "Lecture aléatoire")
                .setCustomIconResId(if (state.shuffle) R.drawable.ic_widget_shuffle else R.drawable.ic_action_shuffle_off)
                .setSessionCommand(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()
        )
        val (repeatIcon, repeatRes, repeatLabel) = when (state.repeatMode) {
            Player.REPEAT_MODE_ALL -> Triple(CommandButton.ICON_REPEAT_ALL, R.drawable.ic_widget_repeat, "Répéter le titre")
            Player.REPEAT_MODE_ONE -> Triple(CommandButton.ICON_REPEAT_ONE, R.drawable.ic_widget_repeat_one, "Désactiver la répétition")
            else -> Triple(CommandButton.ICON_REPEAT_OFF, R.drawable.ic_action_repeat_off, "Tout répéter")
        }
        buttons.add(
            CommandButton.Builder(repeatIcon)
                .setDisplayName(repeatLabel)
                .setCustomIconResId(repeatRes)
                .setSessionCommand(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
                .build()
        )
        return buttons.build()
    }

    private fun favoriteButton(isFavorite: Boolean) =
        CommandButton.Builder(if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris")
            .setCustomIconResId(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
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
        const val CMD_TOGGLE_SHUFFLE = "fr.synxio.player.TOGGLE_SHUFFLE"
        const val CMD_CYCLE_REPEAT = "fr.synxio.player.CYCLE_REPEAT"
        const val CMD_ALARM_MODE = "fr.synxio.player.ALARM_MODE"
        const val ARG_RAMP_SECONDS = "ramp_seconds"

        /** Attributs de la musique ; [spatial] laisse Android la spatialiser (audio 360). */
        private fun mediaAttributes(spatial: Boolean) = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .setSpatializationBehavior(if (spatial) C.SPATIALIZATION_BEHAVIOR_AUTO else C.SPATIALIZATION_BEHAVIOR_NEVER)
            .build()

        private const val BUDS_CHANNEL_ID = "buds"
        private const val BUDS_NOTIFICATION_ID = 4_201
        private const val CONNECT_SOURCE_WAIT_MS = 3_000L
        private const val CONNECT_SHUFFLE_SIZE = 100
        private val ALARM_ATTRIBUTES = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_ALARM)
            .build()
        private const val ALARM_START_GAIN = 0.05f
        private const val ALARM_RAMP_TICK_MS = 250L
        const val ARG_DURATION_MS = "duration_ms"
        const val ARG_FINISH_TRACK = "finish_track"

        /** Laisse la persistance écrire, et absorbe les appels groupés. */
        private const val WIDGET_REFRESH_DELAY_MS = 250L

        /** Vrai tant que le service existe dans ce processus (widgets : état « lecture » périmé). */
        @Volatile
        var isRunning: Boolean = false
            private set

        private val _stopRequested = MutableStateFlow(false)

        /** Le service a demandé son arrêt et attend que le contrôleur de l'app se libère. */
        val stopRequested: StateFlow<Boolean> = _stopRequested.asStateFlow()

        private const val TAG = "PlaybackService"
        private const val POSITION_SAVE_INTERVAL_MS = 10_000L
        private const val PERSIST_DEBOUNCE_MS = 500L
        private const val FINAL_WRITE_TIMEOUT_MS = 1_500L
        private const val AUTO_REWIND_MIN_PAUSE_MS = 5_000L
        private const val BROWSE_NOTIFY_DEBOUNCE_MS = 1_500L
        private const val CHILDREN_SCAN_WAIT_MS = 4_000L
    }
}
