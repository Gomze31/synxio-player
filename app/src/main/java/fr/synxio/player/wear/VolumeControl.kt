package fr.synxio.player.wear

import android.media.AudioManager
import kotlin.math.roundToInt

/** Volume du flux musique, piloté par la couronne de la montre. */
interface VolumeControl {
    fun raise()
    fun lower()
    /** [level] de 0 à 1. */
    fun set(level: Float)
}

class StreamVolumeControl(private val audio: AudioManager) : VolumeControl {
    override fun raise() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    override fun lower() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    override fun set(level: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level.coerceIn(0f, 1f) * max).roundToInt(), AudioManager.FLAG_SHOW_UI)
    }
}
