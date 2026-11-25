package fr.synxio.player.playback.engine

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Focus audio unique pour les deux decks de [CrossfadePlayer].
 *
 * Deux ExoPlayer qui gèrent chacun le focus se le volent mutuellement : quand la réserve
 * démarre pendant le chevauchement, l'actif reçoit `AUDIOFOCUS_LOSS` et se met en pause.
 * Les decks sont donc créés avec `handleAudioFocus = false`, et cette porte reproduit le
 * comportement de l'`AudioFocusManager` d'ExoPlayer : perte définitive → pause, perte
 * transitoire → lecture suspendue puis reprise, atténuation → gain 0,2.
 */
class AudioFocusGate(
    context: Context,
    private val handler: Handler,
) {

    /** Posé par [CrossfadePlayer] à sa construction. */
    var listener: Listener? = null

    interface Listener {
        /** Focus perdu pour de bon : le lecteur passe en pause (`PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS`). */
        fun onFocusLost()
        /** Perte transitoire (appel, navigation) : lecture suspendue, puis rendue à `false`. */
        fun onFocusSuppressed(suppressed: Boolean)
        /** Atténuation demandée par le système (notification), 1 = aucune. */
        fun onFocusVolumeMultiplier(multiplier: Float)
    }

    private enum class State { NONE, HELD, LOSS_TRANSIENT, DUCKED }

    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var state = State.NONE
    private var request: AudioFocusRequest? = null
    private var attributes: AudioAttributes = AudioAttributes.DEFAULT

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                val wasSuppressed = state == State.LOSS_TRANSIENT
                state = State.HELD
                listener?.onFocusVolumeMultiplier(1f)
                if (wasSuppressed) listener?.onFocusSuppressed(false)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                abandon()
                listener?.onFocusLost()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                state = State.LOSS_TRANSIENT
                listener?.onFocusSuppressed(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (willPauseWhenDucked()) {
                    state = State.LOSS_TRANSIENT
                    listener?.onFocusSuppressed(true)
                } else {
                    state = State.DUCKED
                    listener?.onFocusVolumeMultiplier(DUCK_MULTIPLIER)
                }
            }
        }
    }

    /** Nouveaux attributs (musique ↔ alarme) : le focus détenu est rendu, puis redemandé à la lecture. */
    fun setAudioAttributes(value: AudioAttributes) {
        if (attributes == value) return
        attributes = value
        request = null
        if (state != State.NONE) {
            abandon()
        }
    }

    /**
     * À appeler quand `playWhenReady` ou l'état de lecture change.
     *
     * @return vrai si la lecture peut démarrer maintenant ; faux si le focus est refusé ou
     *   suspendu (la lecture reste en attente du rappel).
     */
    fun updateAudioFocus(playWhenReady: Boolean, @Player.State playbackState: Int): Boolean {
        if (playbackState == Player.STATE_IDLE) {
            abandon()
            return playWhenReady
        }
        if (!playWhenReady) {
            // Musique : le focus reste détenu en pause, comme ExoPlayer. Gain transitoire
            // (alarme) : rendu dès la pause, pour ne pas bloquer les autres applis.
            if (focusGain() != AudioManager.AUDIOFOCUS_GAIN) abandon()
            return false
        }
        return when (state) {
            State.HELD, State.DUCKED -> true
            State.LOSS_TRANSIENT -> false
            State.NONE -> request()
        }
    }

    fun release() {
        abandon()
    }

    private fun request(): Boolean {
        val manager = audioManager ?: return true
        val req = request ?: AudioFocusRequest.Builder(focusGain())
            .setAudioAttributes(attributes.audioAttributesV21.audioAttributes)
            .setWillPauseWhenDucked(willPauseWhenDucked())
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
            .also { request = it }
        val result = runCatching { manager.requestAudioFocus(req) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            state = State.HELD
            true
        } else {
            state = State.NONE
            false
        }
    }

    private fun abandon() {
        if (state == State.NONE) return
        state = State.NONE
        val req = request ?: return
        runCatching { audioManager?.abandonAudioFocusRequest(req) }
    }

    private fun willPauseWhenDucked(): Boolean = attributes.contentType == C.AUDIO_CONTENT_TYPE_SPEECH

    private fun focusGain(): Int = when (attributes.usage) {
        C.USAGE_ALARM, C.USAGE_VOICE_COMMUNICATION -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        C.USAGE_ASSISTANCE_ACCESSIBILITY,
        C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        C.USAGE_ASSISTANCE_SONIFICATION,
        C.USAGE_NOTIFICATION -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        else -> AudioManager.AUDIOFOCUS_GAIN
    }

    private companion object {
        const val DUCK_MULTIPLIER = 0.2f
    }
}
