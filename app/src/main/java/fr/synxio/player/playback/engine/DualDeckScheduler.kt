package fr.synxio.player.playback.engine

/**
 * Décide, à partir de la position du deck actif, quand préparer la réserve, quand
 * engager le chevauchement, quand basculer et quand tout abandonner.
 *
 * Pur, sans Android ni horloge : c'est [CrossfadePlayer] qui fournit la position à
 * chaque tick et applique les décisions aux deux ExoPlayer.
 *
 * Cycle nominal, `fadeMs > 0` et titre suivant connu :
 * 1. `Prepare` à `T − fadeMs − prepareLeadMs` : la réserve se positionne sur le suivant.
 * 2. `Overlap(p)` à partir de `T − fadeMs` : la réserve joue, `p` monte de 0 à 1.
 * 3. `Swap` quand `p ≥ 1`.
 * `T` est la durée du titre, ou `outroMs` s'il est connu, antérieur, et que le mode
 * « Caler sur l'énergie » est actif ; dans ce mode la réserve démarre à `introMs`.
 */
class DualDeckScheduler(private val prepareLeadMs: Long = PREPARE_LEAD_MS) {

    enum class Phase { IDLE, PREPARED, OVERLAP }

    sealed interface Decision {
        /** Rien à faire pour l'instant. */
        data object Idle : Decision
        /** Positionner la réserve sur [nextIndex] à [startMs], en pause, volume 0. */
        data class Prepare(val nextIndex: Int, val startMs: Long) : Decision
        /** Chevauchement en cours ; au premier tick, lancer la réserve. */
        data class Overlap(val progress: Float) : Decision
        /** La réserve devient l'actif. */
        data object Swap : Decision
        /** Couper la réserve, rendre le volume plein à l'actif. */
        data object Abort : Decision
    }

    data class Config(val fadeMs: Int, val energyAligned: Boolean)

    /** Bornes réelles du son d'un titre (étape 5), null quand il n'est pas analysé. */
    data class Traits(val introMs: Long?, val outroMs: Long?)

    /** Instants calculés pour le titre courant. */
    data class Plan(val prepareAtMs: Long, val overlapStartMs: Long, val overlapEndMs: Long)

    var phase: Phase = Phase.IDLE
        private set

    /** Index préparé sur la réserve, null hors préparation. */
    var preparedIndex: Int? = null
        private set

    private var plan: Plan? = null

    /**
     * Instants du fondu pour un titre de [durationMs], ou null si aucun fondu n'est
     * possible : fondu désactivé, durée inconnue, titre plus court que deux fondus.
     */
    fun plan(durationMs: Long, config: Config, current: Traits?): Plan? {
        val fade = config.fadeMs.toLong()
        if (fade <= 0 || durationMs <= 0 || durationMs < fade * 2) return null
        val outro = current?.outroMs?.takeIf { config.energyAligned && it in (fade + 1) until durationMs }
        val end = outro ?: durationMs
        val start = end - fade
        return Plan(prepareAtMs = (start - prepareLeadMs).coerceAtLeast(0), overlapStartMs = start, overlapEndMs = end)
    }

    fun tick(
        positionMs: Long,
        durationMs: Long,
        nextIndex: Int?,
        config: Config,
        current: Traits?,
        next: Traits?,
    ): Decision {
        val plan = plan(durationMs, config, current)
        if (plan == null || nextIndex == null) return interrupt()
        this.plan = plan
        return when (phase) {
            Phase.IDLE -> {
                if (positionMs < plan.prepareAtMs) return Decision.Idle
                // Trop tard pour un fondu propre : la réserve n'aurait pas le temps d'être prête.
                if (positionMs >= plan.overlapStartMs) return Decision.Idle
                phase = Phase.PREPARED
                preparedIndex = nextIndex
                val startMs = if (config.energyAligned) next?.introMs ?: 0L else 0L
                Decision.Prepare(nextIndex, startMs.coerceAtLeast(0L))
            }
            Phase.PREPARED -> {
                if (nextIndex != preparedIndex) {
                    // Le suivant a changé (aléatoire, file) : on repart de zéro.
                    reset()
                    return tick(positionMs, durationMs, nextIndex, config, current, next)
                }
                if (positionMs < plan.overlapStartMs) return Decision.Idle
                phase = Phase.OVERLAP
                Decision.Overlap(progress(positionMs, plan, config))
            }
            Phase.OVERLAP -> {
                val p = progress(positionMs, plan, config)
                if (p >= 1f) {
                    reset()
                    Decision.Swap
                } else {
                    Decision.Overlap(p)
                }
            }
        }
    }

    /** Délai avant le prochain tick utile, en ms de position (à diviser par la vitesse). */
    fun nextTickDelayMs(positionMs: Long, durationMs: Long, config: Config, current: Traits?): Long {
        return when (phase) {
            Phase.OVERLAP -> OVERLAP_TICK_MS
            Phase.PREPARED -> {
                val start = plan?.overlapStartMs ?: return OVERLAP_TICK_MS
                (start - positionMs).coerceIn(OVERLAP_TICK_MS, PREPARED_TICK_MAX_MS)
            }
            Phase.IDLE -> {
                val p = plan(durationMs, config, current) ?: return IDLE_TICK_MAX_MS
                (p.prepareAtMs - positionMs).coerceIn(IDLE_TICK_MIN_MS, IDLE_TICK_MAX_MS)
            }
        }
    }

    /** Le deck actif a changé de titre (nativement ou par bascule) : cycle terminé. */
    fun transition(): Decision {
        reset()
        return Decision.Idle
    }

    fun seek(): Decision = interrupt()

    fun pause(): Decision = interrupt()

    fun queueChanged(): Decision = interrupt()

    /** La réserve n'a pas pu lire le suivant : on laisse l'enchaînement natif faire. */
    fun reserveFailed(): Decision {
        if (phase == Phase.IDLE) return Decision.Idle
        reset()
        return Decision.Abort
    }

    private fun interrupt(): Decision {
        val wasOverlapping = phase == Phase.OVERLAP
        reset()
        return if (wasOverlapping) Decision.Abort else Decision.Idle
    }

    private fun reset() {
        phase = Phase.IDLE
        preparedIndex = null
        plan = null
    }

    private fun progress(positionMs: Long, plan: Plan, config: Config): Float =
        ((positionMs - plan.overlapStartMs).toFloat() / config.fadeMs).coerceIn(0f, 1f)

    companion object {
        const val PREPARE_LEAD_MS = 3_000L
        const val OVERLAP_TICK_MS = 50L
        private const val PREPARED_TICK_MAX_MS = 250L
        private const val IDLE_TICK_MIN_MS = 250L
        private const val IDLE_TICK_MAX_MS = 2_000L
    }
}
