package fr.synxio.player.data.model

/**
 * Cadence de course ou de marche à partir des horodatages de pas (un événement par pas,
 * `TYPE_STEP_DETECTOR`). Pur : aucune dépendance Android, l'horloge est passée en argument.
 *
 * - Fenêtre glissante de [windowMs] : cadence brute = (n − 1) × 60 000 / (dernier − premier),
 *   exacte pour des pas réguliers, lissée ensuite par une moyenne exponentielle ([alpha]).
 * - [stable] quand la cadence lissée a varié de moins de [stabilityTolerance] sur les
 *   [stabilityMs] dernières secondes (il faut donc au moins [stabilityMs] d'historique).
 * - Retombe à 0 et oublie tout après [silenceMs] sans pas ([tick] doit être appelé
 *   régulièrement, le capteur ne dit rien quand on s'arrête).
 *
 * Rien n'est conservé au-delà de la fenêtre : pas de distance, pas d'historique de course.
 */
class CadenceEstimator(
    private val windowMs: Long = 10_000L,
    private val stabilityMs: Long = 20_000L,
    private val alpha: Float = 0.3f,
    private val silenceMs: Long = 10_000L,
    private val stabilityTolerance: Float = 0.04f,
) {
    private val steps = ArrayDeque<Long>()
    private val history = ArrayDeque<Pair<Long, Float>>()
    private var smoothed = 0f
    private var lastStepMs: Long? = null

    /** Cadence lissée en pas par minute, 0 sans pas depuis [silenceMs]. */
    val cadence: Float get() = smoothed

    /** Vrai quand la cadence a peu varié sur les [stabilityMs] dernières secondes. */
    val stable: Boolean
        get() {
            if (smoothed <= 0f || history.size < 2) return false
            val span = history.last().first - history.first().first
            if (span < stabilityMs) return false
            var min = Float.MAX_VALUE
            var max = 0f
            var sum = 0f
            for ((_, c) in history) {
                if (c < min) min = c
                if (c > max) max = c
                sum += c
            }
            val mean = sum / history.size
            return mean > 0f && (max - min) / mean < stabilityTolerance
        }

    /** Instant du dernier pas, ou null si aucun pas n'a été vu depuis la remise à zéro. */
    val lastStepAt: Long? get() = lastStepMs

    /** Un pas à [tMs] (millisecondes, horloge monotone). */
    fun onStep(tMs: Long) {
        lastStepMs = tMs
        steps.addLast(tMs)
        while (steps.isNotEmpty() && tMs - steps.first() > windowMs) steps.removeFirst()
        if (steps.size < 2) return
        val span = steps.last() - steps.first()
        if (span <= 0L) return
        val raw = (steps.size - 1) * 60_000f / span
        smoothed = if (smoothed <= 0f) raw else smoothed + alpha * (raw - smoothed)
        history.addLast(tMs to smoothed)
        // On garde un point au-delà des 20 s pour que l'historique couvre bien la fenêtre.
        while (history.size > 1 && tMs - history[1].first > stabilityMs) history.removeFirst()
    }

    /** Horloge : sans pas depuis [silenceMs], la cadence retombe à 0. */
    fun tick(nowMs: Long) {
        val last = lastStepMs ?: return
        if (nowMs - last > silenceMs) reset()
    }

    fun reset() {
        steps.clear()
        history.clear()
        smoothed = 0f
        lastStepMs = null
    }
}
