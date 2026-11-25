package fr.synxio.player.playback.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Forme du fondu entre deux morceaux. */
enum class CrossfadeCurve(val label: String, val description: String) {
    /** `cos` / `sin` : la puissance totale reste constante, pas de creux au milieu. */
    EQUAL_POWER("Equal-power", "Equal-power : volume perçu constant"),
    /** Gains complémentaires en ligne droite : léger creux au milieu. */
    LINEAR("Linéaire", "Linéaire : simple et prévisible"),
    /** Equal-power au carré : départ et arrivée très doux, somme des gains constante. */
    SMOOTH("Douce", "Douce : départ et fin progressifs");

    companion object {
        fun fromName(name: String?): CrossfadeCurve = entries.firstOrNull { it.name == name } ?: EQUAL_POWER
    }
}

/**
 * Enveloppe du fondu : gain du morceau qui s'éteint et de celui qui démarre, pour une
 * progression `p` de 0 (début du chevauchement) à 1 (bascule). Pur, sans Android.
 */
object CrossfadeEnvelope {

    fun fadeOut(progress: Float, curve: CrossfadeCurve): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (curve) {
            CrossfadeCurve.EQUAL_POWER -> cos(p * HALF_PI).toFloat()
            CrossfadeCurve.LINEAR -> 1f - p
            CrossfadeCurve.SMOOTH -> cos(p * HALF_PI).let { (it * it).toFloat() }
        }.coerceIn(0f, 1f)
    }

    fun fadeIn(progress: Float, curve: CrossfadeCurve): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (curve) {
            CrossfadeCurve.EQUAL_POWER -> sin(p * HALF_PI).toFloat()
            CrossfadeCurve.LINEAR -> p
            CrossfadeCurve.SMOOTH -> sin(p * HALF_PI).let { (it * it).toFloat() }
        }.coerceIn(0f, 1f)
    }

    private const val HALF_PI = PI / 2
}
