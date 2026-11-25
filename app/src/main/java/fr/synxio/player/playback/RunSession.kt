package fr.synxio.player.playback

import fr.synxio.player.data.model.RunTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** « Suivre » : la cible suit la cadence ; « Verrouillé » : la cible est choisie à la main. */
enum class RunMode(val label: String) { FOLLOW("Suivre"), LOCKED("Verrouillé") }

/**
 * État de la course au tempo, partagé entre l'écran et le service. L'écran écrit la
 * demande (active, permission, cible verrouillée) ; le service écrit ce qu'il mesure
 * (cadence, stabilité, cible suivie) et ce qu'il choisit (titre d'avance).
 */
data class RunState(
    val active: Boolean = false,
    /** Permission `ACTIVITY_RECOGNITION` accordée (ou inutile avant Android 10). */
    val sensorAllowed: Boolean = false,
    /** Aucun podomètre sur l'appareil : seul le mode verrouillé a un sens. */
    val sensorMissing: Boolean = false,
    val cadence: Float = 0f,
    val stable: Boolean = false,
    val target: RunTarget = RunTarget.NONE,
    /** BPM du titre en cours, s'il est analysé. */
    val currentBpm: Float? = null,
    /** Vitesse appliquée au titre en cours (1,0 hors titre choisi par la course). */
    val currentSpeed: Float = 1f,
    val nextId: Long? = null,
    val nextTitle: String? = null,
    val nextArtist: String? = null,
    val nextBpm: Float? = null,
    val nextSpeed: Float = 1f,
    /** Cible connue mais aucun titre de la bibliothèque à ce tempo. */
    val noMatch: Boolean = false,
) {
    val mode: RunMode get() = if (target.locked) RunMode.LOCKED else RunMode.FOLLOW
}

@Singleton
class RunSession @Inject constructor() {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    /** Démarre la course (ou met à jour la permission si elle tourne déjà). */
    fun start(sensorAllowed: Boolean) {
        _state.update { s -> if (s.active) s.copy(sensorAllowed = sensorAllowed) else RunState(active = true, sensorAllowed = sensorAllowed) }
    }

    fun stop() {
        _state.value = RunState()
    }

    fun setSensorAllowed(allowed: Boolean) = _state.update { it.copy(sensorAllowed = allowed) }

    /** Verrouille la cible à [bpm] pas/min. */
    fun lock(bpm: Float) = _state.update { it.copy(target = RunTarget(bpm.coerceIn(60f, 240f), locked = true)) }

    /** Verrouille sur la cadence mesurée du moment ; sans cadence, ne fait rien. */
    fun lockToCadence() {
        val c = _state.value.cadence
        if (c > 0f) lock(c.roundToInt().toFloat())
    }

    /** Repasse en « Suivre » : la cible actuelle reste le point de départ, la cadence la fera bouger. */
    fun follow() = _state.update { it.copy(target = RunTarget(it.target.bpm, locked = false)) }

    internal fun update(block: (RunState) -> RunState) = _state.update(block)
}
