package fr.synxio.player.playback.pulse

import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source du pouls : le service branche [sink] dans la chaîne audio et dit quand la
 * lecture tourne ; l'interface s'abonne à [pulse]. Sans abonné, rien ne tourne ; en
 * pause, le flux émet [AudioPulse.SILENT] sans calculer.
 */
@Singleton
class PulseSource internal constructor(
    scope: CoroutineScope,
    private val tickMs: Long,
    private val produce: () -> AudioPulse,
    val sink: PulseSink,
) {
    @Inject
    constructor(@ApplicationScope scope: CoroutineScope) : this(Analysis(scope))

    private constructor(analysis: Analysis) : this(analysis.scope, TICK_MS, analysis::next, analysis.sink)

    private val playing = MutableStateFlow(false)

    val pulse: StateFlow<AudioPulse> = flow {
        while (currentCoroutineContext().isActive) {
            emit(if (playing.value) produce() else AudioPulse.SILENT)
            delay(tickMs)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MS), AudioPulse.SILENT)

    fun setPlaying(value: Boolean) {
        playing.value = value
    }

    /** Tampon, puits et analyseur de production, réunis pour que le constructeur Hilt reste simple. */
    private class Analysis(val scope: CoroutineScope) {
        private val buffer = PulseRingBuffer()
        val sink = PulseSink(buffer)
        private val analyser = AudioAnalyser()
        private val frame = FloatArray(FRAME_SIZE)

        fun next(): AudioPulse {
            analyser.setSampleRate(sink.sampleRate)
            buffer.readLast(frame)
            return analyser.analyse(frame)
        }
    }

    private companion object {
        const val TICK_MS = 33L
        const val STOP_TIMEOUT_MS = 500L
        const val FRAME_SIZE = 1024
    }
}
