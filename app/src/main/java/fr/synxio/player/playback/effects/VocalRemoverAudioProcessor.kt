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

    /** Lu sur le thread audio, écrit depuis le service : doit être visible tout de suite. */
    @Volatile
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

    /**
     * Actif seulement quand le karaoké est allumé, comme en 4.3.0.
     *
     * La 4.4.0 le gardait toujours dans le pipeline audio pour appliquer le karaoké en
     * cours de morceau ; ce changement est retiré par précaution, le pipeline éprouvé
     * compte plus que l'effet immédiat. ExoPlayer ne relisant `isActive()` qu'à la
     * reconfiguration, le karaoké s'applique au morceau suivant (ou après une recherche).
     */
    override fun isActive(): Boolean = active && inputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        if (!active) {
            buffer.put(inputBuffer)
            buffer.flip()
            outputBuffer = buffer
            return
        }

        // Traitement 16 bits stéréo. Les tampons audio de Media3 sont dans l'ordre natif :
        // on l'impose explicitement plutôt que de dépendre de celui du tampon reçu.
        val shortBuffer = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        val outShortBuffer = buffer.asShortBuffer()

        while (shortBuffer.remaining() >= 2) {
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
