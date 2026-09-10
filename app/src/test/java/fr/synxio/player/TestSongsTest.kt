package fr.synxio.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TestSongsTest {
    @Test
    fun `la fabrique applique les valeurs par defaut et les surcharges`() {
        val s = song(id = 7, title = "Ainsi bas la vida")
        assertEquals(7L, s.id)
        assertEquals("Ainsi bas la vida", s.title)
        assertEquals("/music/7.mp3", s.path)
        assertEquals(180_000L, s.durationMs)
    }
}
