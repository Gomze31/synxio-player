package fr.synxio.player.data.model

import fr.synxio.player.data.media.Chapter
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookPlaceTest {

    @Test
    fun `signet - titre du chapitre et position dans ce chapitre`() {
        val files = listOf(
            song(id = 1, title = "Fichier 1", track = 1, durationMs = 600_000),
            song(id = 2, title = "Fichier 2", track = 2, durationMs = 1_200_000),
        )
        val embedded = mapOf(2L to listOf(Chapter("A", 0, 300_000), Chapter("B", 300_000, 1_200_000)))
        assertEquals("Fichier 1 · 1:05", bookPlace(files, emptyMap(), 1, 65_000))
        assertEquals("B · 2:00", bookPlace(files, embedded, 2, 420_000))
        assertNull(bookPlace(files, embedded, 99, 0))
    }
}
