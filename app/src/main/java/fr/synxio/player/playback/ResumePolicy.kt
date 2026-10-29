package fr.synxio.player.playback

import androidx.media3.common.Player
import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.data.model.BookProgress
import fr.synxio.player.data.model.Song

/**
 * Règles de reprise des podcasts et des chapitres de livres audio : quand une position
 * est enregistrée, laquelle, et où reprendre quand le morceau revient.
 */
object ResumePolicy {

    /** Seuls les podcasts et les livres audio gardent une position par morceau. */
    fun tracksProgress(song: Song): Boolean = song.isPodcast || song.isAudiobook

    /**
     * Seule une fin naturelle (ou une répétition) termine la piste. Choisir un autre titre
     * (PLAYLIST_CHANGED) ou zapper (SEEK) l'interrompt : compter cela comme « terminé »
     * remettait à zéro la progression d'un podcast à moitié écouté.
     */
    fun endsTrack(transitionReason: Int): Boolean =
        transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

    /**
     * Position à enregistrer. Fini, l'épisode ou le chapitre garde sa durée, ce qui le
     * marque lu : la progression d'un livre passe au chapitre suivant, et une nouvelle
     * écoute repart du début ([BookProgress.resumePosition]).
     */
    fun positionToSave(song: Song, finished: Boolean, trackedPositionMs: Long): Long =
        if (finished) song.durationMs else trackedPositionMs

    /**
     * Faut-il replacer la position enregistrée quand [song] démarre ? Pas après un zap
     * (SEEK) : passer au chapitre suivant ne doit pas sauter au milieu de celui-ci.
     */
    fun restoresOnTransition(song: Song?, transitionReason: Int): Boolean =
        song != null && tracksProgress(song) && transitionReason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK

    /** Où reprendre [song] d'après sa position enregistrée ; `null` : rester au début. */
    fun restoreTarget(song: Song, saved: PodcastProgressEntity?): Long? {
        val at = saved?.let { BookProgress.resumePosition(it.positionMs, song.durationMs) } ?: 0L
        return at.takeIf { it > 0L }
    }
}

/**
 * « Retour en arrière à la reprise » : après une vraie pause sur le même morceau, on
 * recule de quelques secondes pour retrouver le fil (livres audio, podcasts).
 *
 * @param uptime horloge monotone en millisecondes.
 */
class AutoRewind(private val uptime: () -> Long) {
    private var pausedAt = 0L
    private var pausedMediaId: String? = null

    fun onPaused(mediaId: String?) {
        pausedAt = uptime()
        pausedMediaId = mediaId
    }

    /**
     * Position où reculer quand la lecture reprend, ou `null` pour ne rien faire : réglage
     * à zéro, pas de pause connue, autre morceau, ou pause trop courte (une coupure
     * de quelques secondes ne fait pas perdre le fil).
     */
    fun onResumed(rewindSeconds: Int, currentMediaId: String?, positionMs: Long): Long? {
        val pausedFor = uptime() - pausedAt
        val sameTrack = pausedMediaId != null && pausedMediaId == currentMediaId
        if (rewindSeconds <= 0 || pausedAt == 0L || !sameTrack || pausedFor < MIN_PAUSE_MS) return null
        pausedAt = 0L
        return (positionMs - rewindSeconds * 1_000L).coerceAtLeast(0L)
    }

    companion object {
        const val MIN_PAUSE_MS = 5_000L
    }
}

/** Auto-DJ : titres proches à ajouter quand la file arrive à sa fin. */
object AutoDj {
    const val BATCH_SIZE = 5

    /**
     * Titres de [library] du même genre ou du même artiste que [current], absents de la
     * file ([queued]), mélangés par [shuffle]. Rien pour un podcast ni un livre audio :
     * leur file ne se prolonge pas avec de la musique.
     */
    fun pick(
        library: List<Song>,
        current: Song,
        queued: Set<Long>,
        shuffle: (List<Song>) -> List<Song> = { it.shuffled() },
    ): List<Song> {
        if (current.isPodcast || current.isAudiobook) return emptyList()
        val similar = library.filter {
            (it.genre == current.genre || it.artistId == current.artistId) && it.id !in queued
        }
        return shuffle(similar).take(BATCH_SIZE)
    }
}
