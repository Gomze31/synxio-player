package fr.synxio.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPositionTest {
    @Test
    fun `progression bornee entre 0 et 1`() {
        assertEquals(0.5f, PlayerPosition(positionMs = 50_000, durationMs = 100_000).progress)
        assertEquals(1f, PlayerPosition(positionMs = 150_000, durationMs = 100_000).progress)
        assertEquals(0f, PlayerPosition(positionMs = -5, durationMs = 100_000).progress)
    }

    @Test
    fun `duree inconnue - progression et tampon a zero`() {
        val p = PlayerPosition(positionMs = 30_000, bufferedMs = 40_000, durationMs = 0)
        assertEquals(0f, p.progress)
        assertEquals(0f, p.buffered)
    }

    @Test
    fun `tampon rapporte a la duree`() {
        assertEquals(0.25f, PlayerPosition(bufferedMs = 25_000, durationMs = 100_000).buffered)
    }
}
