package fr.synxio.player.data.model

import androidx.compose.runtime.Immutable
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.data.db.PodcastProgressEntity

/**
 * Où en est l'écoute d'un livre audio : chapitre en cours, position dans ce chapitre et
 * dans le livre entier.
 *
 * Les positions viennent de la table `podcast_progress`, une ligne par chapitre écouté.
 * Le chapitre en cours est le dernier écouté ; un chapitre fini renvoie au suivant.
 */
@Immutable
data class BookProgress(
    /** Index du chapitre en cours dans la liste ordonnée des chapitres (0 = le premier). */
    val chapterIndex: Int,
    val chapterCount: Int,
    /** Identifiant du morceau du chapitre en cours. */
    val chapterSongId: Long,
    val chapterPositionMs: Long,
    val elapsedMs: Long,
    val totalMs: Long,
    val finished: Boolean,
) {
    val remainingMs: Long get() = (totalMs - elapsedMs).coerceAtLeast(0L)

    /** Avancement de 0 à 1 dans le livre entier. */
    val fraction: Float get() = when {
        finished -> 1f
        totalMs <= 0L -> 0f
        else -> (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    }

    val percent: Int get() = (fraction * 100).toInt()

    /** Vrai dès qu'une écoute a laissé une trace : il y a un endroit où reprendre. */
    val started: Boolean get() = finished || elapsedMs > 0L

    companion object {
        /**
         * Marge de fin : un chapitre arrêté à moins de trois secondes de sa fin est lu.
         * La durée MediaStore diffère souvent de quelques centaines de millisecondes de
         * la durée réelle décodée.
         */
        const val END_MARGIN_MS = 3_000L

        /** Vrai si [positionMs] est assez près de la fin pour compter comme écouté. */
        fun isAtEnd(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0L && positionMs >= durationMs - END_MARGIN_MS

        /**
         * Position où reprendre un chapitre : la position enregistrée, sauf un chapitre
         * déjà fini, qui repart du début au lieu de se terminer aussitôt.
         */
        fun resumePosition(savedMs: Long, durationMs: Long): Long = when {
            savedMs <= 0L -> 0L
            isAtEnd(savedMs, durationMs) -> 0L
            durationMs > 0L -> savedMs.coerceAtMost(durationMs)
            else -> savedMs
        }

        /**
         * Progression du livre formé par [chapters] (déjà dans l'ordre de lecture), d'après
         * les positions enregistrées. Sans chapitre, pas de livre : `null`.
         */
        fun of(chapters: List<Song>, saved: Map<Long, PodcastProgressEntity>): BookProgress? {
            if (chapters.isEmpty()) return null
            // À la seconde près, un chapitre fini et le suivant qui démarre sont ex aequo :
            // le plus avancé dans le livre l'emporte.
            val latest = chapters.indices
                .mapNotNull { i -> saved[chapters[i].id]?.let { i to it } }
                .maxWithOrNull(compareBy({ (_, entry) -> entry.lastPlayedSec }, { (i, _) -> i }))
                ?: return at(chapters, 0, 0L)

            val (index, entry) = latest
            val chapter = chapters[index]
            if (!isAtEnd(entry.positionMs, chapter.durationMs)) {
                return at(chapters, index, entry.positionMs)
            }
            // Chapitre terminé : on attend au début du suivant, ou le livre est fini.
            return if (index < chapters.lastIndex) at(chapters, index + 1, 0L)
            else at(chapters, index, chapter.durationMs).copy(finished = true)
        }

        /**
         * Progression pendant la lecture, au chapitre [currentSongId] et à [positionMs].
         * `null` si ce morceau n'appartient pas au livre.
         */
        fun live(chapters: List<Song>, currentSongId: Long, positionMs: Long): BookProgress? {
            val index = chapters.indexOfFirst { it.id == currentSongId }
            if (index < 0) return null
            return at(chapters, index, positionMs)
        }

        private fun at(chapters: List<Song>, index: Int, positionMs: Long): BookProgress {
            val chapter = chapters[index]
            val position = positionMs.coerceAtLeast(0L).let {
                if (chapter.durationMs > 0L) it.coerceAtMost(chapter.durationMs) else it
            }
            val before = chapters.subList(0, index).sumOf { it.durationMs }
            return BookProgress(
                chapterIndex = index,
                chapterCount = chapters.size,
                chapterSongId = chapter.id,
                chapterPositionMs = position,
                elapsedMs = before + position,
                totalMs = chapters.sumOf { it.durationMs },
                finished = false,
            )
        }
    }
}

/** Chapitres d'un livre dans l'ordre de lecture, comme sur l'écran du livre. */
fun List<Song>.inBookOrder(): List<Song> = sortedBy { it.track }

/** `Chapitre 3 sur 12`. */
fun BookProgress.chapterLabel(): String = "Chapitre ${chapterIndex + 1} sur $chapterCount"

/**
 * Temps restant du livre : `encore 2 h 10 min`. « encore » plutôt que « restantes »
 * évite l'accord (« 1 h restante », « 2 h restantes »).
 */
fun BookProgress.remainingLabel(): String = when {
    finished -> "Terminé"
    remainingMs < 60_000L -> "moins d'une minute"
    else -> "encore ${remainingMs.asLongDuration()}"
}

/** Ligne d'état d'un livre dans la liste : où en est l'écoute, sinon rien (`null`). */
fun BookProgress.statusLabel(): String? = when {
    finished -> "Terminé · ${if (chapterCount <= 1) "$chapterCount chapitre" else "$chapterCount chapitres"}"
    !started -> null
    else -> "${chapterLabel()} · ${remainingLabel()}"
}
