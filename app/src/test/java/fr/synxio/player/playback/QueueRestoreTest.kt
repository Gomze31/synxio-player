package fr.synxio.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRestoreTest {

    private fun available(vararg ids: Long) = ids.associateWith { "s$it" }

    @Test
    fun `file intacte - index et position conserves`() {
        val r = restoreQueue(listOf(1L, 2L, 3L), 1, 42_000L, available(1, 2, 3))!!
        assertEquals(listOf("s1", "s2", "s3"), r.items)
        assertEquals(1, r.index)
        assertEquals(42_000L, r.positionMs)
    }

    @Test
    fun `morceaux supprimes avant le courant - index decale sans depasser la file`() {
        // Ancien plantage : index 4 sur une file devenue longue de 3.
        val r = restoreQueue(listOf(1L, 2L, 3L, 4L, 5L), 4, 10_000L, available(3, 4, 5))!!
        assertEquals(listOf("s3", "s4", "s5"), r.items)
        assertEquals(2, r.index)
        assertEquals("s5", r.items[r.index])
        assertEquals(10_000L, r.positionMs)
    }

    @Test
    fun `morceau courant disparu - on reprend au suivant depuis le debut`() {
        val r = restoreQueue(listOf(1L, 2L, 3L), 1, 90_000L, available(1, 3))!!
        assertEquals("s3", r.items[r.index])
        assertEquals(0L, r.positionMs)
    }

    @Test
    fun `morceau courant et suivants disparus - on se rabat sur le dernier survivant`() {
        val r = restoreQueue(listOf(1L, 2L, 3L), 2, 5_000L, available(1))!!
        assertEquals(0, r.index)
        assertEquals(0L, r.positionMs)
    }

    @Test
    fun `index sauvegarde hors bornes - borne a la file`() {
        val r = restoreQueue(listOf(1L, 2L), 99, 5_000L, available(1, 2))!!
        assertEquals(1, r.index)
        assertEquals(0L, r.positionMs)
    }

    @Test
    fun `plus aucun morceau - null`() {
        assertNull(restoreQueue(listOf(1L, 2L), 0, 0L, available()))
    }

    @Test
    fun `position negative ramenee a zero`() {
        val r = restoreQueue(listOf(1L), 0, -3L, available(1))!!
        assertEquals(0L, r.positionMs)
    }
}
