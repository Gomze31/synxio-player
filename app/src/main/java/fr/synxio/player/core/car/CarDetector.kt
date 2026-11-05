package fr.synxio.player.core.car

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suit la voiture connectée en Bluetooth, comme `BudsMonitor` suit les Galaxy Buds.
 *
 * Une voiture, c'est un appareil de classe « audio de voiture » ou une adresse cochée dans
 * « Mes voitures ». Les connexions arrivent par les diffusions ACL de la pile Bluetooth,
 * réservées aux applis qui ont BLUETOOTH_CONNECT (Android 12+) ; sans elle, rien n'est
 * détecté. Partagé en continu : le service agit sur chaque connexion tant que le
 * processus vit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CarDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val permissionChanges = MutableStateFlow(0)

    val connected: StateFlow<CarDevice?> = combine(
        permissionChanges,
        settings.settings.map { it.carAddresses }.distinctUntilChanged(),
    ) { _, chosen -> chosen }
        .flatMapLatest { chosen -> carFlow(chosen) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** À appeler après la demande de permission : le récepteur s'inscrit alors. */
    fun onPermissionChanged() = permissionChanges.update { it + 1 }

    /** Appareils appairés, pour cocher « Mes voitures ». Vide sans permission. */
    @Suppress("MissingPermission")
    fun bondedDevices(): List<CarDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .map { CarDevice(it.address, it.displayName() ?: it.address) }
            .sortedBy { it.name.lowercase() }
    }

    @Suppress("MissingPermission")
    private fun carFlow(chosen: Set<String>): Flow<CarDevice?> = callbackFlow {
        if (!hasBluetoothPermission()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        var current: CarDevice? = null

        fun carOf(device: BluetoothDevice): CarDevice? {
            val deviceClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull() ?: 0
            if (!CarRules.isCar(deviceClass, device.address.orEmpty(), chosen)) return null
            return CarDevice(device.address, device.displayName() ?: device.address)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> carOf(device)?.let { current = it; trySend(it) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (current?.address == device.address) {
                        current = null
                        trySend(null)
                    }
                }
            }
        }

        // État initial : une voiture déjà branchée comme sortie audio, retrouvée parmi les appareils appairés.
        val audioManager = context.getSystemService(AudioManager::class.java)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val outputs = runCatching { audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            .mapNotNull { it.productName?.toString()?.trim() }
        current = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .firstOrNull { (it.displayName() ?: it.address) in outputs }
            ?.let(::carOf)
        trySend(current)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // Exportée : la pile Bluetooth est une appli à part (com.android.bluetooth).
        runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /** Nom affiché : le surnom donné par l'utilisateur (Android 11+), sinon le nom. */
    @Suppress("MissingPermission")
    private fun BluetoothDevice.displayName(): String? =
        runCatching { (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) alias ?: name else name)?.trim() }.getOrNull()
}
