package fr.synxio.player.playback

/** File d'attente prête à être rendue au lecteur après un redémarrage. */
data class RestoredQueue<T>(val items: List<T>, val index: Int, val positionMs: Long)

/**
 * Reconstruit la file sauvegardée en tenant compte des morceaux disparus entre-temps.
 *
 * L'index sauvegardé désigne une position dans l'ancienne file : si des titres ont été
 * supprimés ou déplacés, le reprendre tel quel pointait au mieux sur le mauvais morceau,
 * au pire au-delà de la file — ExoPlayer levait alors `IllegalSeekPositionException`
 * et l'app plantait à chaque démarrage.
 *
 * On compte donc les survivants situés avant le morceau courant. Si le morceau courant
 * a lui-même disparu, on enchaîne sur le suivant encore présent, depuis le début : sa
 * position sauvegardée ne veut rien dire pour un autre titre.
 *
 * @return `null` quand plus aucun morceau n'est disponible.
 */
fun <T> restoreQueue(
    savedIds: List<Long>,
    savedIndex: Int,
    savedPositionMs: Long,
    available: Map<Long, T>,
): RestoredQueue<T>? {
    val items = ArrayList<T>(savedIds.size)
    var survivorsBefore = 0
    var currentSurvived = false
    savedIds.forEachIndexed { i, id ->
        val item = available[id] ?: return@forEachIndexed
        items += item
        when {
            i < savedIndex -> survivorsBefore++
            i == savedIndex -> currentSurvived = true
        }
    }
    if (items.isEmpty()) return null

    val index = survivorsBefore.coerceIn(0, items.lastIndex)
    val position = if (currentSurvived) savedPositionMs.coerceAtLeast(0L) else 0L
    return RestoredQueue(items, index, position)
}
