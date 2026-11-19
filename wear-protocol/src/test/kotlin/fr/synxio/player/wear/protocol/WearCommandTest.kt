package fr.synxio.player.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearCommandTest {

    private val all = listOf(
        WearCommand.PlayPause to "play_pause",
        WearCommand.Next to "next",
        WearCommand.Previous to "previous",
        WearCommand.ToggleFavorite to "toggle_favorite",
        WearCommand.VolumeUp to "volume_up",
        WearCommand.VolumeDown to "volume_down",
        WearCommand.Volume(0.5f) to "volume:0.5",
        WearCommand.Seek(42_000L) to "seek:42000",
        WearCommand.Start("synxio://favorites") to "start:synxio://favorites",
    )

    @Test
    fun `chaque commande s encode comme prevu`() {
        for ((command, text) in all) assertEquals(text, command.encode())
    }

    @Test
    fun `chaque commande fait l aller retour`() {
        for ((command, _) in all) assertEquals(command, WearCommand.parse(command.encode()))
    }

    @Test
    fun `un volume hors bornes ou illisible donne null`() {
        assertNull(WearCommand.parse("volume:1.5"))
        assertNull(WearCommand.parse("volume:-0.1"))
        assertNull(WearCommand.parse("volume:abc"))
        assertNull(WearCommand.parse("volume:"))
    }

    @Test
    fun `les bornes du volume sont acceptees`() {
        assertEquals(WearCommand.Volume(0f), WearCommand.parse("volume:0"))
        assertEquals(WearCommand.Volume(1f), WearCommand.parse("volume:1.0"))
    }

    @Test
    fun `une position negative ou illisible donne null`() {
        assertNull(WearCommand.parse("seek:-1"))
        assertNull(WearCommand.parse("seek:12.5"))
        assertNull(WearCommand.parse("seek:"))
    }

    @Test
    fun `un start sans identifiant donne null`() {
        assertNull(WearCommand.parse("start:"))
        assertNull(WearCommand.parse("start"))
    }

    @Test
    fun `une chaine inconnue donne null`() {
        assertNull(WearCommand.parse(""))
        assertNull(WearCommand.parse("stop"))
        assertNull(WearCommand.parse("PLAY_PAUSE"))
    }

    @Test
    fun `les identifiants de lancement sont ceux de l arbre de navigation`() {
        assertEquals("synxio://last-queue", WearStartIds.LAST_QUEUE)
        assertEquals("synxio://favorites", WearStartIds.FAVORITES)
        assertEquals("synxio://shuffle/all", WearStartIds.SHUFFLE_ALL)
        assertEquals("synxio://surprise", WearStartIds.SURPRISE)
    }
}
