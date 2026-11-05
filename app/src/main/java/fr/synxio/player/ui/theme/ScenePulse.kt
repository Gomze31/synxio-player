package fr.synxio.player.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.playback.pulse.AudioPulse
import kotlinx.coroutines.flow.StateFlow

/** Pouls disponible pour la scène, ou `null` quand la scène réactive est coupée. */
val LocalScenePulse = compositionLocalOf<State<AudioPulse>?> { null }

/**
 * Applique les règles de coupure (`sceneAllowed`) et ne s'abonne au pouls que si tout
 * est favorable. `collectAsStateWithLifecycle` se désabonne hors `STARTED` : l'écran
 * éteint coupe l'analyse sans code supplémentaire.
 */
@Composable
fun rememberScenePulse(
    pulse: StateFlow<AudioPulse>,
    sceneReactive: Boolean,
    playing: Boolean,
    surfaceVisible: Boolean,
): State<AudioPulse>? {
    val power = rememberPowerState()
    val allowed = sceneAllowed(
        SceneConditions(
            reduceMotion = LocalReduceMotion.current,
            glowEffects = LocalGlowEffects.current,
            sceneReactive = sceneReactive,
            powerSave = power.powerSave,
            batteryLow = power.batteryLow,
            screenOn = true,
            playing = playing,
            surfaceVisible = surfaceVisible,
        ),
    )
    return if (allowed) pulse.collectAsStateWithLifecycle() else null
}

/** État d'énergie de l'appareil, observable par la composition. */
private class PowerState { var powerSave by mutableStateOf(false); var batteryLow by mutableStateOf(false) }

/**
 * Économie d'énergie et batterie ≤ 15 %. La batterie vient de `ACTION_BATTERY_CHANGED`,
 * diffusion collante : `registerReceiver` renvoie l'état courant, qui sert de valeur
 * initiale, puis chaque variation de niveau met l'état à jour.
 */
@Composable
private fun rememberPowerState(): PowerState {
    val context = LocalContext.current
    val state = remember { PowerState() }
    DisposableEffect(context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        state.powerSave = pm.isPowerSaveMode
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> state.powerSave = pm.isPowerSaveMode
                    Intent.ACTION_BATTERY_CHANGED -> state.batteryLow = isBatteryLow(intent)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        sticky?.let { state.batteryLow = isBatteryLow(it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

/** Vrai si le niveau (`EXTRA_LEVEL` sur `EXTRA_SCALE`) est au plus à 15 %. */
private fun isBatteryLow(intent: Intent): Boolean {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return false
    return level * 100 / scale <= LOW_BATTERY_PERCENT
}

private const val LOW_BATTERY_PERCENT = 15
