package fr.synxio.player.voice

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Démarre ou arrête l'écoute selon le réglage. Appelé depuis l'interface (ouverture de
 * l'app, retour au premier plan, interrupteur) : c'est le seul moment où Android accorde
 * le micro à un service.
 */
@Singleton
class VoiceWakeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: VoskModelStore,
) {
    fun sync(enabled: Boolean) {
        val intent = Intent(context, VoiceWakeService::class.java)
        if (enabled && VoiceWakeService.canListen(context, models)) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w("VoiceWake", "Démarrage de l'écoute refusé", it) }
        } else {
            context.stopService(intent)
        }
    }
}
