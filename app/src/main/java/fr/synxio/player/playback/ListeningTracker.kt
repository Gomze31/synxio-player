package fr.synxio.player.playback

/** Bilan d'une écoute terminée, à enregistrer dans les statistiques et à scrobbler. */
data class Listen(
    val songId: Long,
    val listenedMs: Long,
    val startedAtSec: Long,
    /** Écoute comptée (lue) plutôt qu'un saut. */
    val counted: Boolean,
)

/**
 * Suivi de l'écoute du morceau en cours, sans dépendance au lecteur.
 *
 * Le temps écouté ne court que pendant la lecture : il s'accumule entre une reprise et la
 * pause (ou la fin) suivante. La position du morceau suivi est retenue à part : au
 * changement de piste, le lecteur est déjà sur le suivant, et lire sa position à ce
 * moment-là donnait celle du nouveau morceau (≈ 0) pour l'ancien.
 *
 * @param uptime horloge monotone en millisecondes (`SystemClock.elapsedRealtime`).
 * @param wallClockSec heure murale en secondes, pour dater le début d'écoute (scrobble).
 */
class ListeningTracker(
    private val uptime: () -> Long,
    private val wallClockSec: () -> Long,
) {
    var songId: Long = NONE
        private set
    var listenedMs: Long = 0L
        private set
    var startedAtSec: Long = 0L
        private set

    private var resumedAt = 0L
    private var lastPositionMs = 0L

    val isTracking: Boolean get() = songId >= 0

    /** Nouveau morceau suivi ([songId] null : rien, une radio par exemple). */
    fun start(songId: Long?, playing: Boolean) {
        this.songId = songId ?: NONE
        lastPositionMs = 0L
        listenedMs = 0L
        resumedAt = if (playing) uptime() else 0L
        startedAtSec = wallClockSec()
    }

    /** La lecture reprend : le temps écouté recommence à courir. */
    fun onResumed() {
        resumedAt = uptime()
    }

    /** Ajoute le temps écoulé depuis la dernière reprise ; à appeler à la pause. */
    fun accumulate() {
        if (resumedAt > 0L) {
            listenedMs += uptime() - resumedAt
            resumedAt = 0L
        }
    }

    /** Position relevée pour [songId] (celle du morceau que le lecteur quitte, par exemple). */
    fun notePosition(songId: Long?, positionMs: Long) {
        if (songId == this.songId) lastPositionMs = positionMs.coerceAtLeast(0L)
    }

    /**
     * Position du morceau suivi : celle du lecteur ([livePositionMs]) tant qu'il est
     * dessus ([currentSongId]), sinon la dernière relevée.
     */
    fun position(currentSongId: Long?, livePositionMs: Long): Long {
        notePosition(currentSongId, livePositionMs)
        return lastPositionMs
    }

    /**
     * Clôt l'écoute du morceau suivi, d'une durée de [durationMs].
     *
     * Un morceau écouté à plus de la moitié compte comme lu ; en dessous, c'est un saut.
     * Sous [MIN_LISTEN_MS], rien n'est enregistré (`null`) : un effleurement n'est ni
     * une écoute ni un saut.
     */
    fun finish(skipped: Boolean, durationMs: Long): Listen? {
        accumulate()
        val listened = listenedMs
        if (listened < MIN_LISTEN_MS) return null
        listenedMs = 0L
        return Listen(songId, listened, startedAtSec, counted = !skipped || listened >= durationMs / 2)
    }

    companion object {
        const val NONE = -1L
        const val MIN_LISTEN_MS = 3_000L
    }
}
