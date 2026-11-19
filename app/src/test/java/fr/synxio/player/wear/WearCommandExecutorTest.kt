package fr.synxio.player.wear

import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearStartIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCommandExecutorTest {

    private class FakeTarget(override var hasQueue: Boolean = true) : PlaybackTarget {
        val calls = mutableListOf<String>()
        override fun playPause() { calls += "playPause" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun seekTo(ms: Long) { calls += "seekTo:$ms" }
        override fun toggleFavorite() { calls += "toggleFavorite" }
        override fun start(mediaId: String) { calls += "start:$mediaId" }
    }

    private class FakeVolume : VolumeControl {
        val calls = mutableListOf<String>()
        override fun raise() { calls += "raise" }
        override fun lower() { calls += "lower" }
        override fun set(level: Float) { calls += "set:$level" }
    }

    private val target = FakeTarget()
    private val volume = FakeVolume()
    private val executor = WearCommandExecutor(target, volume)

    @Test
    fun `les commandes de transport vont au lecteur`() {
        executor.execute(WearCommand.PlayPause)
        executor.execute(WearCommand.Next)
        executor.execute(WearCommand.Previous)
        executor.execute(WearCommand.Seek(5_000))
        executor.execute(WearCommand.ToggleFavorite)
        executor.execute(WearCommand.Start(WearStartIds.FAVORITES))
        assertEquals(
            listOf("playPause", "next", "previous", "seekTo:5000", "toggleFavorite", "start:synxio://favorites"),
            target.calls,
        )
        assertTrue(volume.calls.isEmpty())
    }

    @Test
    fun `lecture pause sans file reprend la derniere file`() {
        target.hasQueue = false
        executor.execute(WearCommand.PlayPause)
        assertEquals(listOf("start:synxio://last-queue"), target.calls)
    }

    @Test
    fun `suivant precedent favori et seek sans file ne font rien`() {
        target.hasQueue = false
        executor.execute(WearCommand.Next)
        executor.execute(WearCommand.Previous)
        executor.execute(WearCommand.ToggleFavorite)
        executor.execute(WearCommand.Seek(1))
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun `les commandes de volume renvoient vrai et vont au volume`() {
        assertTrue(executor.execute(WearCommand.VolumeUp))
        assertTrue(executor.execute(WearCommand.VolumeDown))
        assertTrue(executor.execute(WearCommand.Volume(0.25f)))
        assertEquals(listOf("raise", "lower", "set:0.25"), volume.calls)
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun `les autres commandes renvoient faux`() {
        assertFalse(executor.execute(WearCommand.PlayPause))
        assertFalse(executor.execute(WearCommand.Start(WearStartIds.SURPRISE)))
    }
}
