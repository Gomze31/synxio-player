package fr.synxio.player.playback

import android.view.KeyEvent

/**
 * Gestes des boutons de casque, filaires ou Bluetooth, sans dépendance au temps réel.
 *
 * - Bouton unique (télécommande filaire, lecture/pause Bluetooth) : un appui met en
 *   pause ou relance, deux passent au titre suivant, trois reviennent au précédent.
 * - Suivant / précédent : un appui change de titre ; maintenu, il avance ou recule dans
 *   le morceau par pas réguliers, comme une avance rapide.
 *
 * Le service relaie chaque évènement avec l'heure, puis rappelle [onTimeout] à l'instant
 * demandé par [Outcome.resolveAt] : c'est là qu'une série d'appuis est tranchée.
 */
class MediaButtonInterpreter(
    private val multiClickWindowMs: Long = MULTI_CLICK_WINDOW_MS,
    private val longPressMs: Long = LONG_PRESS_MS,
    private val seekRepeatMs: Long = SEEK_REPEAT_MS,
    private val seekStepMs: Long = SEEK_STEP_MS,
) {

    sealed interface Command {
        data object PlayPause : Command
        data object Next : Command
        data object Previous : Command
        data class SeekBy(val deltaMs: Long) : Command
    }

    /**
     * [handled] faux : l'évènement suit le traitement par défaut de Media3.
     * [resolveAt] : heure à laquelle rappeler [onTimeout] (remplace l'attente en cours).
     * [cancelPending] : l'attente en cours n'a plus lieu d'être.
     */
    data class Outcome(
        val handled: Boolean,
        val command: Command? = null,
        val resolveAt: Long? = null,
        val cancelPending: Boolean = false,
    )

    /**
     * Gestes activés (réglage). Désactivés, chaque appui sur le bouton unique bascule
     * lecture/pause, et suivant/précédent gardent le comportement par défaut.
     */
    var gesturesEnabled: Boolean = true

    private var clicks = 0
    private var lastClickAt = 0L

    /** Touche suivant/précédent tenue : code, instant de l'appui, dernière avance. */
    private var heldKey = 0
    private var heldSince = 0L
    private var lastSeekAt = 0L
    private var seeking = false

    fun onKeyEvent(keyCode: Int, action: Int, repeatCount: Int, now: Long): Outcome = when (keyCode) {
        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> onClickKey(action, repeatCount, now)
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
            if (gesturesEnabled) onSkipKey(keyCode, action, repeatCount, now) else Outcome(handled = false)
        else -> Outcome(handled = false)
    }

    /** Fin de l'attente : tranche la série d'appuis ou l'appui sur suivant/précédent. */
    fun onTimeout(now: Long): Command? {
        if (heldKey != 0) {
            // Ni relâchement ni répétition reçus (certains appareils n'envoient que
            // l'appui) : c'était un appui simple.
            if (seeking || now - heldSince < longPressMs) return null
            val key = heldKey
            resetHeld()
            return skipCommand(key)
        }
        if (clicks == 0 || now - lastClickAt < multiClickWindowMs) return null
        val count = clicks
        clicks = 0
        return when (count) {
            1 -> Command.PlayPause
            2 -> Command.Next
            else -> Command.Previous
        }
    }

    fun reset() {
        clicks = 0
        resetHeld()
    }

    private fun onClickKey(action: Int, repeatCount: Int, now: Long): Outcome {
        if (action != KeyEvent.ACTION_DOWN) return Outcome(handled = true)
        // Bouton maintenu : le système le réserve d'ordinaire à l'assistant vocal. On ne
        // compte pas les répétitions comme des appuis supplémentaires.
        if (repeatCount > 0) return Outcome(handled = true)
        if (!gesturesEnabled) return Outcome(handled = true, command = Command.PlayPause)

        clicks += 1
        lastClickAt = now
        if (clicks >= 3) {
            clicks = 0
            return Outcome(handled = true, command = Command.Previous, cancelPending = true)
        }
        return Outcome(handled = true, resolveAt = now + multiClickWindowMs)
    }

    private fun onSkipKey(keyCode: Int, action: Int, repeatCount: Int, now: Long): Outcome {
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount == 0 || heldKey != keyCode) {
                    // Un nouvel appui : le précédent, s'il attendait encore, était simple.
                    val pending = if (heldKey != 0 && !seeking) skipCommand(heldKey) else null
                    heldKey = keyCode
                    heldSince = now
                    lastSeekAt = 0L
                    seeking = false
                    if (repeatCount == 0) return Outcome(handled = true, command = pending, resolveAt = now + longPressMs)
                }
                // Touche tenue : les répétitions remplacent l'attente d'un relâchement.
                if (now - heldSince < longPressMs || now - lastSeekAt < seekRepeatMs) {
                    return Outcome(handled = true, cancelPending = true)
                }
                seeking = true
                lastSeekAt = now
                val delta = if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) seekStepMs else -seekStepMs
                return Outcome(handled = true, command = Command.SeekBy(delta), cancelPending = true)
            }
            KeyEvent.ACTION_UP -> {
                if (heldKey != keyCode) return Outcome(handled = true)
                val command = if (seeking) null else skipCommand(keyCode)
                resetHeld()
                return Outcome(handled = true, command = command, cancelPending = true)
            }
            else -> return Outcome(handled = true)
        }
    }

    private fun skipCommand(keyCode: Int): Command =
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) Command.Next else Command.Previous

    private fun resetHeld() {
        heldKey = 0
        heldSince = 0L
        lastSeekAt = 0L
        seeking = false
    }

    companion object {
        /** Délai entre deux appuis d'une même série : celui des doubles touchers d'Android, un peu élargi. */
        const val MULTI_CLICK_WINDOW_MS = 450L
        /** Au-delà de la première répétition du clavier (500 ms), pour ne pas la devancer. */
        const val LONG_PRESS_MS = 700L
        const val SEEK_REPEAT_MS = 400L
        const val SEEK_STEP_MS = 10_000L
    }
}
