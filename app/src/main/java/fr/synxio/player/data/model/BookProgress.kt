package fr.synxio.player.data.model

import androidx.compose.runtime.Immutable
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.data.media.Chapter

/**
 * Un chapitre du livre : un fichier entier, ou un chapitre intégré à un fichier.
 *
 * [startInFileMs] situe le chapitre dans son fichier [songId], [startInBookMs] dans le
 * livre entier.
 */
@Immutable
data class BookChapter(
    val songId: Long,
    val title: String,
    val startInFileMs: Long,
    val durationMs: Long,
    val startInBookMs: Long,
)

/**
 * Chapitres du livre formé par [files] (dans l'ordre de lecture) : les chapitres intégrés
 * d'un fichier quand il en a ([embedded], par identifiant de morceau), le fichier sinon.
 */
fun bookChapters(files: List<Song>, embedded: Map<Long, List<Chapter>> = emptyMap()): List<BookChapter> {
    val result = ArrayList<BookChapter>()
    var bookOffset = 0L
    for (file in files) {
        val inner = embedded[file.id].orEmpty()
        if (inner.isEmpty()) {
            result += BookChapter(file.id, file.title, 0L, file.durationMs, bookOffset)
        } else {
            inner.forEach { c ->
                result += BookChapter(file.id, c.title, c.startMs, c.durationMs, bookOffset + c.startMs)
            }
        }
        bookOffset += file.durationMs
    }
    return result
}

/**
 * Où en est l'écoute d'un livre audio : chapitre en cours, position dans ce chapitre et
 * dans le livre entier.
 *
 * Les positions viennent de la table `podcast_progress`, une ligne par fichier écouté.
 * Le fichier en cours est le dernier écouté ; un fichier fini renvoie au suivant. Les
 * chapitres comptés sont ceux de [bookChapters] : un livre en un seul fichier à chapitres
 * intégrés en a autant que le fichier en déclare.
 */
@Immutable
data class BookProgress(
    /** Index du chapitre en cours parmi les chapitres du livre (0 = le premier). */
    val chapterIndex: Int,
    val chapterCount: Int,
    /** Identifiant du fichier (morceau) en cours. */
    val chapterSongId: Long,
    /** Position dans le chapitre en cours. */
    val chapterPositionMs: Long,
    val elapsedMs: Long,
    val totalMs: Long,
    val finished: Boolean,
    /** Dernière écoute du livre (secondes epoch), 0 si jamais écouté. */
    val lastPlayedSec: Long = 0L,
    /** Index du fichier en cours parmi les fichiers du livre. */
    val fileIndex: Int = chapterIndex,
    /** Position dans le fichier en cours. */
    val filePositionMs: Long = chapterPositionMs,
    val chapterTitle: String = "",
    val chapterDurationMs: Long = 0L,
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
        fun of(
            chapters: List<Song>,
            saved: Map<Long, PodcastProgressEntity>,
            embedded: Map<Long, List<Chapter>> = emptyMap(),
        ): BookProgress? {
            if (chapters.isEmpty()) return null
            val all = bookChapters(chapters, embedded)
            // À la seconde près, un chapitre fini et le suivant qui démarre sont ex aequo :
            // le plus avancé dans le livre l'emporte.
            val latest = chapters.indices
                .mapNotNull { i -> saved[chapters[i].id]?.let { i to it } }
                .maxWithOrNull(compareBy({ (_, entry) -> entry.lastPlayedSec }, { (i, _) -> i }))
                ?: return at(chapters, all, 0, 0L)

            val (index, entry) = latest
            val chapter = chapters[index]
            val progress = when {
                !isAtEnd(entry.positionMs, chapter.durationMs) -> at(chapters, all, index, entry.positionMs)
                // Fichier terminé : on attend au début du suivant, ou le livre est fini.
                index < chapters.lastIndex -> at(chapters, all, index + 1, 0L)
                else -> at(chapters, all, index, chapter.durationMs).copy(finished = true)
            }
            return progress.copy(lastPlayedSec = entry.lastPlayedSec)
        }

        /**
         * Progression pendant la lecture, au chapitre [currentSongId] et à [positionMs].
         * `null` si ce morceau n'appartient pas au livre.
         */
        fun live(
            chapters: List<Song>,
            currentSongId: Long,
            positionMs: Long,
            embedded: Map<Long, List<Chapter>> = emptyMap(),
        ): BookProgress? {
            val index = chapters.indexOfFirst { it.id == currentSongId }
            if (index < 0) return null
            return at(chapters, bookChapters(chapters, embedded), index, positionMs)
        }

        private fun at(files: List<Song>, all: List<BookChapter>, index: Int, positionMs: Long): BookProgress {
            val file = files[index]
            val position = positionMs.coerceAtLeast(0L).let {
                if (file.durationMs > 0L) it.coerceAtMost(file.durationMs) else it
            }
            val before = files.subList(0, index).sumOf { it.durationMs }
            // Chapitre qui contient la position : le dernier du fichier commencé avant elle.
            val chapterIndex = all.indices.lastOrNull { all[it].songId == file.id && all[it].startInFileMs <= position }
                ?: all.indexOfFirst { it.songId == file.id }.coerceAtLeast(0)
            val chapter = all.getOrNull(chapterIndex)
            return BookProgress(
                chapterIndex = chapterIndex,
                chapterCount = all.size,
                chapterSongId = file.id,
                chapterPositionMs = (position - (chapter?.startInFileMs ?: 0L)).coerceAtLeast(0L),
                elapsedMs = before + position,
                totalMs = files.sumOf { it.durationMs },
                finished = false,
                fileIndex = index,
                filePositionMs = position,
                chapterTitle = chapter?.title ?: file.title,
                chapterDurationMs = chapter?.durationMs ?: file.durationMs,
            )
        }
    }
}

