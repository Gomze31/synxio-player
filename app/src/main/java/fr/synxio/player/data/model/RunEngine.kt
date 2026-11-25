package fr.synxio.player.data.model

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Cible de tempo de la course ; `bpm = 0` tant qu'aucune cadence stable n'a été vue. */
data class RunTarget(val bpm: Float, val locked: Boolean) {
    val hasTarget: Boolean get() = bpm > 0f

    companion object {
        val NONE = RunTarget(0f, locked = false)
    }
}

/** Titre choisi et vitesse à lui appliquer (tonalité inchangée), dans [0,95 ; 1,05]. */
data class RunPick(val id: Long, val speed: Float)

/**
 * Moteur pur de la course au tempo : quand changer de cible, quel titre choisir.
 *
 * - [retarget] : en mode « Suivre », la cible suit la cadence lissée mais ne bouge que si
 *   l'écart dépasse [HYSTERESIS] et que la cadence est stable. Verrouillée : fixe.
 * - [pick] : un titre dont le BPM (ou son double / sa moitié) est à ±[EXACT_TOLERANCE] de
 *   la cible et d'énergie ≥ [MIN_ENERGY] se joue à vitesse 1,0 ; sinon un titre à
 *   ±[STRETCH_TOLERANCE] se joue à `cible / bpm` borné à ±[MAX_STRETCH] ; sinon `null`.
 */
object RunEngine {
    const val HYSTERESIS = 0.04f
    const val EXACT_TOLERANCE = 0.03f
    const val STRETCH_TOLERANCE = 0.08f
    const val MAX_STRETCH = 0.05f
    const val MIN_ENERGY = 0.5f

    fun retarget(current: RunTarget, cadence: Float?, stable: Boolean): RunTarget {
        if (current.locked) return current
        if (cadence == null || cadence <= 0f || !stable) return current
        val rounded = cadence.roundToInt().toFloat()
        if (!current.hasTarget) return RunTarget(rounded, locked = false)
        val gap = abs(cadence - current.bpm) / current.bpm
        return if (gap > HYSTERESIS) RunTarget(rounded, locked = false) else current
    }

    /** BPM, double ou moitié : celui qui se rapproche le plus de la cible (le pas marque le temps ou la demi-mesure). */
    fun effectiveBpm(target: Float, bpm: Float): Float =
        listOf(bpm, bpm * 2f, bpm / 2f).minBy { abs(it - target) }

    fun pick(target: Float, candidates: List<FlowTrack>, recentIds: Set<Long>, random: Random): RunPick? {
        if (target <= 0f) return null
        val withBpm = candidates.filter { it.bpm > 0f }
        if (withBpm.isEmpty()) return null

        fun gap(t: FlowTrack) = abs(effectiveBpm(target, t.bpm) - target) / target
        fun exact(pool: List<FlowTrack>) = pool.filter { gap(it) <= EXACT_TOLERANCE && it.energy >= MIN_ENERGY }
        fun stretch(pool: List<FlowTrack>) = pool.filter { gap(it) <= STRETCH_TOLERANCE }

        val fresh = withBpm.filter { it.id !in recentIds }
        // Les titres déjà joués pendant la course sont évités, puis repêchés quand la
        // bibliothèque est trop petite : mieux vaut réentendre un titre que rien au tempo.
        val exactPool = exact(fresh).ifEmpty { exact(withBpm) }
        if (exactPool.isNotEmpty()) return RunPick(exactPool[random.nextInt(exactPool.size)].id, 1f)

        val stretchPool = stretch(fresh).ifEmpty { stretch(withBpm) }
        if (stretchPool.isEmpty()) return null
        val chosen = stretchPool[random.nextInt(stretchPool.size)]
        val speed = (target / effectiveBpm(target, chosen.bpm)).coerceIn(1f - MAX_STRETCH, 1f + MAX_STRETCH)
        return RunPick(chosen.id, speed)
    }
}
