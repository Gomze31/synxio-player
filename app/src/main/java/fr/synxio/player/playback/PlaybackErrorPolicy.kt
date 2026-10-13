package fr.synxio.player.playback

/**
 * Décide quoi faire quand un titre ne peut pas être lu.
 *
 * Passer au suivant évite qu'un fichier abîmé coupe toute une écoute. Mais si la cause
 * est générale (pipeline audio en échec, stockage indisponible), chaque titre échoue :
 * enchaîner sans limite faisait alors défiler toute la file sans rien jouer.
 *
 * Le compteur n'est donc remis à zéro que lorsqu'un titre a réellement joué un moment
 * ([MIN_HEALTHY_PLAY_MS]), et non dès que la lecture « démarre » : un titre qui démarre
 * puis échoue aussitôt ne prouve pas que la lecture fonctionne.
 */
class PlaybackErrorPolicy(private val maxConsecutiveErrors: Int = MAX_CONSECUTIVE_ERRORS) {

    enum class Action {
        /** Passer au titre suivant. */
        SKIP,
        /** Arrêter : trop d'échecs d'affilée, ou plus rien après. */
        STOP,
    }

    var consecutiveErrors: Int = 0
        private set

    fun onError(itemCount: Int, hasNext: Boolean): Action {
        consecutiveErrors++
        val limit = minOf(itemCount, maxConsecutiveErrors)
        return if (!hasNext || consecutiveErrors >= limit) Action.STOP else Action.SKIP
    }

    /** Appelé avec la durée effectivement écoutée du titre en cours. */
    fun onListened(listenedMs: Long) {
        if (listenedMs >= MIN_HEALTHY_PLAY_MS) consecutiveErrors = 0
    }

    /** L'utilisateur a lancé une nouvelle lecture : on lui redonne sa chance. */
    fun onUserAction() {
        consecutiveErrors = 0
    }

    companion object {
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val MIN_HEALTHY_PLAY_MS = 5_000L
    }
}
