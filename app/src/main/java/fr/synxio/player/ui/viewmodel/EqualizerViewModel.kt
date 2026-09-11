package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.prefs.Settings
import fr.synxio.player.core.prefs.SettingsRepository
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.db.DeviceProfileDao
import fr.synxio.player.data.db.DeviceProfileEntity
import fr.synxio.player.data.model.EqCurve
import kotlinx.coroutines.flow.map
import fr.synxio.player.playback.EqualizerCapabilities
import fr.synxio.player.playback.EqualizerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val controller: EqualizerController,
    private val settingsRepository: SettingsRepository,
    private val deviceProfileDao: DeviceProfileDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _capabilities = MutableStateFlow(controller.capabilities)
    val capabilities: StateFlow<EqualizerCapabilities> = _capabilities.asStateFlow()

    private val _bandLevels = MutableStateFlow(controller.currentBandLevels())
    val bandLevels: StateFlow<List<Short>> = _bandLevels.asStateFlow()

    val fftFlow: StateFlow<ByteArray> = controller.fftFlow

    init {
        refresh()
    }

    /**
     * Les effets ne sont disponibles qu'une fois la session audio créée : si rien n'a
     * encore été joué, l'écran doit le dire au lieu d'afficher un égaliseur mort.
     */
    fun refresh() {
        _capabilities.value = controller.capabilities
        _bandLevels.value = controller.currentBandLevels()
    }

    fun setEnabled(enabled: Boolean) {
        controller.setEnabled(enabled)
        viewModelScope.launch { settingsRepository.setEqualizerEnabled(enabled) }
        refresh()
    }

    fun setVisualizerEnabled(enabled: Boolean) {
        controller.setVisualizerEnabled(enabled)
    }

    fun setBand(index: Int, millibel: Short) {
        controller.setBandLevel(index, millibel)
        val updated = controller.currentBandLevels()
        _bandLevels.value = updated
        viewModelScope.launch { settingsRepository.setEqualizerBands(updated) }
    }

    /** Nom de la sortie audio courante (casque, écouteurs), vide si haut-parleur. */
    private val _connectedDevice = MutableStateFlow("")
    val connectedDevice: StateFlow<String> = _connectedDevice.asStateFlow()

    /** Associations mémorisées périphérique → profil. */
    val deviceProfiles: StateFlow<Map<String, String>> = deviceProfileDao.observeAll()
        .map { list -> list.associate { it.deviceName to it.curveId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Relit la sortie audio active : appelé à l'ouverture de l'écran. */
    fun refreshConnectedDevice() {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val outputs = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull().orEmpty()

        _connectedDevice.value = outputs
            .firstOrNull { it.type in HEADSET_TYPES }
            ?.productName?.toString()
            .orEmpty()
    }

    /** Mémorise le profil courant pour le casque connecté. */
    fun rememberProfileForDevice() {
        val device = _connectedDevice.value
        val curveId = _activeCurveId.value
        if (device.isBlank() || curveId == null) return
        viewModelScope.launch {
            deviceProfileDao.put(DeviceProfileEntity(device, curveId, System.currentTimeMillis()))
        }
    }

    fun forgetProfileForDevice() {
        val device = _connectedDevice.value.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { deviceProfileDao.remove(device) }
    }

    /** Profil actuellement appliqué, pour cocher la puce correspondante. */
    private val _activeCurveId = MutableStateFlow<String?>(null)
    val activeCurveId: StateFlow<String?> = _activeCurveId.asStateFlow()

    /**
     * Applique une courbe d'écoute en l'interpolant sur les bandes réelles de l'appareil.
     *
     * Le nombre de bandes varie d'un téléphone à l'autre (5 ici) : on ne peut donc pas
     * stocker des niveaux figés, seulement une courbe fréquence → gain.
     */
    fun applyCurve(curve: EqCurve) {
        val caps = controller.capabilities
        if (!caps.available || caps.bands.isEmpty()) return

        val levels = curve.toBandLevels(
            bandFrequenciesHz = caps.bands.map { it.centerFreqHz },
            minMb = caps.minLevel,
            maxMb = caps.maxLevel,
        )

        // Choisir un profil alors que l'égaliseur est éteint ne s'entendrait pas :
        // on l'allume, c'est l'intention évidente de l'utilisateur.
        controller.setEnabled(true)
        controller.setBandLevels(levels)
        _bandLevels.value = controller.currentBandLevels()
        _activeCurveId.value = curve.id

        viewModelScope.launch {
            settingsRepository.setEqualizerEnabled(true)
            // -1 : on n'utilise plus un préréglage constructeur mais notre courbe.
            settingsRepository.setEqualizerPreset(-1)
            settingsRepository.setEqualizerBands(_bandLevels.value)
            settingsRepository.setEqualizerCurve(curve.id)
        }
        refresh()
    }

    fun applyPreset(index: Int) {
        _activeCurveId.value = null
        controller.applyPreset(index)
        val updated = controller.currentBandLevels()
        _bandLevels.value = updated
        viewModelScope.launch {
            settingsRepository.setEqualizerPreset(index)
            settingsRepository.setEqualizerBands(updated)
        }
        refresh()
    }

    fun resetBands() {
        val flat = List(capabilities.value.bands.size) { 0.toShort() }
        controller.setBandLevels(flat)
        _bandLevels.value = flat
        viewModelScope.launch {
            settingsRepository.setEqualizerPreset(-1)
            settingsRepository.setEqualizerBands(flat)
        }
    }

    fun setBassBoost(value: Int) {
        controller.setBassBoost(value)
        viewModelScope.launch { settingsRepository.setBassBoost(value) }
    }

    fun setVirtualizer(value: Int) {
        controller.setVirtualizer(value)
        viewModelScope.launch { settingsRepository.setVirtualizer(value) }
    }

    fun setLoudness(value: Int) {
        controller.setLoudnessGain(value)
        viewModelScope.launch { settingsRepository.setLoudnessGain(value) }
    }

    private companion object {
        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
