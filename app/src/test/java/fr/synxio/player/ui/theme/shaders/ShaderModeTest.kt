package fr.synxio.player.ui.theme.shaders

import org.junit.Assert.assertEquals
import org.junit.Test

class ShaderModeTest {
    @Test
    fun `API 33 et plus sans drapeau - AGSL`() {
        assertEquals(ShaderMode.AGSL, shaderMode(sdkInt = 33, unavailable = false))
        assertEquals(ShaderMode.AGSL, shaderMode(sdkInt = 36, unavailable = false))
    }

    @Test
    fun `sous API 33 - Canvas quoi qu'il arrive`() {
        assertEquals(ShaderMode.CANVAS, shaderMode(sdkInt = 32, unavailable = false))
        assertEquals(ShaderMode.CANVAS, shaderMode(sdkInt = 28, unavailable = true))
    }

    @Test
    fun `drapeau leve - Canvas meme sur API 33`() {
        assertEquals(ShaderMode.CANVAS, shaderMode(sdkInt = 34, unavailable = true))
    }
}
