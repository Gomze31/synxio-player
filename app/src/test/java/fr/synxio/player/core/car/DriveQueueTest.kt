package fr.synxio.player.core.car

import fr.synxio.player.data.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveQueueTest {
    @Test
    fun `les trois suivants`() {
        assertEquals(listOf(3, 4, 5), DriveQueue.upcoming(size = 10, index = 2, repeat = RepeatMode.OFF))
    }

    @Test
    fun `fin de file - moins de trois, puis aucun`() {
        assertEquals(listOf(9), DriveQueue.upcoming(size = 10, index = 8, repeat = RepeatMode.OFF))
        assertEquals(emptyList<Int>(), DriveQueue.upcoming(size = 10, index = 9, repeat = RepeatMode.OFF))
    }

    @Test
    fun `repetition de la file - on reboucle sans repasser par le titre courant`() {
        assertEquals(listOf(9, 0, 1), DriveQueue.upcoming(size = 10, index = 8, repeat = RepeatMode.ALL))
        assertEquals(listOf(1), DriveQueue.upcoming(size = 2, index = 0, repeat = RepeatMode.ALL))
        assertEquals(emptyList<Int>(), DriveQueue.upcoming(size = 1, index = 0, repeat = RepeatMode.ALL))
    }

    @Test
    fun `repetition du titre, file vide ou index hors file - rien`() {
        assertEquals(emptyList<Int>(), DriveQueue.upcoming(size = 10, index = 2, repeat = RepeatMode.ONE))
        assertEquals(emptyList<Int>(), DriveQueue.upcoming(size = 0, index = 0, repeat = RepeatMode.OFF))
        assertEquals(emptyList<Int>(), DriveQueue.upcoming(size = 5, index = -1, repeat = RepeatMode.OFF))
    }
}