/**
 * Livre à proposer en reprise : le dernier écouté parmi ceux entamés et pas finis, avec
 * son chemin de dossier ; `null` si aucun.
 */
fun Map<String, BookProgress>.bookToResume(): Pair<String, BookProgress>? =
    entries
        .filter { (_, p) -> p.started && !p.finished }
        .maxByOrNull { (_, p) -> p.lastPlayedSec }
        ?.toPair()

/** Chapitres d'un livre dans l'ordre de lecture, comme sur l'écran du livre. */
fun List<Song>.inBookOrder(): List<Song> = sortedBy { it.track }

/**
 * Où tombe une position du livre, pour nommer un signet : `Partie 3 · 12:34` (titre du
 * chapitre et position dans ce chapitre). `null` si le fichier n'est plus dans le livre.
 */
fun bookPlace(
    files: List<Song>,
    embedded: Map<Long, List<Chapter>>,
    songId: Long,
    positionMs: Long,
): String? {
    val p = BookProgress.live(files, songId, positionMs, embedded) ?: return null
    return "${p.chapterTitle} · ${p.chapterPositionMs.asDuration()}"
}

/** Au-delà de ce temps dans un chapitre, « précédent » revient au début du chapitre. */
const val CHAPTER_RESTART_THRESHOLD_MS = 3_000L

/**
 * Cible de « chapitre précédent », comme sur un lecteur : le début du chapitre en cours
 * après quelques secondes d'écoute, sinon le chapitre d'avant (ou le premier, au début).
 */
fun List<BookChapter>.previousFrom(progress: BookProgress): BookChapter? =
    if (progress.chapterPositionMs > CHAPTER_RESTART_THRESHOLD_MS) getOrNull(progress.chapterIndex)
    else getOrNull(progress.chapterIndex - 1) ?: getOrNull(progress.chapterIndex)

/** Chapitre suivant, `null` au dernier. */
fun List<BookChapter>.nextAfter(progress: BookProgress): BookChapter? = getOrNull(progress.chapterIndex + 1)

/**
 * Temps réel avant la fin du chapitre en cours à la vitesse [speed] : à 1,5×, douze
 * minutes de chapitre passent en huit.
 */
fun BookProgress.msToChapterEnd(speed: Float): Long =
    ((chapterDurationMs - chapterPositionMs).coerceAtLeast(0L) / speed.coerceAtLeast(0.25f)).toLong()

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
