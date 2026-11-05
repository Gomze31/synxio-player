package fr.synxio.player.playback.pulse

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PulseSinkTest {
    private fun pipelineWith(sink: PulseSink, format: AudioFormat): AudioProcessingPipeline =
        AudioProcessingPipeline(ImmutableList.of(TeeAudioProcessor(sink))).apply {
            configure(format)
            flush()
        }

    private fun feed(pipeline: AudioProcessingPipeline, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val input = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        input.flip()
        var guard = 0
        while (input.hasRemaining()) {
            pipeline.queueInput(input)
            drain(pipeline, out)
            check(++guard < 10_000)
        }
        pipeline.queueEndOfStream()
        drain(pipeline, out)
        return out.toByteArray()
    }

    private fun drain(pipeline: AudioProcessingPipeline, out: ByteArrayOutputStream) {
        var buffer = pipeline.output
        while (buffer.hasRemaining()) {
            val chunk = ByteArray(buffer.remaining())
            buffer.get(chunk)
            out.write(chunk)
            buffer = pipeline.output
        }
    }

    @Test
    fun `stereo 16 bits - sortie identique, tampon mono moyenne des canaux`() {
        val buffer = PulseRingBuffer(64)
        val sink = PulseSink(buffer)
        val pipeline = pipelineWith(sink, AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        // 4 trames : gauche = 16384, droite = -16384 → mono 0 ; puis gauche = droite = 8192 → 0,25.
        val samples = shortArrayOf(16384, -16384, 16384, -16384, 8192, 8192, 8192, 8192)
        val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder())
            .apply { samples.forEach { putShort(it) } }.array()
        val out = feed(pipeline, bytes)
        assertArrayEquals(bytes, out)
        assertTrue(sink.supported)
        assertEquals(48_000, sink.sampleRate)
        val last = FloatArray(4)
        buffer.readLast(last)
        assertEquals(listOf(0f, 0f, 0.25f, 0.25f), last.toList())
    }

    @Test
    fun `mono float - valeurs copiees telles quelles`() {
        val buffer = PulseRingBuffer(64)
        val sink = PulseSink(buffer)
        val pipeline = pipelineWith(sink, AudioFormat(44_100, 1, C.ENCODING_PCM_FLOAT))
        val values = floatArrayOf(0.5f, -0.5f, 1f)
        val bytes = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder()).apply { values.forEach { putFloat(it) } }.array()
        feed(pipeline, bytes)
        val last = FloatArray(3)
        buffer.readLast(last)
        assertEquals(values.toList(), last.toList())
    }

    @Test
    fun `encodage inconnu - passe-plat, rien d'ecrit`() {
        val buffer = PulseRingBuffer(64)
        val sink = PulseSink(buffer)
        val pipeline = pipelineWith(sink, AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT))
        val bytes = ByteArray(12) { it.toByte() }
        assertArrayEquals(bytes, feed(pipeline, bytes))
        assertFalse(sink.supported)
        assertEquals(0L, buffer.written)
    }
}
