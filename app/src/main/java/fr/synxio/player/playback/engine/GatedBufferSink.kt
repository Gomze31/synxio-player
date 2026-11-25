package fr.synxio.player.playback.engine

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer

/**
 * Porte devant le puits du pouls, une par deck.
 *
 * [fr.synxio.player.playback.pulse.PulseSink] n'a qu'un tampon de travail et aucun
 * verrou : deux decks qui y écrivent en même temps se marcheraient dessus. Seule la porte
 * du deck actif est ouverte ; à l'ouverture, le format est rejoué au puits pour qu'il
 * décode correctement les premiers octets.
 *
 * `open` est posé depuis le thread principal, le reste tourne sur le thread audio du deck.
 */
class GatedBufferSink(private val delegate: TeeAudioProcessor.AudioBufferSink) : TeeAudioProcessor.AudioBufferSink {

    @Volatile
    var open: Boolean = false
        set(value) {
            field = value
            if (value) formatSent = false
        }

    @Volatile
    private var formatSent = false
    private var sampleRateHz = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        formatSent = false
        if (open) {
            delegate.flush(sampleRateHz, channelCount, encoding)
            formatSent = true
        }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!open) return
        if (!formatSent) {
            if (encoding == C.ENCODING_INVALID) return
            delegate.flush(sampleRateHz, channelCount, encoding)
            formatSent = true
        }
        delegate.handleBuffer(buffer)
    }
}
