package fr.synxio.player.core.car

import fr.synxio.player.data.model.RepeatMode

/** Les titres qui suivent, pour la file simplifiée du mode voiture. */
object DriveQueue {
    /**
     * Index des [count] titres suivants : jusqu'à la fin en lecture simple, en boucle
     * sans repasser par le titre courant quand la file se répète, aucun quand le titre
     * se répète. Vide si la file est vide ou l'index hors de la file.
     */
    fun upcoming(size: Int, index: Int, repeat: RepeatMode, count: Int = 3): List<Int> {
        if (size <= 0 || index !in 0 until size || repeat == RepeatMode.ONE) return emptyList()
        val available = if (repeat == RepeatMode.ALL) size - 1 else size - 1 - index
        return (1..minOf(count, available)).map { (index + it) % size }
    }
}
