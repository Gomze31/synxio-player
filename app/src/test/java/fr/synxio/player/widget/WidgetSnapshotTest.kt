package fr.synxio.player.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {

    private val base = WidgetSnapshot(
        mediaId = "42",
        title = "Titre",
        positionMs = 30_000,
        durationMs = 120_000,
        capturedAtMs = 1_000_000,
    )

    @Test
    fun `en pause la progression reste celle de la photo`() {
        val s = base.copy(isPlaying = false)
        assertEquals(0.25f, s.progressAt(1_060_000), 0.0001f)
    }

    @Test
    fun `en lecture la progression avance avec l horloge`() {
        val s = base.copy(isPlaying = true)
        // 30 s + 30 s écoulées depuis la photo = la moitié du morceau.
        assertEquals(0.5f, s.progressAt(1_030_000), 0.0001f)
    }

    @Test
    fun `la progression est bornee a la fin du morceau`() {
        val s = base.copy(isPlaying = true)
        assertEquals(1f, s.progressAt(9_000_000), 0.0001f)
    }

    @Test
    fun `une horloge qui recule ne fait pas reculer la barre`() {
        val s = base.copy(isPlaying = true)
        assertEquals(0.25f, s.progressAt(500_000), 0.0001f)
    }

    @Test
    fun `un flux en direct ou sans duree n a pas de progression`() {
        assertEquals(0f, base.copy(live = true).progressAt(1_030_000), 0f)
        assertEquals(0f, base.copy(durationMs = 0).progressAt(1_030_000), 0f)
    }

    @Test
    fun `une photo vide signale l absence de lecture`() {
        assertTrue(WidgetSnapshot().isEmpty)
        assertTrue(!base.isEmpty)
    }
}
