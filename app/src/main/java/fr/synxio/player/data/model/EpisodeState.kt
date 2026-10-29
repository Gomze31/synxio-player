package fr.synxio.player.data.model

import fr.synxio.player.data.db.PodcastProgressEntity

/** État d'écoute d'un épisode de podcast ou d'un chapitre de livre audio. */
enum class EpisodeState { NEW, IN_PROGRESS, PLAYED }

/**
 * État d'après la position enregistrée : rien ou zéro, jamais écouté ; près de la fin,
 * lu (une écoute terminée garde la durée comme position) ; entre les deux, en cours.
 */
fun episodeState(saved: PodcastProgressEntity?, durationMs: Long): EpisodeState = when {
    saved == null || saved.positionMs <= 0L -> EpisodeState.NEW
    BookProgress.isAtEnd(saved.positionMs, durationMs) -> EpisodeState.PLAYED
    else -> EpisodeState.IN_PROGRESS
}

/**
 * Positions qui marquent [songs] comme lus : chacun à sa fin, tous à [nowSec]. À
 * égalité d'horodatage, [BookProgress.of] retient le dernier chapitre : le livre est fini.
 */
fun markedPlayed(songs: List<Song>, nowSec: Long): List<PodcastProgressEntity> =
    songs.map { PodcastProgressEntity(songId = it.id, positionMs = it.durationMs, lastPlayedSec = nowSec) }
