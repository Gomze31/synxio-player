package fr.synxio.player.wear

import android.content.ComponentName
import android.media.AudioManager
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.playback.PlaybackService
import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Reçoit les commandes de la montre et les joue sur `PlaybackService`.
 *
 * Play Services démarrent ce service à chaque message, même appli fermée. Un
 * `MediaController` est ouvert le temps de la commande : sa construction démarre
 * `PlaybackService` s'il ne tourne pas, ce qui suffit à « Reprendre » depuis la montre.
 */
@AndroidEntryPoint
class WearCommandService : WearableListenerService() {

    @Inject lateinit var wearBridge: WearBridge

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.COMMAND) return
        val payload = String(event.data, Charsets.UTF_8)
        val command = WearCommand.parse(payload)
        if (command == null) {
            Log.w(TAG, "Commande montre ignorée : $payload")
            return
        }
        scope.launch { run(command) }
    }

    private suspend fun run(command: WearCommand) {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        val controller = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { runCatching { future.await() }.getOrNull() }
        if (controller == null) {
            Log.w(TAG, "Lecteur injoignable en $CONNECT_TIMEOUT_MS ms : $command abandonnée")
            MediaController.releaseFuture(future)
            return
        }
        try {
            val audio = getSystemService(AudioManager::class.java)
            val volumeChanged = WearCommandExecutor(ControllerTarget(controller), StreamVolumeControl(audio)).execute(command)
            if (volumeChanged) wearBridge.publishVolumeOnly()
        } finally {
            MediaController.releaseFuture(future)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SynxioWear"
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
