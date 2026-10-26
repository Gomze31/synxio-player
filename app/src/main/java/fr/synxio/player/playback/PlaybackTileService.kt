package fr.synxio.player.playback

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import fr.synxio.player.R

/**
 * Tuile des Réglages rapides : lecture / pause, avec le titre en cours en sous-titre.
 *
 * Le contrôleur n'est relié à la session que pendant que le volet est ouvert
 * (`onStartListening` → `onStopListening`) : rien ne tourne quand la tuile est cachée.
 * L'appui long ouvre l'appli (filtre `QS_TILE_PREFERENCES` de MainActivity).
 */
class PlaybackTileService : TileService() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone && !it.isCancelled }?.let { runCatching { it.get() }.getOrNull() }

    /** Toucher reçu avant la fin de la connexion : joué dès qu'elle aboutit. */
    private var pendingToggle = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        connect()
        updateTile()
    }

    override fun onStopListening() {
        release()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val c = controller
        if (c == null) {
            pendingToggle = true
            connect()
        } else {
            toggle(c)
        }
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = controller ?: return@addListener
            c.addListener(listener)
            if (pendingToggle) {
                pendingToggle = false
                toggle(c)
            }
            updateTile()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun release() {
        controllerFuture?.let { future ->
            controller?.removeListener(listener)
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
    }

    /** Sans file chargée, `play()` déclenche la reprise de la file sauvegardée côté session. */
    private fun toggle(c: MediaController) {
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val c = controller
        val playing = c?.isPlaying == true
        val title = c?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_music)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = title ?: if (playing) "Lecture" else "En pause"
        }
        tile.contentDescription = buildString {
            append(if (playing) "Mettre en pause" else "Lancer la lecture")
            title?.let { append(" : ").append(it) }
        }
        tile.updateTile()
    }
}
