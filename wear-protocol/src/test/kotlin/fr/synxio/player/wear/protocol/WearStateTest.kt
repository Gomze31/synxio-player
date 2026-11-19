package fr.synxio.player.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearStateTest {

    private val base = WearState(
        mediaId = "42",
        title = "Titre",
        artist = "Artiste",
        positionMs = 30_000,
        durationMs = 120_000,
        capturedAtMs = 1_000_000,
    )

    @Test
    fun `un etat sans mediaId est vide`() {
        assertTrue(WearState().isEmpty)
        assertFalse(base.isEmpty)
    }

    @Test
    fun `en pause la position reste celle de la photo`() {
        val s = base.copy(isPlaying = false)
        assertEquals(30_000L, s.progressAt(1_060_000))
    }

    @Test
    fun `en lecture la position avance avec l horloge`() {
        val s = base.copy(isPlaying = true)
        assertEquals(60_000L, s.progressAt(1_030_000))
        assertEquals(0.5f, s.progressFractionAt(1_030_000), 0.0001f)
    }

    @Test
    fun `la position est bornee a la duree`() {
        val s = base.copy(isPlaying = true)
        assertEquals(120_000L, s.progressAt(9_000_000))
        assertEquals(1f, s.progressFractionAt(9_000_000), 0.0001f)
    }

    @Test
    fun `une horloge qui recule ne fait pas reculer la position`() {
        val s = base.copy(isPlaying = true)
        assertEquals(30_000L, s.progressAt(500_000))
    }

    @Test
    fun `un direct n a pas de fraction de progression`() {
        val s = base.copy(isPlaying = true, live = true)
        assertEquals(0f, s.progressFractionAt(1_030_000), 0.0001f)
    }

    @Test
    fun `un etat en lecture plus vieux que six heures est perime`() {
        val s = base.copy(isPlaying = true)
        assertFalse(s.isStaleAt(1_000_000 + WearState.STALE_MS - 1_000))
        assertTrue(s.isStaleAt(1_000_000 + WearState.STALE_MS + 1_000))
    }

    @Test
    fun `un etat en pause n est jamais perime`() {
        val s = base.copy(isPlaying = false)
        assertFalse(s.isStaleAt(1_000_000 + 10 * WearState.STALE_MS))
    }

    @Test
    fun `la serialisation JSON fait l aller retour`() {
        val s = base.copy(isPlaying = true, favorite = true, volume = 0.4f, accent = 0xFFAA3322.toInt(), hasNext = true)
        assertEquals(s, WearState.fromJson(s.toJson()))
    }

    @Test
    fun `un JSON illisible donne null`() {
        assertNull(WearState.fromJson("{pas du json"))
        assertNull(WearState.fromJson(""))
    }

    @Test
    fun `un JSON avec des champs inconnus est accepte`() {
        val json = """{"mediaId":"1","title":"T","champFutur":true}"""
        assertEquals("1", WearState.fromJson(json)?.mediaId)
    }
}
