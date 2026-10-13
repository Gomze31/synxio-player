package fr.synxio.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import fr.synxio.player.playback.effects.VocalRemoverAudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VocalRemoverAudioProcessorTest {

    private val stereo16 = AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

    private fun pcm(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it) }
            flip()
        }

    private fun ByteBuffer.shorts(): ShortArray {
        val view = duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        return ShortArray(view.remaining()) { view.get() }
    }

    @Test
    fun `karaoke eteint - le processeur reste hors du pipeline`() {
        val processor = VocalRemoverAudioProcessor()
        processor.configure(stereo16)
        assertFalse(processor.isActive())
    }

    @Test
    fun `karaoke allume - voix centrale annulee et entree consommee`() {
        val processor = VocalRemoverAudioProcessor().apply { setEnabled(true) }
        processor.configure(stereo16)
        assertTrue(processor.isActive())

        val input = pcm(1000, 1000, 3000, 1000)
        processor.queueInput(input)
        assertFalse("l'entrée doit être entièrement consommée", input.hasRemaining())
        // (L - R) / 2 recopié sur les deux canaux.
        assertArrayEquals(shortArrayOf(0, 0, 1000, 1000), processor.output.shorts())
    }

    @Test
    fun `eteint en cours de morceau - le signal passe intact`() {
        val processor = VocalRemoverAudioProcessor().apply { setEnabled(true) }
        processor.configure(stereo16)
        processor.setEnabled(false)

        val input = pcm(12, -7, 300, 301)
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        assertArrayEquals(shortArrayOf(12, -7, 300, 301), processor.output.shorts())
    }

    @Test
    fun `fin de flux - termine une fois la sortie lue`() {
        val processor = VocalRemoverAudioProcessor().apply { setEnabled(true) }
        processor.configure(stereo16)
        processor.queueInput(pcm(1, 2))
        processor.queueEndOfStream()
        assertFalse(processor.isEnded())
        processor.output
        assertTrue(processor.isEnded())
        assertEquals(AudioProcessor.EMPTY_BUFFER, processor.output)
    }

    @Test
    fun `format mono - ignore`() {
        val processor = VocalRemoverAudioProcessor().apply { setEnabled(true) }
        processor.configure(AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        assertFalse(processor.isActive())
    }
}
