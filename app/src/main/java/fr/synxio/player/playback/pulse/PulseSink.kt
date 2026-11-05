package fr.synxio.player.playback.pulse

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer

/**
 * Reçoit une copie du PCM qui sort d'ExoPlayer (via [TeeAudioProcessor], qui laisse le
 * flux inchangé), le mixe en mono et l'écrit dans [buffer]. Chemin chaud : aucune
 * allocation après la construction, aucun verrou, aucun log.
 *
 * Formats acceptés : PCM 16 bits et float. Les autres rendent le puits inactif ; le son
 * continue, le pouls reste muet.
 */
class PulseSink(private val buffer: PulseRingBuffer) : TeeAudioProcessor.AudioBufferSink {

    @Volatile
    var sampleRate: Int = 0
        private set

    @Volatile
    var supported: Boolean = false
        private set

    private var channels = 1
    private var encoding = C.ENCODING_INVALID
    private val scratch = FloatArray(SCRATCH_FRAMES)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        supported = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
    }

    override fun handleBuffer(input: ByteBuffer) {
        if (!supported) return
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * channels
        var position = input.position()
        val limit = input.limit()
        var count = 0
        while (position + frameBytes <= limit) {
            var sum = 0f
            for (c in 0 until channels) {
                val offset = position + c * bytesPerSample
                sum += if (bytesPerSample == 4) input.getFloat(offset) else input.getShort(offset) / 32_768f
            }
            scratch[count++] = sum / channels
            position += frameBytes
            if (count == SCRATCH_FRAMES) {
                buffer.write(scratch, count)
                count = 0
            }
        }
        if (count > 0) buffer.write(scratch, count)
    }

    private companion object {
        const val SCRATCH_FRAMES = 4096
    }
}
