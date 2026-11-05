package fr.synxio.player.playback

import android.os.SystemClock
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SleepTimerState(
    val active: Boolean = false,
    val remainingMs: Long = 0,
    val finishCurrentTrack: Boolean = false,
    /** Veille adaptative : fondu long, pause sur immobilité, secousse pour prolonger. */
    val adaptive: Boolean = false,
    /** Lecture mise en pause par l'immobilité ; une secousse dans les 30 s relance. */
    val pausedForStillness: Boolean = false,
) {
    /** Multiplicateur de volume. Classique : 1 → 0 dans la dernière minute. Adaptatif : [adaptiveFade]. */
    val fadeMultiplier: Float
        get() = when {
            !active || finishCurrentTrack -> 1f
            adaptive -> adaptiveFade(remainingMs)
            remainingMs <= FINAL_FADE_MS -> (remainingMs / FINAL_FADE_MS.toFloat()).coerceIn(0f, 1f)
            else -> 1f
        }
}

/** Fondu adaptatif : 1 à 20 min, linéaire jusqu'à 0,15 à 60 s, puis le fondu de fin jusqu'à 0. */
fun adaptiveFade(remainingMs: Long): Float = when {
    remainingMs >= ADAPTIVE_FADE_MS -> 1f
    remainingMs > FINAL_FADE_MS -> ADAPTIVE_FLOOR + (1f - ADAPTIVE_FLOOR) * (remainingMs - FINAL_FADE_MS).toFloat() / (ADAPTIVE_FADE_MS - FINAL_FADE_MS)
    else -> ADAPTIVE_FLOOR * (remainingMs / FINAL_FADE_MS.toFloat()).coerceIn(0f, 1f)
}

const val FINAL_FADE_MS = 60_000L
const val ADAPTIVE_FADE_MS = 20 * 60_000L
const val ADAPTIVE_FLOOR = 0.15f

/**
 * Minuterie de veille. Trois modes :
 *  - durée fixe (« dans 30 min ») ;
 *  - « à la fin du morceau » : le service coupe sur l'événement de fin de piste ;
 *  - adaptative : fondu sur 20 min, pause quand le téléphone est immobile écran éteint
 *    (le service appelle [onStillness]), secousse qui relance ou prolonge ([onShake]).
 *
 * L'échéance est un instant `elapsedRealtime` : des ticks en retard (Doze) ne la décalent pas.
 */
@Singleton
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    @Inject
    constructor(@ApplicationScope scope: CoroutineScope) : this(scope, SystemClock::elapsedRealtime)

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null
    private var graceJob: Job? = null
    private var deadline = 0L
    private var onElapsed: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onResume: (() -> Unit)? = null

    fun setCallback(callback: () -> Unit) {
        onElapsed = callback
    }

    /** Détache le rappel du service détruit, que ce singleton retenait sinon. */
    fun clearCallback(callback: () -> Unit) {
        if (onElapsed === callback) onElapsed = null
    }

    fun setStillnessCallbacks(onPause: () -> Unit, onResume: () -> Unit) {
        this.onPause = onPause
        this.onResume = onResume
    }

    fun start(durationMs: Long, finishCurrentTrack: Boolean, adaptive: Boolean = false) {
        cancel()
        deadline = clock() + durationMs
        _state.value = SleepTimerState(true, durationMs, finishCurrentTrack, adaptive)
        job = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val remaining = (deadline - clock()).coerceAtLeast(0)
                _state.value = _state.value.copy(remainingMs = remaining)
                if (remaining <= 0) break
            }
            if (isActive && !finishCurrentTrack) {
                // Pause d'abord : réinitialiser l'état remet le gain de veille à 1, ce qui
                // ferait remonter le volume d'un coup avant l'arrêt.
                onElapsed?.invoke()
                _state.value = SleepTimerState()
            }
            // En mode « fin de morceau », on laisse l'état actif : c'est le service
            // qui déclenchera l'arrêt à la transition de piste suivante.
        }
    }

    /** Prolonge la minuterie en cours (bouton « +5 min » de la notification, secousse). */
    fun extend(extraMs: Long) {
        val current = _state.value
        if (!current.active) return
        val remaining = (deadline - clock()).coerceAtLeast(0)
        start(remaining + extraMs, current.finishCurrentTrack, current.adaptive)
    }

    /** Vrai si la minuterie est arrivée à zéro en mode « fin de morceau ». */
    fun shouldStopAtTrackEnd(): Boolean =
        _state.value.active && _state.value.finishCurrentTrack && _state.value.remainingMs <= 0

    /** Le service a constaté l'immobilité écran éteint : pause, puis arrêt après la grâce. */
    fun onStillness() {
        val current = _state.value
        if (!current.active || !current.adaptive || current.pausedForStillness) return
        _state.value = current.copy(pausedForStillness = true)
        onPause?.invoke()
        graceJob = scope.launch {
            delay(GRACE_MS)
            if (_state.value.pausedForStillness) {
                onElapsed?.invoke()
                cancel()
            }
        }
    }

    /** Secousse : relance si la lecture est en pause de grâce, et ajoute 15 min dans tous les cas adaptatifs. */
    fun onShake() {
        val current = _state.value
        if (!current.active || !current.adaptive) return
        if (current.pausedForStillness) {
            graceJob?.cancel()
            graceJob = null
            _state.value = current.copy(pausedForStillness = false)
            onResume?.invoke()
        }
        extend(SHAKE_EXTEND_MS)
    }

    fun cancel() {
        job?.cancel()
        job = null
        graceJob?.cancel()
        graceJob = null
        _state.value = SleepTimerState()
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val GRACE_MS = 30_000L
        const val SHAKE_EXTEND_MS = 15 * 60_000L
    }
}
