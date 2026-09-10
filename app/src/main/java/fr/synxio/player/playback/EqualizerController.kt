package fr.synxio.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

data class BandInfo(val index: Int, val centerFreqHz: Int, val levelMillibel: Short)

data class EqualizerCapabilities(
    val available: Boolean = false,
    val bands: List<BandInfo> = emptyList(),
    val minLevel: Short = -1500,
    val maxLevel: Short = 1500,
    val presets: List<String> = emptyList(),
    val bassBoostSupported: Boolean = false,
    val virtualizerSupported: Boolean = false,
    val loudnessSupported: Boolean = false,
)

/**
 * Effets audio système attachés à la session audio d'ExoPlayer.
 *
 * Ces effets sont fournis par le constructeur du téléphone : le nombre de bandes et
 * les préréglages varient d'un appareil à l'autre, d'où la découverte dynamique.
 */
@Singleton
class EqualizerController @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private var sessionId: Int = 0

    var capabilities: EqualizerCapabilities = EqualizerCapabilities()
        private set

    /** À appeler dès qu'ExoPlayer publie un nouvel identifiant de session audio. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId

        runCatching {
            val eq = Equalizer(PRIORITY, audioSessionId)
            val bandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            capabilities = EqualizerCapabilities(
                available = true,
                bands = (0 until bandCount).map { i ->
                    BandInfo(
                        index = i,
                        centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000,
                        levelMillibel = eq.getBandLevel(i.toShort()),
                    )
                },
                minLevel = range.getOrElse(0) { -1500 },
                maxLevel = range.getOrElse(1) { 1500 },
                presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                bassBoostSupported = true,
                virtualizerSupported = true,
                loudnessSupported = true,
            )
            equalizer = eq
        }.onFailure {
            Log.w(TAG, "Égaliseur indisponible sur cet appareil", it)
            capabilities = EqualizerCapabilities(available = false)
        }

        bassBoost = runCatching { BassBoost(PRIORITY, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(PRIORITY, audioSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

        capabilities = capabilities.copy(
            bassBoostSupported = bassBoost?.strengthSupported ?: false,
            virtualizerSupported = virtualizer?.strengthSupported ?: false,
            loudnessSupported = loudness != null,
        )
    }

    fun setEnabled(enabled: Boolean) {
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            loudness?.enabled = enabled
        }.onFailure { Log.w(TAG, "Activation des effets impossible", it) }
    }

    fun applyPreset(presetIndex: Int) {
        val eq = equalizer ?: return
        runCatching {
            if (presetIndex in 0 until eq.numberOfPresets) {
                eq.usePreset(presetIndex.toShort())
                refreshBands()
            }
        }.onFailure { Log.w(TAG, "Préréglage $presetIndex refusé", it) }
    }

    fun setBandLevel(band: Int, millibel: Short) {
        runCatching { equalizer?.setBandLevel(band.toShort(), millibel) }
            .onFailure { Log.w(TAG, "Bande $band refusée", it) }
        refreshBands()
    }

    fun setBandLevels(levels: List<Short>) {
        val eq = equalizer ?: return
        runCatching {
            levels.forEachIndexed { i, level ->
                if (i < eq.numberOfBands) eq.setBandLevel(i.toShort(), level)
            }
        }.onFailure { Log.w(TAG, "Application des bandes impossible", it) }
        refreshBands()
    }

    /** [strength] de 0 à 1000, échelle Android. */
    fun setBassBoost(strength: Int) {
        runCatching { bassBoost?.setStrength(strength.coerceIn(0, 1000).toShort()) }
            .onFailure { Log.w(TAG, "Bass boost refusé", it) }
    }

    fun setVirtualizer(strength: Int) {
        runCatching { virtualizer?.setStrength(strength.coerceIn(0, 1000).toShort()) }
            .onFailure { Log.w(TAG, "Virtualiseur refusé", it) }
    }

    /** Gain en millibels : compense les enregistrements trop faibles. */
    fun setLoudnessGain(millibel: Int) {
        runCatching { loudness?.setTargetGain(millibel.coerceIn(0, 2000)) }
            .onFailure { Log.w(TAG, "Loudness refusé", it) }
    }

    fun currentBandLevels(): List<Short> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()) }
        }.getOrDefault(emptyList())
    }

    private fun refreshBands() {
        val eq = equalizer ?: return
        runCatching {
            capabilities = capabilities.copy(
                bands = (0 until eq.numberOfBands).map { i ->
                    BandInfo(i, eq.getCenterFreq(i.toShort()) / 1000, eq.getBandLevel(i.toShort()))
                }
            )
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        sessionId = 0
    }

    private companion object {
        const val TAG = "EqualizerController"
        /** 0 = priorité normale ; suffisant pour un lecteur au premier plan. */
        const val PRIORITY = 0
    }
}
