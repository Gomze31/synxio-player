package fr.synxio.player.core.util

import kotlin.math.sqrt

/**
 * Détecte un téléphone immobile : variance glissante de la norme de l'accélération sur
 * 10 s, sous 0,05 (m/s²)². Pur : les échantillons et l'horloge sont passés en argument.
 * [stillFor] donne depuis combien de millisecondes la variance est sous le seuil.
 */
class StillnessDetector {
    private val times = ArrayDeque<Long>()
    private val mags = ArrayDeque<Float>()
    private var stillSinceMs = -1L

    fun sample(x: Float, y: Float, z: Float, nowMs: Long) {
        times.addLast(nowMs)
        mags.addLast(sqrt(x * x + y * y + z * z))
        while (times.isNotEmpty() && nowMs - times.first() > WINDOW_MS) {
            times.removeFirst()
            mags.removeFirst()
        }
        val still = mags.size >= 2 && variance() < THRESHOLD
        stillSinceMs = when {
            !still -> -1L
            stillSinceMs < 0 -> nowMs
            else -> stillSinceMs
        }
    }

    /** Millisecondes d'immobilité, 0 sans échantillon ou après un mouvement. */
    fun stillFor(nowMs: Long): Long = if (stillSinceMs < 0) 0L else (nowMs - stillSinceMs).coerceAtLeast(0L)

    private fun variance(): Float {
        val mean = mags.sum() / mags.size
        return mags.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / mags.size
    }

    private companion object {
        const val WINDOW_MS = 10_000L
        const val THRESHOLD = 0.05f
    }
}
