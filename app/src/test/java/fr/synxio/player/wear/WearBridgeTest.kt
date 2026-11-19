package fr.synxio.player.wear

import fr.synxio.player.wear.protocol.WearState
import fr.synxio.player.widget.WidgetSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearBridgeTest {

    private class FakePublisher : WearStatePublisher {
        val published = mutableListOf<Triple<WearState, ByteArray?, ByteArray?>>()
        var failWith: Exception? = null
        override suspend fun publish(state: WearState, art: ByteArray?, artBlurred: ByteArray?) {
            failWith?.let { throw it }
            published += Triple(state, art, artBlurred)
        }
    }

    private class FakeArt : WearArtPreparer {
        var calls = 0
        override fun prepare(artUri: String?, key: String): WearArt {
            calls++
            return WearArt(accent = 0x11, accentLight = 0x22, art = byteArrayOf(1, 2), artBlurred = byteArrayOf(3))
        }
    }

    private var now = 1_000_000L
    private var volume = 0.5f
    private val publisher = FakePublisher()
    private val art = FakeArt()
    private val bridge = WearBridge(publisher, art, volume = { volume }, clock = { now })

    private val snapshot = WidgetSnapshot(
        mediaId = "42", title = "Titre", artist = "Artiste", isPlaying = true,
        positionMs = 30_000, durationMs = 120_000, capturedAtMs = 1_000_000, favorite = false, hasNext = true,
    )

    @Test
    fun `la photo est convertie avec volume, accents et pochette`() = runTest {
        bridge.publish(snapshot, "content://art/42")
        val (state, bytes, blurred) = publisher.published.single()
        assertEquals("42", state.mediaId)
        assertEquals("Titre", state.title)
        assertEquals("Artiste", state.artist)
        assertTrue(state.isPlaying)
        assertEquals(0.5f, state.volume, 0.0001f)
        assertEquals(0x11, state.accent)
        assertEquals(0x22, state.accentLight)
        assertEquals(30_000L, state.positionMs)
        assertEquals(120_000L, state.durationMs)
        assertTrue(state.hasNext)
        assertArrayEquals(byteArrayOf(1, 2), bytes)
        assertArrayEquals(byteArrayOf(3), blurred)
    }

    @Test
    fun `deux photos equivalentes ne publient qu une fois`() = runTest {
        bridge.publish(snapshot, "content://art/42")
        now += 10_000
        bridge.publish(snapshot.copy(positionMs = 40_000, capturedAtMs = now), "content://art/42")
        assertEquals(1, publisher.published.size)
    }

    @Test
    fun `la pochette n est preparee que quand le titre change`() = runTest {
        bridge.publish(snapshot, "content://art/42")
        now += 1_000
        bridge.publish(snapshot.copy(favorite = true, capturedAtMs = now), "content://art/42")
        assertEquals(2, publisher.published.size)
        assertEquals(1, art.calls)
        assertNull(publisher.published[1].second)
        assertNull(publisher.published[1].third)

        now += 1_000
        bridge.publish(snapshot.copy(mediaId = "43", capturedAtMs = now), "content://art/43")
        assertEquals(2, art.calls)
        assertArrayEquals(byteArrayOf(1, 2), publisher.published[2].second)
    }

    @Test
    fun `une photo vide publie un etat vide sans preparer d image`() = runTest {
        bridge.publish(WidgetSnapshot(), null)
        assertTrue(publisher.published.single().first.isEmpty)
        assertEquals(0, art.calls)
    }

    @Test
    fun `publishVolumeOnly reemet le dernier etat avec le volume courant`() = runTest {
        bridge.publish(snapshot, "content://art/42")
        volume = 0.8f
        bridge.publishVolumeOnly()
        assertEquals(2, publisher.published.size)
        assertEquals(0.8f, publisher.published[1].first.volume, 0.0001f)
        assertEquals("42", publisher.published[1].first.mediaId)
        assertNull(publisher.published[1].second)
    }

    @Test
    fun `publishVolumeOnly sans etat precedent ne fait rien`() = runTest {
        bridge.publishVolumeOnly()
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `une exception du publieur est avalee`() = runTest {
        publisher.failWith = IllegalStateException("Play Services absents")
        bridge.publish(snapshot, "content://art/42")
        // Pas d'exception remontée : le service continue.
        publisher.failWith = null
        now += 1_000
        bridge.publish(snapshot.copy(favorite = true, capturedAtMs = now), "content://art/42")
        assertEquals(1, publisher.published.size)
    }
}
