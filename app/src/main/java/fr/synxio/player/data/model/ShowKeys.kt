package fr.synxio.player.data.model

/**
 * Identifiant d'« émission » pour mémoriser une vitesse de lecture : un podcast se
 * reconnaît à son album (nom de l'émission), un livre audio à son dossier. La musique
 * n'en a pas et garde la vitesse globale.
 */
object ShowKeys {

    fun of(song: Song?): String? = when {
        song == null -> null
        song.isAudiobook -> song.folderPath.takeIf { it.isNotBlank() }?.let { "book:$it" }
        song.isPodcast -> (song.album.takeIf { it.isNotBlank() } ?: song.folderPath.takeIf { it.isNotBlank() })
            ?.let { "podcast:$it" }
        else -> null
    }
}
