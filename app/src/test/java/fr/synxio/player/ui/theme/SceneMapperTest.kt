package fr.synxio.player.ui.theme

import fr.synxio.player.playback.pulse.AudioPulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMapperTest {
    private val loud = AudioPulse(bass = 1f, mid = 1f, treble = 1f, rms = 1f, onset = 1f)

    @Test
    fun `repos - parametres neutres`() {
        val p = mapScene(AudioPulse.SILENT, silentForMs = 0, previous = SceneParams.REST)
        assertEquals(listOf(1f, 1f, 1f), p.haloScale.toList())
        assertEquals(0.85f, p.brightness, 1e-4f)
        assertEquals(0f, p.flash, 1e-4f)
        assertFalse(p.frozen)
    }

    @Test
    fun `plein pouls - bornes atteintes apres lissage`() {
        var p = SceneParams.REST
        repeat(30) { p = mapScene(loud, 0, p) }
        assertEquals(1.25f, p.haloScale[0], 1e-3f)
        assertEquals(1.15f, p.haloScale[1], 1e-3f)
        assertEquals(1.10f, p.haloScale[2], 1e-3f)
        assertEquals(1.15f, p.brightness, 1e-3f)
        assertEquals(1f, p.shimmer, 1e-3f)
        assertEquals(1f, p.flash, 1e-3f)
    }

    @Test
    fun `continuite - pas de saut de plus de 0,2 par image`() {
        val p = mapScene(loud, 0, SceneParams.REST)
        assertTrue(p.haloScale[0] - 1f <= 0.2f)
        assertTrue(p.brightness - 0.85f <= 0.2f)
        assertTrue(p.flash <= 0.6f)
    }

    @Test
    fun `silence d'une seconde - fige`() {
        assertFalse(mapScene(AudioPulse.SILENT, 999, SceneParams.REST).frozen)
        assertTrue(mapScene(AudioPulse.SILENT, 1_000, SceneParams.REST).frozen)
    }

    @Test
    fun `coupures - une seule condition suffit`() {
        val ok = SceneConditions(
            reduceMotion = false, glowEffects = true, sceneReactive = true, powerSave = false,
            batteryLow = false, screenOn = true, playing = true, surfaceVisible = true,
        )
        assertTrue(sceneAllowed(ok))
        assertFalse(sceneAllowed(ok.copy(reduceMotion = true)))
        assertFalse(sceneAllowed(ok.copy(glowEffects = false)))
        assertFalse(sceneAllowed(ok.copy(sceneReactive = false)))
        assertFalse(sceneAllowed(ok.copy(powerSave = true)))
        assertFalse(sceneAllowed(ok.copy(batteryLow = true)))
        assertFalse(sceneAllowed(ok.copy(screenOn = false)))
        assertFalse(sceneAllowed(ok.copy(playing = false)))
        assertFalse(sceneAllowed(ok.copy(surfaceVisible = false)))
    }
}
