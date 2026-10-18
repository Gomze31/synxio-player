package fr.synxio.player.playback

import android.view.KeyEvent
import fr.synxio.player.playback.MediaButtonInterpreter.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaButtonInterpreterTest {

    private val hook = KeyEvent.KEYCODE_HEADSETHOOK
    private val playPause = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    private val next = KeyEvent.KEYCODE_MEDIA_NEXT
    private val previous = KeyEvent.KEYCODE_MEDIA_PREVIOUS
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    private val buttons = MediaButtonInterpreter()

    /** Appui complet (enfoncé puis relâché) ; renvoie l'issue de l'appui. */
    private fun click(key: Int, at: Long): MediaButtonInterpreter.Outcome {
        val outcome = buttons.onKeyEvent(key, down, 0, at)
        buttons.onKeyEvent(key, up, 0, at + 60)
        return outcome
    }

    @Test
    fun `un appui - lecture pause, une fois la fenetre passee`() {
        val outcome = click(hook, 0)
        assertTrue(outcome.handled)
        assertNull(outcome.command)
        assertEquals(MediaButtonInterpreter.MULTI_CLICK_WINDOW_MS, outcome.resolveAt)
        assertNull(buttons.onTimeout(200))
        assertEquals(Command.PlayPause, buttons.onTimeout(MediaButtonInterpreter.MULTI_CLICK_WINDOW_MS))
    }

    @Test
    fun `deux appuis - titre suivant`() {
        click(playPause, 0)
        val second = click(playPause, 250)
        assertEquals(250 + MediaButtonInterpreter.MULTI_CLICK_WINDOW_MS, second.resolveAt)
        assertEquals(Command.Next, buttons.onTimeout(second.resolveAt!!))
    }

    @Test
    fun `trois appuis - titre precedent, sans attendre`() {
        click(hook, 0)
        click(hook, 200)
        val third = click(hook, 400)
        assertEquals(Command.Previous, third.command)
        assertTrue(third.cancelPending)
        assertNull(buttons.onTimeout(2_000))
    }

    @Test
    fun `le relachement n'annule pas l'attente de la serie`() {
        buttons.onKeyEvent(hook, down, 0, 0)
        val release = buttons.onKeyEvent(hook, up, 0, 80)
        assertTrue(release.handled)
        assertFalse(release.cancelPending)
        assertNull(release.resolveAt)
    }

    @Test
    fun `deux appuis trop espaces - deux lectures pause distinctes`() {
        click(hook, 0)
        assertEquals(Command.PlayPause, buttons.onTimeout(MediaButtonInterpreter.MULTI_CLICK_WINDOW_MS))
        val later = click(hook, 2_000)
        assertEquals(Command.PlayPause, buttons.onTimeout(later.resolveAt!!))
    }

    @Test
    fun `bouton maintenu - les repetitions ne comptent pas comme des appuis`() {
        buttons.onKeyEvent(hook, down, 0, 0)
        repeat(10) { buttons.onKeyEvent(hook, down, it + 1, 500L + it * 50) }
        assertEquals(Command.PlayPause, buttons.onTimeout(MediaButtonInterpreter.MULTI_CLICK_WINDOW_MS))
    }

    @Test
    fun `gestes desactives - chaque appui bascule tout de suite, suivant suit Media3`() {
        buttons.gesturesEnabled = false
        val outcome = buttons.onKeyEvent(hook, down, 0, 0)
        assertEquals(Command.PlayPause, outcome.command)
        assertNull(outcome.resolveAt)
        assertFalse(buttons.onKeyEvent(next, down, 0, 0).handled)
    }

    @Test
    fun `suivant - appui bref, change de titre au relachement`() {
        val press = buttons.onKeyEvent(next, down, 0, 0)
        assertTrue(press.handled)
        assertNull(press.command)
        val release = buttons.onKeyEvent(next, up, 0, 120)
        assertEquals(Command.Next, release.command)
        assertTrue(release.cancelPending)
    }

    @Test
    fun `suivant - appareil qui n'envoie que l'appui, tranche a l'echeance`() {
        val press = buttons.onKeyEvent(previous, down, 0, 1_000)
        assertEquals(1_000 + MediaButtonInterpreter.LONG_PRESS_MS, press.resolveAt)
        assertEquals(Command.Previous, buttons.onTimeout(press.resolveAt!!))
    }

    @Test
    fun `suivant maintenu - avance rapide par pas, sans changer de titre`() {
        buttons.onKeyEvent(next, down, 0, 0)
        val commands = (1..30).mapNotNull { i ->
            val outcome = buttons.onKeyEvent(next, down, i, 500L + i * 50)
            assertTrue(outcome.cancelPending)
            outcome.command
        }
        // Répétitions de 550 à 2 000 ms : première avance à 700 ms, puis toutes les 400 ms.
        assertEquals(List(4) { Command.SeekBy(MediaButtonInterpreter.SEEK_STEP_MS) }, commands)
        assertNull(buttons.onKeyEvent(next, up, 0, 2_100).command)
        assertNull(buttons.onTimeout(5_000))
    }

    @Test
    fun `precedent maintenu - retour rapide`() {
        buttons.onKeyEvent(previous, down, 0, 0)
        val seek = buttons.onKeyEvent(previous, down, 5, 800).command
        assertEquals(Command.SeekBy(-MediaButtonInterpreter.SEEK_STEP_MS), seek)
    }

    @Test
    fun `autres touches - traitement par defaut`() {
        assertFalse(buttons.onKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY, down, 0, 0).handled)
        assertFalse(buttons.onKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE, down, 0, 0).handled)
        assertFalse(buttons.onKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, down, 0, 0).handled)
    }
}
