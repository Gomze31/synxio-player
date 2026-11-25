package fr.synxio.player.playback.engine

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.ShuffleOrder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Lecteur à deux platines pour le fondu enchaîné réel.
 *
 * C'est le seul lecteur que voient la `MediaLibrarySession`, les contrôleurs et le
 * service. Il possède deux [ExoPlayer] identiques : l'**actif** fournit l'état (position,
 * durée, erreurs, métadonnées, timeline avec son ordre aléatoire) ; la **réserve** dort,
 * puis se positionne sur le titre suivant quelques secondes avant la fin, démarre pendant
 * le chevauchement et devient l'actif à la bascule. Toute mutation de playlist est
 * appliquée aux deux, pour que chacun puisse aussi enchaîner nativement : à `fadeMs = 0`
 * la réserve ne sert jamais et le gapless d'ExoPlayer reste intact.
 *
 * Les décisions viennent de [DualDeckScheduler] (pur), les gains de [CrossfadeEnvelope].
 * Le volume de chaque deck vaut `enveloppe × normalisation(titre) × veille × plafond ×
 * alarme × volume × focus` : ce lecteur est le seul à écrire `deck.volume`.
 *
 * Les événements des decks sont relayés par un `post` : un `invalidateState()` synchrone
 * depuis un `handleXxx` ferait inférer à `SimpleBasePlayer` un faux type de discontinuité
 * (un « suivant » passerait pour un changement de file et remélangerait l'aléatoire).
 */
class CrossfadePlayer(
    looper: Looper,
    decks: List<Deck>,
    private val focus: AudioFocusGate?,
) : SimpleBasePlayer(looper) {

    /** Un ExoPlayer et sa porte vers le pouls (null si la scène réactive n'écoute pas ce deck). */
    class Deck(val player: ExoPlayer, val pulseGate: GatedBufferSink?)

    /** Gains multipliés à l'enveloppe, appliqués aux deux decks. */
    inner class Gains internal constructor() {
        /** Gain du minuteur de sommeil (fondu final de 60 s). */
        var sleep: Float = 1f
            set(value) { field = value.coerceIn(0f, 1f); applyVolumes() }

        /** Plafond « Limiter le volume », 1 = aucun. */
        var volumeCap: Float = 1f
            set(value) { field = value.coerceIn(0f, 1f); applyVolumes() }

        /** Montée du réveil en musique. */
        var alarm: Float = 1f
            set(value) { field = value.coerceIn(0f, 1f); applyVolumes() }

        /** Gain de normalisation d'un titre, entre 0 et 1 ; évalué pour chaque deck sur son titre courant. */
        var normalizationFor: (MediaItem?) -> Float = { 1f }
            set(value) { field = value; applyVolumes() }

        /** À appeler quand une mesure de niveau arrive ou que le réglage change. */
        fun refresh() = applyVolumes()
    }

    val gains = Gains()

    /** Durée du fondu, 0 = désactivé (gapless natif). */
    var fadeMs: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, MAX_FADE_MS)
            if (field == clamped) return
            field = clamped
            if (clamped == 0) applyDecision(scheduler.queueChanged())
            scheduleTick()
        }

    var curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER

    /** « Caler sur l'énergie » : fin du fondu à `outroMs`, départ du suivant à `introMs`. */
    var energyAligned: Boolean = false

    /** Bornes réelles du son d'un titre, null s'il n'est pas analysé. */
    var traitsFor: (MediaItem) -> DualDeckScheduler.Traits? = { null }

    private val decks: List<Deck> = decks.also { require(it.size == 2) { "deux decks attendus" } }
    private val handler = Handler(looper)
    private val scheduler = DualDeckScheduler()

    private var activeIndex = 0
    private val active: Deck get() = decks[activeIndex]
    private val reserve: Deck get() = decks[1 - activeIndex]
    private val envelope = floatArrayOf(1f, 0f)
    private var pauseAtEndApplied = false
    private var overlapStarted = false

    /** Uids stables, un par élément de la playlist maîtresse. */
    private val uids = ArrayList<Any>()

    private var playWhenReady = false
    private var playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var suppressedByFocus = false
    private var focusMultiplier = 1f
    private var userVolume = 1f

    private var pendingDiscontinuity: Pair<Int, Long>? = null
    private var invalidatePosted = false
    private var tickPosted = false
    private var trimScheduled = false
    private var released = false

    private val tickRunnable = Runnable { tickPosted = false; tick() }
    private val trimRunnable = Runnable { trimScheduled = false; trimReserve() }
    private val invalidateRunnable = Runnable {
        invalidatePosted = false
        if (!released) invalidateState()
    }

    private val focusListener = object : AudioFocusGate.Listener {
        override fun onFocusLost() {
            suppressedByFocus = false
            playWhenReady = false
            playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
            applyDecision(scheduler.pause())
            syncDecksPlayWhenReady()
            postInvalidate()
        }

        override fun onFocusSuppressed(suppressed: Boolean) {
            suppressedByFocus = suppressed
            if (suppressed) applyDecision(scheduler.pause())
            syncDecksPlayWhenReady()
            postInvalidate()
        }

        override fun onFocusVolumeMultiplier(multiplier: Float) {
            focusMultiplier = multiplier
            applyVolumes()
        }
    }

    init {
        decks.forEachIndexed { index, deck ->
            deck.player.addListener(DeckListener(index))
            deck.player.playWhenReady = false
        }
        decks[0].pulseGate?.open = true
        focus?.listener = focusListener
        applyVolumes()
    }

    // --- Entrées ExoPlayer non couvertes par Player ----------------------------------

    fun setShuffleOrder(order: ShuffleOrder) = eachDeck { it.setShuffleOrder(order) }

    fun setSkipSilenceEnabled(enabled: Boolean) = eachDeck { it.skipSilenceEnabled = enabled }

    /** Même session audio sur les deux decks : les effets s'appliquent à toute `AudioTrack` qui la porte. */
    fun setAudioSessionId(sessionId: Int) {
        eachDeck { it.audioSessionId = sessionId }
        postInvalidate()
    }

    fun addDeckAnalyticsListener(listener: AnalyticsListener) = eachDeck { it.addAnalyticsListener(listener) }

    /** Libère les décodeurs de la réserve si elle n'est pas engagée (`onTrimMemory`, 30 s d'inactivité). */
    fun trimReserve() {
        cancelTrim()
        if (released || scheduler.phase != DualDeckScheduler.Phase.IDLE) return
        val r = reserve.player
        if (r.playbackState != Player.STATE_IDLE) r.stop()
    }

    private fun eachDeck(block: (ExoPlayer) -> Unit) = decks.forEach { block(it.player) }

    // --- État exposé ----------------------------------------------------------------

    override fun getState(): State {
        val a = active.player
        val timeline = a.currentTimeline
        if (uids.size != timeline.windowCount) {
            uids.clear()
            repeat(timeline.windowCount) { uids += Any() }
        }
        val playbackState = a.playbackState
        val builder = State.Builder()
            .setAvailableCommands(availableCommandsOf(a))
            .setPlayWhenReady(playWhenReady, playWhenReadyReason)
            .setPlaybackState(playbackState)
            .setPlaybackSuppressionReason(
                if (suppressedByFocus) Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                else a.playbackSuppressionReason
            )
            .setPlayerError(a.playerError)
            .setRepeatMode(a.repeatMode)
            .setShuffleModeEnabled(a.shuffleModeEnabled)
            .setIsLoading(a.isLoading && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY))
            .setSeekBackIncrementMs(a.seekBackIncrement)
            .setSeekForwardIncrementMs(a.seekForwardIncrement)
            .setMaxSeekToPreviousPositionMs(a.maxSeekToPreviousPosition)
            .setPlaybackParameters(a.playbackParameters)
            .setTrackSelectionParameters(a.trackSelectionParameters)
            .setAudioAttributes(a.audioAttributes)
            .setAudioSessionId(a.audioSessionId)
            .setVolume(userVolume)
            .setDeviceInfo(a.deviceInfo)
            .setPlaylistMetadata(a.playlistMetadata)
        if (timeline.isEmpty) {
            builder.setPlaylist(timeline, a.currentTracks, a.mediaMetadata)
                .setCurrentMediaItemIndex(C.INDEX_UNSET)
                .setContentPositionMs(PositionSupplier.ZERO)
        } else {
            builder.setPlaylist(StableUidTimeline(timeline, uids.toList()), a.currentTracks, a.mediaMetadata)
                .setCurrentMediaItemIndex(a.currentMediaItemIndex.coerceIn(0, timeline.windowCount - 1))
                .setContentPositionMs(PositionSupplier { a.currentPosition.coerceAtLeast(0L) })
                .setContentBufferedPositionMs(PositionSupplier { a.bufferedPosition.coerceAtLeast(0L) })
                .setTotalBufferedDurationMs(PositionSupplier { a.totalBufferedDuration.coerceAtLeast(0L) })
        }
        pendingDiscontinuity?.let { (reason, positionMs) ->
            builder.setPositionDiscontinuity(reason, positionMs)
            pendingDiscontinuity = null
        }
        return builder.build()
    }

    private fun availableCommandsOf(player: ExoPlayer): Player.Commands =
        player.availableCommands.buildUpon()
            .removeAll(
                Player.COMMAND_SET_VIDEO_SURFACE,
                Player.COMMAND_SET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
            )
            .build()

    private fun postInvalidate() {
        if (invalidatePosted || released) return
        invalidatePosted = true
        handler.post(invalidateRunnable)
    }

    // --- Commandes -------------------------------------------------------------------

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        if (!playWhenReady) applyDecision(scheduler.pause())
        refreshFocus()
        syncDecksPlayWhenReady()
        scheduleTick()
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        active.player.prepare()
        refreshFocus()
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        applyDecision(scheduler.queueChanged())
        eachDeck { it.stop() }
        refreshFocus(Player.STATE_IDLE)
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        handler.removeCallbacksAndMessages(null)
        focus?.release()
        eachDeck { it.release() }
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        eachDeck { it.repeatMode = repeatMode }
        return done()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        eachDeck { it.shuffleModeEnabled = shuffleModeEnabled }
        return done()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        eachDeck { it.playbackParameters = playbackParameters }
        scheduleTick()
        return done()
    }

    override fun handleSetTrackSelectionParameters(trackSelectionParameters: TrackSelectionParameters): ListenableFuture<*> {
        eachDeck { it.trackSelectionParameters = trackSelectionParameters }
        return done()
    }

    override fun handleSetPlaylistMetadata(playlistMetadata: MediaMetadata): ListenableFuture<*> {
        eachDeck { it.playlistMetadata = playlistMetadata }
        return done()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        userVolume = volume.coerceIn(0f, 1f)
        applyVolumes()
        return done()
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> = handleSetVolume(volume)

    override fun handleSetAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean): ListenableFuture<*> {
        eachDeck { it.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false) }
        focus?.setAudioAttributes(audioAttributes)
        refreshFocus()
        return done()
    }

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        applyDecision(scheduler.queueChanged())
        val a = active.player
        if (startIndex == C.INDEX_UNSET) a.setMediaItems(mediaItems, /* resetPosition = */ true)
        else a.setMediaItems(mediaItems, startIndex, startPositionMs)
        reserve.player.setMediaItems(mediaItems)
        uids.clear()
        repeat(mediaItems.size) { uids += Any() }
        scheduleTick()
        return done()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        applyDecision(scheduler.queueChanged())
        val at = index.coerceIn(0, uids.size)
        eachDeck { it.addMediaItems(at, mediaItems) }
        uids.addAll(at, List(mediaItems.size) { Any() })
        scheduleTick()
        return done()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        applyDecision(scheduler.queueChanged())
        val from = fromIndex.coerceIn(0, uids.size)
        val to = toIndex.coerceIn(from, uids.size)
        eachDeck { it.moveMediaItems(from, to, newIndex) }
        val moved = ArrayList(uids.subList(from, to))
        uids.subList(from, to).clear()
        uids.addAll(newIndex.coerceIn(0, uids.size), moved)
        scheduleTick()
        return done()
    }

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        applyDecision(scheduler.queueChanged())
        val from = fromIndex.coerceIn(0, uids.size)
        val to = toIndex.coerceIn(from, uids.size)
        eachDeck { it.replaceMediaItems(from, to, mediaItems) }
        uids.subList(from, to).clear()
        uids.addAll(from, List(mediaItems.size) { Any() })
        scheduleTick()
        return done()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        applyDecision(scheduler.queueChanged())
        val from = fromIndex.coerceIn(0, uids.size)
        val to = toIndex.coerceIn(from, uids.size)
        eachDeck { it.removeMediaItems(from, to) }
        uids.subList(from, to).clear()
        scheduleTick()
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        applyDecision(scheduler.seek())
        val a = active.player
        if (mediaItemIndex == C.INDEX_UNSET) a.seekTo(positionMs) else a.seekTo(mediaItemIndex, positionMs)
        scheduleTick()
        return done()
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    // --- Decks -----------------------------------------------------------------------

    private fun effectivePlayWhenReady() = playWhenReady && !suppressedByFocus

    /**
     * Demande ou rend le focus selon l'intention de lecture et l'état du deck actif ; un
     * refus met en pause, comme le ferait ExoPlayer.
     */
    private fun refreshFocus(playbackState: Int = active.player.playbackState) {
        val gate = focus ?: return
        val allowed = gate.updateAudioFocus(playWhenReady, playbackState)
        if (playWhenReady && !allowed && !suppressedByFocus && playbackState != Player.STATE_IDLE) {
            playWhenReady = false
            playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
            syncDecksPlayWhenReady()
            postInvalidate()
        }
    }

    private fun syncDecksPlayWhenReady() {
        val effective = effectivePlayWhenReady()
        active.player.playWhenReady = effective
        reserve.player.playWhenReady = effective && overlapStarted
    }

    private fun applyVolumes() {
        val shared = gains.sleep * gains.volumeCap * gains.alarm * userVolume * focusMultiplier
        decks.forEachIndexed { index, deck ->
            val normalization = gains.normalizationFor(deck.player.currentMediaItem).coerceIn(0f, 1f)
            deck.player.volume = (envelope[index] * normalization * shared).coerceIn(0f, 1f)
        }
    }

    private fun syncPauseAtEnd() {
        val wanted = scheduler.phase != DualDeckScheduler.Phase.IDLE
        if (wanted == pauseAtEndApplied) return
        pauseAtEndApplied = wanted
        active.player.pauseAtEndOfMediaItems = wanted
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tickRunnable)
        tickPosted = false
        if (released || fadeMs <= 0) return
        tickPosted = true
        handler.post(tickRunnable)
    }

    private fun tick() {
        if (released) return
        val a = active.player
        if (fadeMs <= 0 || !a.isPlaying) {
            if (scheduler.phase != DualDeckScheduler.Phase.IDLE) applyDecision(scheduler.pause())
            return
        }
        val durationMs = a.duration
        val positionMs = a.currentPosition
        val timeline = a.currentTimeline
        val current = a.currentMediaItemIndex
        val nextIndex = if (timeline.isEmpty || a.isCurrentMediaItemLive) null
        else timeline.getNextWindowIndex(current, a.repeatMode, a.shuffleModeEnabled).takeIf { it != C.INDEX_UNSET }
        val config = DualDeckScheduler.Config(fadeMs, energyAligned)
        val currentTraits = a.currentMediaItem?.let(traitsFor)
        val nextTraits = nextIndex?.let { a.getMediaItemAt(it) }?.let(traitsFor)
        applyDecision(scheduler.tick(positionMs, durationMs, nextIndex, config, currentTraits, nextTraits))
        if (released) return
        val speed = a.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        val delay = (scheduler.nextTickDelayMs(positionMs, durationMs, config, currentTraits) / speed).toLong()
        tickPosted = true
        handler.postDelayed(tickRunnable, delay.coerceAtLeast(DualDeckScheduler.OVERLAP_TICK_MS))
    }

    private fun applyDecision(decision: DualDeckScheduler.Decision) {
        when (decision) {
            DualDeckScheduler.Decision.Idle -> Unit
            is DualDeckScheduler.Decision.Prepare -> prepareReserve(decision.nextIndex, decision.startMs)
            is DualDeckScheduler.Decision.Overlap -> overlap(decision.progress)
            DualDeckScheduler.Decision.Swap -> swap()
            DualDeckScheduler.Decision.Abort -> abort()
        }
        syncPauseAtEnd()
        if (scheduler.phase == DualDeckScheduler.Phase.IDLE) {
            if (overlapStarted) {
                // Sortie de chevauchement sans Abort explicite : la réserve se tait.
                overlapStarted = false
                reserve.player.playWhenReady = false
                setEnvelope(1f, 0f)
            }
            scheduleTrim()
        } else {
            cancelTrim()
        }
    }

    private fun prepareReserve(nextIndex: Int, startMs: Long) {
        val r = reserve.player
        overlapStarted = false
        r.playWhenReady = false
        setEnvelope(1f, 0f)
        if (nextIndex !in 0 until r.mediaItemCount) return
        r.seekTo(nextIndex, startMs)
        if (r.playbackState == Player.STATE_IDLE) r.prepare()
    }

    private fun overlap(progress: Float) {
        if (!overlapStarted) {
            overlapStarted = true
            reserve.player.playWhenReady = effectivePlayWhenReady()
        }
        setEnvelope(CrossfadeEnvelope.fadeOut(progress, curve), CrossfadeEnvelope.fadeIn(progress, curve))
    }

    private fun swap() {
        val old = active
        val new = reserve
        pendingDiscontinuity = Player.DISCONTINUITY_REASON_AUTO_TRANSITION to old.player.currentPosition.coerceAtLeast(0L)
        activeIndex = 1 - activeIndex
        overlapStarted = false
        if (pauseAtEndApplied) {
            old.player.pauseAtEndOfMediaItems = false
            pauseAtEndApplied = false
        }
        old.player.playWhenReady = false
        new.player.playWhenReady = effectivePlayWhenReady()
        old.pulseGate?.open = false
        new.pulseGate?.open = true
        setEnvelope(1f, 0f)
        scheduler.transition()
        postInvalidate()
    }

    private fun abort() {
        overlapStarted = false
        reserve.player.playWhenReady = false
        setEnvelope(1f, 0f)
    }

    /** Enveloppe de l'actif puis de la réserve. */
    private fun setEnvelope(activeGain: Float, reserveGain: Float) {
        envelope[activeIndex] = activeGain
        envelope[1 - activeIndex] = reserveGain
        applyVolumes()
    }

    private fun scheduleTrim() {
        if (trimScheduled || released || reserve.player.playbackState == Player.STATE_IDLE) return
        trimScheduled = true
        handler.postDelayed(trimRunnable, RESERVE_TRIM_MS)
    }

    private fun cancelTrim() {
        if (!trimScheduled) return
        trimScheduled = false
        handler.removeCallbacks(trimRunnable)
    }

    private inner class DeckListener(private val index: Int) : Player.Listener {

        private val isActive: Boolean get() = index == activeIndex

        override fun onEvents(player: Player, events: Player.Events) {
            if (!isActive) return
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                )
            ) {
                scheduleTick()
            }
            postInvalidate()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (!isActive) return
            // Les seeks sont déjà rapportés par SimpleBasePlayer lui-même.
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                reason == Player.DISCONTINUITY_REASON_SKIP ||
                reason == Player.DISCONTINUITY_REASON_INTERNAL
            ) {
                pendingDiscontinuity = reason to oldPosition.positionMs.coerceAtLeast(0L)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isActive) return
            applyDecision(scheduler.transition())
            applyVolumes()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isActive) return
            if (playWhenReady == effectivePlayWhenReady()) return
            // Le deck a décidé seul : casque débranché, ou fin de titre pendant un fondu engagé.
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                scheduler.phase != DualDeckScheduler.Phase.IDLE
            ) {
                if (!overlapStarted) overlap(0f)
                applyDecision(DualDeckScheduler.Decision.Swap)
                return
            }
            this@CrossfadePlayer.playWhenReady = playWhenReady
            playWhenReadyReason = reason
            if (!playWhenReady) applyDecision(scheduler.pause())
            syncDecksPlayWhenReady()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!isActive) return
            refreshFocus(playbackState)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (isActive) return
            // Réserve illisible : on laisse l'actif enchaîner nativement.
            applyDecision(scheduler.reserveFailed())
        }
    }

    private companion object {
        const val MAX_FADE_MS = 12_000
        const val RESERVE_TRIM_MS = 30_000L
    }
}
