package fr.synxio.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSheetStateTest {
    private val sheet = PlayerSheetState(screenHeightPx = 2000f)

    @Test
    fun `glisser vers le bas reduit l'expansion proportionnellement a la hauteur`() {
        assertEquals(0.75f, sheet.dragTo(startExpansion = 1f, dragPx = 500f), 1e-4f)
        assertEquals(0f, sheet.dragTo(1f, 5000f), 1e-4f)
        assertEquals(1f, sheet.dragTo(1f, -300f), 1e-4f)
    }

    @Test
    fun `glisser vers le haut depuis le mini-lecteur ouvre`() {
        assertEquals(0.25f, sheet.dragTo(startExpansion = 0f, dragPx = -500f), 1e-4f)
    }

    @Test
    fun `velocite au-dessus du seuil - la direction decide`() {
        assertEquals(0f, sheet.settle(1f, 0.9f, velocityPxPerSec = 1_500f))
        assertEquals(1f, sheet.settle(0f, 0.1f, velocityPxPerSec = -1_500f))
    }

    @Test
    fun `sans velocite - seuil de 30 pour cent depuis le depart`() {
        assertEquals(1f, sheet.settle(0f, 0.3f, 0f))
        assertEquals(0f, sheet.settle(0f, 0.29f, 0f))
        assertEquals(0f, sheet.settle(1f, 0.7f, 0f))
        assertEquals(1f, sheet.settle(1f, 0.71f, 0f))
    }

    @Test
    fun `balayage vers le bas seulement depuis les 65 pour cent superieurs`() {
        assertTrue(sheet.canStartDrag(0f))
        assertTrue(sheet.canStartDrag(0.65f))
        assertFalse(sheet.canStartDrag(0.66f))
    }
}
