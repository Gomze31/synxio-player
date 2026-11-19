package fr.synxio.player.wear

import fr.synxio.player.wear.protocol.WearState
import kotlin.math.abs

/**
 * Décide si un nouvel état mérite un aller Bluetooth.
 *
 * Le service publie souvent (sauvegarde de position, minuteur). Pendant la lecture, la
 * montre extrapole la position : un état qui ne fait que confirmer cette extrapolation
 * n'apporte rien. Tout changement d'un autre champ, ou un saut de position, est publié.
 */
object WearStateDedup {
    const val POSITION_TOLERANCE_MS = 2_000L

    fun needsPublish(previous: WearState?, next: WearState, nowMs: Long): Boolean {
        if (previous == null) return true
        if (previous.stripped() != next.stripped()) return true
        if (!next.isPlaying) return previous.positionMs != next.positionMs
        val expected = previous.progressAt(nowMs)
        val actual = next.progressAt(nowMs)
        return abs(expected - actual) > POSITION_TOLERANCE_MS
    }

    /** Sans l'horloge ni la position : les champs dont tout changement vaut publication. */
    private fun WearState.stripped() = copy(positionMs = 0L, capturedAtMs = 0L)
}
