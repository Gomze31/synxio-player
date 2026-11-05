package fr.synxio.player.core.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarActionsTest {

    private val off = CarSettings(
        notify = false, autoPlay = false, playSource = CarPlaySource.RESUME, playlistId = -1L,
        openDriveMode = false, exitWithCar = false, pauseOnDisconnect = false,
    )

    @Test
    fun `tout desactive - rien a la connexion, seulement le retrait de la notification a la deconnexion`() {
        assertTrue(carActions(CarEvent.CONNECTED, off, foreground = true).isEmpty())
        assertEquals(listOf(CarAction.DismissNotification), carActions(CarEvent.DISCONNECTED, off, foreground = false))
    }

    @Test
    fun `proposer - notification, au premier plan ou non`() {
        val s = off.copy(notify = true)
        assertEquals(listOf(CarAction.Notify), carActions(CarEvent.CONNECTED, s, foreground = true))
        assertEquals(listOf(CarAction.Notify), carActions(CarEvent.CONNECTED, s, foreground = false))
    }

    @Test
    fun `lancer la lecture - avant la notification, avec sa source et sa playlist`() {
        val s = off.copy(notify = true, autoPlay = true, playSource = CarPlaySource.PLAYLIST, playlistId = 7L)
        assertEquals(
            listOf(CarAction.StartPlayback(CarPlaySource.PLAYLIST, 7L), CarAction.Notify),
            carActions(CarEvent.CONNECTED, s, foreground = false),
        )
    }

    @Test
    fun `ouvrir automatiquement - l'ecran au premier plan, la notification sinon, meme sans proposer`() {
        val s = off.copy(openDriveMode = true)
        assertEquals(listOf(CarAction.OpenDriveMode), carActions(CarEvent.CONNECTED, s, foreground = true))
        assertEquals(listOf(CarAction.Notify), carActions(CarEvent.CONNECTED, s, foreground = false))
    }

    @Test
    fun `ouvrir et proposer - une seule notification en arriere-plan, pas de doublon`() {
        val s = off.copy(notify = true, openDriveMode = true)
        assertEquals(listOf(CarAction.Notify), carActions(CarEvent.CONNECTED, s, foreground = false))
        assertEquals(listOf(CarAction.OpenDriveMode), carActions(CarEvent.CONNECTED, s, foreground = true))
    }

    @Test
    fun `deconnexion - quitter puis pause, apres le retrait de la notification`() {
        val s = off.copy(exitWithCar = true, pauseOnDisconnect = true)
        assertEquals(
            listOf(CarAction.DismissNotification, CarAction.ExitDriveMode, CarAction.Pause),
            carActions(CarEvent.DISCONNECTED, s, foreground = true),
        )
        assertEquals(
            listOf(CarAction.DismissNotification, CarAction.Pause),
            carActions(CarEvent.DISCONNECTED, off.copy(pauseOnDisconnect = true), foreground = false),
        )
    }
}
