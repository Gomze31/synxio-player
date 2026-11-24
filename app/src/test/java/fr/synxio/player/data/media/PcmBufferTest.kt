package fr.synxio.player.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ShortBuffer

class PcmBufferTest {

    @Test
    fun `mono - recopie telle quelle`() {
        val buffer = PcmBuffer(initialCapacity = 4)
        buffer.append(ShortBuffer.wrap(shortArrayOf(1, -2, 3)), channels = 1)
        assertArrayEquals(shortArrayOf(1, -2, 3), buffer.toArray())
    }

    @Test
    fun `stereo - moyenne par trame`() {
        val buffer = PcmBuffer()
        buffer.append(ShortBuffer.wrap(shortArrayOf(1000, 3000, -4000, 2000)), channels = 2)
        assertArrayEquals(shortArrayOf(2000, -1000), buffer.toArray())
    }

    @Test
    fun `croissance - au-dela de la capacite initiale, rien n'est perdu`() {
        val buffer = PcmBuffer(initialCapacity = 8)
        val input = ShortArray(65_537) { (it % 100).toShort() }
        buffer.append(ShortBuffer.wrap(input.copyOfRange(0, 40_000)), channels = 1)
        buffer.append(ShortBuffer.wrap(input.copyOfRange(40_000, 65_537)), channels = 1)
        assertEquals(65_537, buffer.size)
        assertArrayEquals(input, buffer.toArray())
    }

    @Test
    fun `trame incomplete - ignoree`() {
        val buffer = PcmBuffer()
        buffer.append(ShortBuffer.wrap(shortArrayOf(10, 20, 30)), channels = 2)
        assertArrayEquals(shortArrayOf(15), buffer.toArray())
    }
}
