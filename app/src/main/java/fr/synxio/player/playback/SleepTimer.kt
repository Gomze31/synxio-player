package fr.synxio.player.playback

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
)

/**
 * Minuterie de veille. Deux modes :
 *  - durée fixe (« dans 30 min ») ;
 *  - « à la fin du morceau » : le service coupe sur l'événement de fin de piste.
 */
@Singleton
class SleepTimer @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null
    private var onElapsed: (() -> Unit)? = null

    fun setCallback(callback: () -> Unit) {
        onElapsed = callback
    }

    fun start(durationMs: Long, finishCurrentTrack: Boolean) {
        cancel()
        _state.value = SleepTimerState(true, durationMs, finishCurrentTrack)
        job = scope.launch {
            var remaining = durationMs
            while (isActive && remaining > 0) {
                delay(TICK_MS)
                remaining -= TICK_MS
                _state.value = _state.value.copy(remainingMs = remaining.coerceAtLeast(0))
            }
            if (isActive && !finishCurrentTrack) {
                _state.value = SleepTimerState()
                onElapsed?.invoke()
            }
            // En mode « fin de morceau », on laisse l'état actif : c'est le service
            // qui déclenchera l'arrêt à la transition de piste suivante.
        }
    }

    /** Prolonge la minuterie en cours (bouton « +5 min » de la notification). */
    fun extend(extraMs: Long) {
        val current = _state.value
        if (!current.active) return
        start(current.remainingMs + extraMs, current.finishCurrentTrack)
    }

    /** Vrai si la minuterie est arrivée à zéro en mode « fin de morceau ». */
    fun shouldStopAtTrackEnd(): Boolean =
        _state.value.active && _state.value.finishCurrentTrack && _state.value.remainingMs <= 0

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SleepTimerState()
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
