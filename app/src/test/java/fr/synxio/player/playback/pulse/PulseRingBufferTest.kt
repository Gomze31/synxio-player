package fr.synxio.player.playback.pulse

import org.junit.Assert.assertEquals
import org.junit.Test

class PulseRingBufferTest {
    @Test
    fun `readLast rend les derniers echantillons dans l'ordre`() {
        val buffer = PulseRingBuffer(capacity = 8)
        buffer.write(floatArrayOf(1f, 2f, 3f, 4f, 5f), 5)
        buffer.write(floatArrayOf(6f, 7f, 8f, 9f, 10f), 5)
        val out = FloatArray(4)
        buffer.readLast(out)
        assertEquals(listOf(7f, 8f, 9f, 10f), out.toList())
        assertEquals(10L, buffer.written)
    }

    @Test
    fun `avant remplissage - zeros devant`() {
        val buffer = PulseRingBuffer(capacity = 8)
        buffer.write(floatArrayOf(1f, 2f), 2)
        val out = FloatArray(4)
        buffer.readLast(out)
        assertEquals(listOf(0f, 0f, 1f, 2f), out.toList())
    }

    @Test
    fun `count limite l'ecriture`() {
        val buffer = PulseRingBuffer(capacity = 4)
        buffer.write(floatArrayOf(1f, 2f, 3f), 2)
        assertEquals(2L, buffer.written)
    }
}
