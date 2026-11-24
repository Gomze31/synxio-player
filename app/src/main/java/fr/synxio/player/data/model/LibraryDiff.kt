package fr.synxio.player.data.model

/** Ce qu'un scan incrémental doit écrire en Room. */
data class DiffPlan(val upserts: List<Long>, val deletions: Set<Long>) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletions.isEmpty()
}

/**
 * Plan d'un scan incrémental : MediaStore donne les lignes modifiées depuis la dernière
 * génération et la liste des ids présents ; le cache donne les ids connus.
 * Pur, sans Android.
 */
object LibraryDiff {
    fun plan(cachedIds: Collection<Long>, changedIds: Collection<Long>, currentIds: Collection<Long>): DiffPlan {
        val current = currentIds.toHashSet()
        val deletions = cachedIds.filterTo(HashSet()) { it !in current }
        val upserts = changedIds.filter { it in current }
        return DiffPlan(upserts, deletions)
    }
}
