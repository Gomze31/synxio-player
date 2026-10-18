package fr.synxio.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** Familles de sorties audio, telles qu'on les présente à l'utilisateur. */
enum class AudioOutputKind { SPEAKER, WIRED, USB, BLUETOOTH, CAR, HEARING_AID, HDMI, OTHER }

/** Sortie qui reçoit la musique en ce moment. */
data class AudioOutput(val kind: AudioOutputKind, val name: String) {
    /** Le haut-parleur du téléphone n'a pas besoin d'être signalé. */
    val isExternal: Boolean get() = kind != AudioOutputKind.SPEAKER

    companion object {
        val Speaker = AudioOutput(AudioOutputKind.SPEAKER, "Haut-parleur du téléphone")
    }
}

/** Ce que l'on retient d'un `AudioDeviceInfo`, pour décider sans Android. */
data class OutputDevice(val type: Int, val productName: String?)

/**
 * Choix de la sortie active parmi les périphériques branchés.
 *
 * Android route la musique vers le dernier casque connecté, avant le haut-parleur : on
 * reproduit cet ordre quand le système ne dit pas lui-même où part le son.
 */
object AudioOutputs {

    private val PRIORITY = listOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
    )

    /** Sorties qui comptent comme « un casque » pour la reprise et la pause automatiques. */
    val HEADSET_TYPES: Set<Int> = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )

    /**
     * [routed] : les sorties que le système annonce pour la musique (Android 13 et plus),
     * vide s'il ne le dit pas ; [connected] : toutes les sorties présentes.
     */
    fun pick(routed: List<OutputDevice>, connected: List<OutputDevice>): AudioOutput {
        routed.firstOrNull { it.type in PRIORITY || it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?.let { return describe(it) }
        val best = connected
            .filter { it.type in PRIORITY }
            .minByOrNull { PRIORITY.indexOf(it.type) }
            ?: return AudioOutput.Speaker
        return describe(best)
    }

    /**
     * Reprise de la lecture quand un casque se connecte.
     *
     * Seulement si l'utilisateur l'a demandé, qu'une file attend en pause, et jamais
     * pendant un appel : la musique couvrirait la conversation dès la connexion du casque.
     */
    fun shouldResumeOnConnect(enabled: Boolean, hasQueue: Boolean, playWhenReady: Boolean, inCall: Boolean): Boolean =
        enabled && hasQueue && !playWhenReady && !inCall

    fun describe(device: OutputDevice): AudioOutput {
        val name = device.productName?.trim().orEmpty()
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutput.Speaker
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                AudioOutput(if (looksLikeCar(name)) AudioOutputKind.CAR else AudioOutputKind.BLUETOOTH, name.ifEmpty { "Appareil Bluetooth" })
            AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutput(AudioOutputKind.HEARING_AID, name.ifEmpty { "Appareil auditif" })
            // Pour une prise jack, le « nom du produit » est celui du téléphone : on ne l'affiche pas.
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                AudioOutput(AudioOutputKind.WIRED, "Casque filaire")
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY ->
                AudioOutput(AudioOutputKind.USB, name.ifEmpty { "Sortie USB" })
            AudioDeviceInfo.TYPE_HDMI -> AudioOutput(AudioOutputKind.HDMI, name.ifEmpty { "HDMI" })
            else -> AudioOutput(AudioOutputKind.OTHER, name.ifEmpty { "Sortie externe" })
        }
    }

    /** Les autoradios Bluetooth n'ont pas de type à part : leur nom les trahit souvent. */
    fun looksLikeCar(name: String): Boolean {
        // Mot entier : « car » ne doit pas désigner « Oscar » ni « Scarlett ».
        val words = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return words.any { it in CAR_WORDS }
    }

    private val CAR_WORDS = setOf(
        "car", "auto", "voiture", "uconnect", "sync", "mylink", "carplay", "mmi", "idrive", "mbux", "comand",
        "rlink", "peugeot", "citroen", "citroën", "renault", "toyota", "honda", "nissan", "hyundai", "kia",
        "skoda", "škoda", "seat", "cupra", "volkswagen", "vw", "audi", "bmw", "mercedes", "tesla", "ford", "opel",
        "fiat", "dacia", "mazda", "volvo", "porsche", "lexus", "jeep",
    )
}

/**
 * Sortie audio active, suivie tant que quelqu'un l'observe.
 *
 * Le rappel système n'est inscrit que pendant la collecte : l'écran du lecteur l'écoute,
 * puis le relâche dès qu'il passe en arrière-plan.
 */
@Singleton
class AudioOutputMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val output: Flow<AudioOutput> = callbackFlow {
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            trySend(AudioOutput.Speaker)
            awaitClose { }
            return@callbackFlow
        }
        fun publish() {
            trySend(current(audioManager))
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = publish()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = publish()
        }
        publish()
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }.conflate().distinctUntilChanged()

    private fun current(audioManager: AudioManager): AudioOutput = runCatching {
        val connected = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toOutputDevice() }
        val routed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val media = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioManager.getAudioDevicesForAttributes(media).map { it.toOutputDevice() }
        } else emptyList()
        AudioOutputs.pick(routed, connected)
    }.getOrDefault(AudioOutput.Speaker)

    private fun AudioDeviceInfo.toOutputDevice() = OutputDevice(type, productName?.toString())
}

/**
 * Ouvre le sélecteur de sortie du système (Android 11+), ou les réglages Bluetooth avant.
 *
 * Android 14 l'expose directement ; Android 11 à 13 l'ouvrent sur une diffusion de
 * l'interface système.
 */
fun openSystemOutputSwitcher(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val shown = runCatching { android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher() }
            .getOrDefault(false)
        if (shown) return true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val sent = runCatching {
            context.sendBroadcast(
                android.content.Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                    .setPackage("com.android.systemui")
                    .putExtra("package_name", context.packageName)
            )
        }.isSuccess
        if (sent) return true
    }
    return runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}
