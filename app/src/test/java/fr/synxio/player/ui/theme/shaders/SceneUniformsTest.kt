package fr.synxio.player.ui.theme.shaders

import fr.synxio.player.ui.theme.SceneParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneUniformsTest {
    private val colors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f)
    private val alphas = floatArrayOf(0.36f, 0.28f, 0.16f)
    private val base = floatArrayOf(0.05f, 0.04f, 0.06f, 1f)

    @Test
    fun `repos - rayons et alphas de base`() {
        val u = SceneUniforms()
        fillSceneUniforms(u, 1080f, 2400f, t = 0f, params = SceneParams.REST, colorsRgba = colors, alphas = alphas, baseRgba = base, timeSeconds = 3f)
        assertEquals(2400f * 0.78f, u.radii[0], 1e-3f)
        assertEquals(2400f * 0.70f, u.radii[1], 1e-3f)
        assertEquals(2400f * 0.55f, u.radii[2], 1e-3f)
        assertEquals(0.36f, u.colors[3], 1e-4f)
        assertEquals(0.28f, u.colors[7], 1e-4f)
        assertEquals(0.16f, u.colors[11], 1e-4f)
        assertEquals(1080f, u.width, 0f)
        assertEquals(3f, u.time, 0f)
        assertEquals(0.05f, u.base[0], 0f)
    }

    @Test
    fun `plein pouls - rayons dilates, alphas bornes`() {
        val u = SceneUniforms()
        val p = SceneParams(floatArrayOf(1.25f, 1.15f, 1.10f), brightness = 1.15f, shimmer = 1f, flash = 1f, frozen = false)
        fillSceneUniforms(u, 1080f, 2400f, 0f, p, colors, floatArrayOf(0.9f, 0.9f, 0.9f), base, 0f)
        assertEquals(2400f * 0.78f * 1.25f, u.radii[0], 1e-2f)
        assertTrue(u.colors[3] <= 1f && u.colors[7] <= 1f && u.colors[11] <= 1f)
        assertTrue(u.colors[3] > 0.9f)
    }

    @Test
    fun `centres - dans l'ecran et continus en t`() {
        val u1 = SceneUniforms()
        val u2 = SceneUniforms()
        fillSceneUniforms(u1, 1000f, 2000f, 1.00f, SceneParams.REST, colors, alphas, base, 0f)
        fillSceneUniforms(u2, 1000f, 2000f, 1.01f, SceneParams.REST, colors, alphas, base, 0f)
        for (i in 0 until 3) {
            assertTrue(u1.centers[2 * i] in -200f..1200f && u1.centers[2 * i + 1] in -200f..2200f)
            assertTrue(kotlin.math.abs(u1.centers[2 * i] - u2.centers[2 * i]) < 5f)
        }
    }
}
