package fr.synxio.player.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Déplacement dans une playlist dont certains titres sont devenus indisponibles. */
class PlaylistMoveTest {

    // Les minuscules sont des titres indisponibles (masqués dans l'interface).
    private val visible: (String) -> Boolean = { it.first().isUpperCase() }

    @Test
    fun `deplacer vers le bas sans titre masque`() {
        assertEquals(listOf("B", "C", "A"), moveVisible(listOf("A", "B", "C"), visible, 0, 2))
        assertEquals(listOf("B", "A", "C"), moveVisible(listOf("A", "B", "C"), visible, 0, 1))
    }

    @Test
    fun `deplacer vers le haut sans titre masque`() {
        assertEquals(listOf("C", "A", "B"), moveVisible(listOf("A", "B", "C"), visible, 2, 0))
    }

    @Test
    fun `les titres masques restent en place et ne sont jamais perdus`() {
        val entries = listOf("A", "x", "B", "y", "C")
        val moved = moveVisible(entries, visible, 0, 2)
        assertEquals(entries.sorted(), moved.sorted())
        assertEquals(listOf("B", "C", "A"), moved.filter(visible))
        // L'ordre relatif des masqués est conservé.
        assertEquals(listOf("x", "y"), moved.filterNot(visible))
    }

    @Test
    fun `un deplacement impossible rend la liste intacte`() {
        val entries = listOf("A", "B")
        assertSame(entries, moveVisible(entries, visible, 0, 5))
        assertSame(entries, moveVisible(entries, visible, 1, 1))
        assertSame(entries, moveVisible(entries, visible, -1, 0))
    }
}
