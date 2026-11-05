package fr.synxio.player.ui.components

/**
 * Décisions des gestes d'ouverture et de fermeture du lecteur, sans Compose.
 *
 * L'expansion va de 0 (mini-lecteur) à 1 (plein écran). Un déplacement positif va vers
 * le bas et ferme ; négatif, il ouvre. Les seuils sont ceux de la spécification 5.7.1.
 */
class PlayerSheetState(private val screenHeightPx: Float) {

    /** Expansion atteinte après un déplacement cumulé [dragPx] depuis [startExpansion]. */
    fun dragTo(startExpansion: Float, dragPx: Float): Float =
        (startExpansion - dragPx / screenHeightPx).coerceIn(0f, 1f)

    /**
     * Cible au relâcher : 1f ouvre, 0f ferme.
     *
     * Une vélocité franche décide seule ; sinon il faut avoir parcouru 30 % depuis le
     * point de départ, pour qu'un geste hésitant revienne d'où il vient.
     */
    fun settle(startExpansion: Float, expansion: Float, velocityPxPerSec: Float): Float = when {
        velocityPxPerSec >= FLING_VELOCITY -> 0f
        velocityPxPerSec <= -FLING_VELOCITY -> 1f
        startExpansion <= 0.5f -> if (expansion >= OPEN_THRESHOLD) 1f else 0f
        else -> if (expansion <= 1f - OPEN_THRESHOLD) 0f else 1f
    }

    /** Le balayage de fermeture ne part que du haut de l'écran : jamais des commandes. */
    fun canStartDrag(startYFraction: Float): Boolean = startYFraction <= DRAG_ZONE

    companion object {
        const val OPEN_THRESHOLD = 0.3f
        const val FLING_VELOCITY = 1_200f
        const val DRAG_ZONE = 0.65f
    }
}
