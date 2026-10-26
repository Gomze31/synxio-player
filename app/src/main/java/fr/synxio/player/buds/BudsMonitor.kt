package fr.synxio.player.buds

import android.Manifest
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Galaxy Buds connectés : modèle, nom, et ce que la pile Bluetooth en dit. */
data class BudsState(
    val model: BudsModel,
    val name: String,
    /** Niveau global annoncé par les écouteurs (HFP), null tant qu'inconnu. */
    val batteryPercent: Int? = null,
    val codec: String? = null,
)

/**
 * Suit les Galaxy Buds connectés tant que quelqu'un observe [state].
 *
 * Le modèle vient du nom de la sortie audio, sans permission. Batterie et codec arrivent
 * par deux diffusions de la pile Bluetooth réservées aux applis qui ont BLUETOOTH_CONNECT ;
 * le reste (batterie gauche / droite / boîtier, réduction de bruit) passe par le protocole
 * propriétaire de Samsung, que l'app Galaxy Wearable garde pour elle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BudsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val permissionChanges = MutableStateFlow(0)

    /**
     * Dernières valeurs par appareil : les diffusions ne sont pas rejouées, et le flux
     * s'arrête quand plus personne ne l'observe.
     */
    private val batteries = ConcurrentHashMap<String, Int>()
    private val codecs = ConcurrentHashMap<String, String>()

    val state: StateFlow<BudsState?> = permissionChanges
        .flatMapLatest { budsFlow() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** À appeler après la demande de permission : les diffusions s'inscrivent alors. */
    fun onPermissionChanged() = permissionChanges.update { it + 1 }

    private fun budsFlow(): Flow<BudsState?> = callbackFlow {
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val permitted = hasBluetoothPermission()
        var connectedName: String? = null

        fun publish() {
            val name = connectedName
            val model = GalaxyBuds.detect(name)
            trySend(
                if (name == null || model == null) null
                else BudsState(model, name, batteries[name], codecs[name])
            )
        }

        fun refreshConnected() {
            val device = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
                .getOrDefault(emptyArray())
                .firstOrNull { it.type in BLUETOOTH_TYPES && GalaxyBuds.detect(it.productName?.toString()) != null }
            val name = device?.productName?.toString()?.trim()
            if (name != null && name != connectedName && permitted) readBattery(name)?.let { batteries[name] = it }
            connectedName = name
            publish()
        }

        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshConnected()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshConnected()
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = intent.deviceName() ?: connectedName ?: return
                if (GalaxyBuds.detect(name) == null) return
                when (intent.action) {
                    ACTION_BATTERY_LEVEL_CHANGED -> {
                        val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                        if (level in 0..100) batteries[name] = level else batteries.remove(name)
                    }
                    ACTION_CODEC_CONFIG_CHANGED -> {
                        val status = IntentCompat.getParcelableExtra(
                            intent, BluetoothCodecStatus.EXTRA_CODEC_STATUS, BluetoothCodecStatus::class.java,
                        )
                        codecName(status)?.let { codecs[name] = it }
                    }
                }
                publish()
            }
        }

        refreshConnected()
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        if (permitted) {
            val filter = IntentFilter().apply {
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
                addAction(ACTION_CODEC_CONFIG_CHANGED)
            }
            // Exportée : la pile Bluetooth est une appli à part (com.android.bluetooth).
            runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
        }
        awaitClose {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            if (permitted) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @Suppress("MissingPermission")
    private fun Intent.deviceName(): String? {
        val device = IntentCompat.getParcelableExtra(this, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return null
        return runCatching { device.displayName() }.getOrNull()
    }

    /** Nom affiché de l'appareil : le surnom donné par l'utilisateur (Android 11+), sinon son nom. */
    @Suppress("MissingPermission")
    private fun BluetoothDevice.displayName(): String? =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) alias ?: name else name)?.trim()

    /**
     * Niveau connu au moment de la connexion. `getBatteryLevel` n'est pas public : lu par
     * réflexion, au mieux, sinon on attend la prochaine diffusion des écouteurs.
     */
    @Suppress("MissingPermission")
    private fun readBattery(name: String): Int? = runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val device = adapter.bondedDevices.firstOrNull { it.displayName() == name } ?: return null
        val level = BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
        level.takeIf { it in 0..100 }
    }.getOrNull()

    private fun codecName(status: BluetoothCodecStatus?): String? {
        val config = status?.codecConfig ?: return null
        val extended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { config.extendedCodecType?.codecName }.getOrNull()
        } else null
        @Suppress("DEPRECATION")
        return GalaxyBuds.codecName(config.codecType, extended)
    }

    private companion object {
        val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )

        // Constantes masquées du SDK, stables depuis Android 8.
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val ACTION_CODEC_CONFIG_CHANGED = "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"
    }
}
