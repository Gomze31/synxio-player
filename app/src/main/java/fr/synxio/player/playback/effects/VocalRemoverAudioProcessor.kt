package fr.synxio.player.playback.effects

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Filtre audio temps réel (Center Channel Extraction) pour supprimer la voix (mode Karaoké).
 * Il soustrait le canal droit du canal gauche, car la voix est généralement mixée au centre (mono).
 */
@androidx.media3.common.util.UnstableApi
class VocalRemoverAudioProcessor : AudioProcessor {

    private var active = false
    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    
    private var inputEnded = false

    fun setEnabled(enabled: Boolean) {
        if (active != enabled) {
            active = enabled
        }
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        // Le mode karaoké requiert un signal stéréo 16 bits.
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            this.inputAudioFormat = AudioFormat.NOT_SET
            this.outputAudioFormat = AudioFormat.NOT_SET
            return AudioFormat.NOT_SET
        }
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = active && inputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            outputBuffer = inputBuffer
            return
        }

        val size = inputBuffer.remaining()
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        // Traitement 16 bits stéréo
        val shortBuffer = inputBuffer.asShortBuffer()
        val outShortBuffer = buffer.asShortBuffer()
        
        while (shortBuffer.hasRemaining()) {
            val left = shortBuffer.get()
            val right = shortBuffer.get()
            
            // Annulation de phase (L - R)
            // On divise par 2 pour éviter le clipping
            val difference = ((left - right) / 2).toShort()
            
            // On redistribue le résultat sur les deux canaux (fausse stéréo mono)
            outShortBuffer.put(difference)
            outShortBuffer.put(difference)
        }
        
        inputBuffer.position(inputBuffer.position() + size)
        buffer.limit(size)
        outputBuffer = buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }
}
