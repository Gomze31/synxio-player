package fr.synxio.player.wear

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import fr.synxio.player.playback.PlaybackService

/** Ce que la montre peut faire faire au lecteur. Interface étroite, testable sans Media3. */
interface PlaybackTarget {
    val hasQueue: Boolean
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun toggleFavorite()
    /** Pose un `MediaItem` dont l'identifiant est résolu par `onSetMediaItems`, puis lit. */
    fun start(mediaId: String)
}

/** Implémentation sur un [MediaController] connecté à `PlaybackService`. */
class ControllerTarget(private val controller: MediaController) : PlaybackTarget {
    override val hasQueue: Boolean get() = controller.mediaItemCount > 0

    override fun playPause() {
        Util.handlePlayPauseButtonAction(controller)
    }

    override fun next() = controller.seekToNext()
    override fun previous() = controller.seekToPrevious()
    override fun seekTo(ms: Long) = controller.seekTo(ms)

    override fun toggleFavorite() {
        controller.sendCustomCommand(SessionCommand(PlaybackService.CMD_TOGGLE_FAVORITE, Bundle.EMPTY), Bundle.EMPTY)
    }

    override fun start(mediaId: String) {
        controller.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }
}
