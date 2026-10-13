package fr.synxio.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.google.common.collect.ImmutableList
import fr.synxio.player.playback.effects.VocalRemoverAudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fait passer un flux PCM dans le vrai `AudioProcessingPipeline` de Media3, comme le fait
 * l'`AudioSink` d'ExoPlayer, pour vérifier que le karaoké (allumé ou non) laisse le son
 * passer et que la fin de flux est atteinte.
 */
class AudioPipelineRegressionTest {

    private val format = AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

    private fun run(processor: AudioProcessor, chunks: Int = 50, chunkBytes: Int = 4096): Pair<ByteArray, ByteArray> {
        val pipeline = AudioProcessingPipeline(ImmutableList.of(processor))
        pipeline.configure(format)
        pipeline.flush()
        return run(pipeline, chunks, chunkBytes)
    }

    private fun run(pipeline: AudioProcessingPipeline, chunks: Int = 50, chunkBytes: Int = 4096): Pair<ByteArray, ByteArray> {

        val expected = ByteArrayOutputStream()
        val actual = ByteArrayOutputStream()
        var guard = 0
        repeat(chunks) { c ->
            val bytes = ByteArray(chunkBytes) { ((it * 7 + c) % 251).toByte() }
            expected.write(bytes)
            val input = ByteBuffer.allocateDirect(chunkBytes).order(ByteOrder.nativeOrder()).put(bytes)
            input.flip()
            while (input.hasRemaining()) {
                pipeline.queueInput(input)
                drain(pipeline, actual)
                check(++guard < 100_000) { "pipeline bloqué" }
            }
        }
        pipeline.queueEndOfStream()
        while (!pipeline.isEnded) {
            drain(pipeline, actual)
            check(++guard < 100_000) { "fin de flux jamais atteinte" }
        }
        return expected.toByteArray() to actual.toByteArray()
    }

    private fun drain(pipeline: AudioProcessingPipeline, out: ByteArrayOutputStream) {
        val output = pipeline.output
        val bytes = ByteArray(output.remaining())
        output.get(bytes)
        out.write(bytes)
    }

    @Test
    fun `karaoke coupe en cours de morceau - signal intact`() {
        // Allumé à la configuration (donc dans le pipeline), puis éteint : le signal
        // doit ressortir tel quel, sans blocage ni perte.
        val processor = VocalRemoverAudioProcessor().apply { setEnabled(true) }
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(processor))
        pipeline.configure(format)
        pipeline.flush()
        processor.setEnabled(false)
        val (expected, actual) = run(pipeline)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `karaoke allume - flux traite jusqu'au bout`() {
        val processor = VocalRemoverAudioProcessor().apply { setEnabled(true) }
        val (expected, actual) = run(processor)
        assertTrue(actual.size == expected.size)
    }
}
