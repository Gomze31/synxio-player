package fr.synxio.player.wear

import fr.synxio.player.wear.protocol.WearState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearStateDedupTest {

    private val base = WearState(
        mediaId = "42", title = "Titre", artist = "Artiste",
        isPlaying = true, positionMs = 30_000, durationMs = 120_000, capturedAtMs = 1_000_000,
    )

    @Test
    fun `sans precedent on publie`() {
        assertTrue(WearStateDedup.needsPublish(null, base, 1_000_000))
    }

    @Test
    fun `meme etat, position coherente avec l horloge, pas de publication`() {
        // 10 s plus tard, le lecteur dit 40 s : exactement ce que la montre extrapole.
        val next = base.copy(positionMs = 40_000, capturedAtMs = 1_010_000)
        assertFalse(WearStateDedup.needsPublish(base, next, 1_010_000))
    }

    @Test
    fun `position decalee de plus de deux secondes, on publie`() {
        val next = base.copy(positionMs = 45_000, capturedAtMs = 1_010_000)
        assertTrue(WearStateDedup.needsPublish(base, next, 1_010_000))
    }

    @Test
    fun `changement de favori, on publie`() {
        val next = base.copy(favorite = true, positionMs = 31_000, capturedAtMs = 1_001_000)
        assertTrue(WearStateDedup.needsPublish(base, next, 1_001_000))
    }

    @Test
    fun `passage en pause, on publie`() {
        val next = base.copy(isPlaying = false, positionMs = 31_000, capturedAtMs = 1_001_000)
        assertTrue(WearStateDedup.needsPublish(base, next, 1_001_000))
    }

    @Test
    fun `changement de volume, on publie`() {
        val next = base.copy(volume = 0.6f, positionMs = 31_000, capturedAtMs = 1_001_000)
        assertTrue(WearStateDedup.needsPublish(base, next, 1_001_000))
    }

    @Test
    fun `en pause la position fait foi`() {
        val paused = base.copy(isPlaying = false)
        val moved = paused.copy(positionMs = 31_500, capturedAtMs = 1_005_000)
        assertTrue(WearStateDedup.needsPublish(paused, moved, 1_005_000))
        val same = paused.copy(capturedAtMs = 1_005_000)
        assertFalse(WearStateDedup.needsPublish(paused, same, 1_005_000))
    }

    @Test
    fun `etat vide apres un etat plein, on publie`() {
        assertTrue(WearStateDedup.needsPublish(base, WearState(), 1_001_000))
    }
}
