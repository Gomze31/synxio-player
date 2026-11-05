package fr.synxio.player.ui.theme.shaders

import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderSourcesTest {
    private fun check(src: String, vararg uniforms: String) {
        assertTrue(src.contains("half4 main(float2"))
        uniforms.forEach { assertTrue("uniform $it manquant", src.contains("uniform") && src.contains(it)) }
        assertTrue("accolades déséquilibrées", src.count { it == '{' } == src.count { it == '}' })
    }

    @Test
    fun `aurore - uniformes attendus`() = check(ShaderSources.AURORA, "resolution", "c0", "c1", "c2", "r0", "r1", "r2", "col0", "col1", "col2", "base", "grain", "dispersion", "time")

    @Test
    fun `verre - uniformes attendus`() = check(ShaderSources.GLASS, "origin", "wobble", "dispersion", "time", "c0", "col2", "base")

    @Test
    fun `verre - pas d'uniforme resolution, il n'y est pas lu`() = assertTrue(!ShaderSources.GLASS.contains("resolution"))

    @Test
    fun `canvas - uniformes attendus`() = check(ShaderSources.CANVAS, "resolution", "phase", "contrast", "colA", "colB", "colC")
}
