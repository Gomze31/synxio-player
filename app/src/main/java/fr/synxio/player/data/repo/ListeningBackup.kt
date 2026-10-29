package fr.synxio.player.data.repo

import fr.synxio.player.data.db.BookmarkEntity
import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.data.model.BackupBookmark
import fr.synxio.player.data.model.BackupPosition

/**
 * Positions de reprise et signets dans une sauvegarde.
 *
 * Les identifiants MediaStore changent quand la bibliothèque est réindexée sur un autre
 * téléphone : à la restauration, un morceau est retrouvé par son chemin d'abord, par son
 * ancien identifiant ensuite s'il existe encore. Ce qui ne se retrouve pas est écarté.
 */
object ListeningBackup {

    fun export(positions: List<PodcastProgressEntity>, pathOf: (Long) -> String?): List<BackupPosition> =
        positions.mapNotNull { p ->
            val path = pathOf(p.songId) ?: return@mapNotNull null
            BackupPosition(p.songId, path, p.positionMs, p.lastPlayedSec)
        }

    fun export(bookmarks: List<BookmarkEntity>): List<BackupBookmark> = bookmarks.map {
        BackupBookmark(it.songId, it.songPath, it.bookPath, it.positionMs, it.note, it.createdAt)
    }

    /**
     * Identifiant actuel du morceau sauvegardé ([songId], [songPath]) : par chemin, sinon
     * par identifiant s'il est encore connu ([knownIds]) ; `null` s'il a disparu.
     */
    fun resolve(songId: Long, songPath: String, idForPath: (String) -> Long?, knownIds: Set<Long>): Long? =
        idForPath(songPath) ?: songId.takeIf { it in knownIds }

    /**
     * Positions à écrire : seulement celles plus récentes que la position déjà en base
     * pour le même morceau. Restaurer une vieille sauvegarde ne fait pas reculer un livre.
     */
    fun positionsToRestore(
        backup: List<BackupPosition>,
        existing: Map<Long, PodcastProgressEntity>,
        idForPath: (String) -> Long?,
        knownIds: Set<Long>,
    ): List<PodcastProgressEntity> = backup
        .mapNotNull { b ->
            val id = resolve(b.songId, b.songPath, idForPath, knownIds) ?: return@mapNotNull null
            PodcastProgressEntity(songId = id, positionMs = b.positionMs.coerceAtLeast(0L), lastPlayedSec = b.lastPlayedSec)
        }
        .groupBy { it.songId }
        .map { (_, entries) -> entries.maxBy { it.lastPlayedSec } }
        .filter { candidate -> (existing[candidate.songId]?.lastPlayedSec ?: Long.MIN_VALUE) < candidate.lastPlayedSec }

    /**
     * Signets à ajouter : ceux dont le fichier est retrouvé, sauf ceux déjà présents
     * (même fichier, même position) — importer deux fois la même sauvegarde ne double rien.
     */
    fun bookmarksToRestore(
        backup: List<BackupBookmark>,
        existing: List<BookmarkEntity>,
        idForPath: (String) -> Long?,
        knownIds: Set<Long>,
    ): List<BookmarkEntity> {
        val present = existing.mapTo(HashSet()) { it.songPath to it.positionMs }
        return backup.mapNotNull { b ->
            val id = resolve(b.songId, b.songPath, idForPath, knownIds) ?: return@mapNotNull null
            if (!present.add(b.songPath to b.positionMs)) return@mapNotNull null
            BookmarkEntity(
                songId = id,
                songPath = b.songPath,
                bookPath = b.bookPath,
                positionMs = b.positionMs.coerceAtLeast(0L),
                note = b.note.take(NOTE_MAX_LENGTH),
                createdAt = b.createdAt,
            )
        }
    }

    private const val NOTE_MAX_LENGTH = 200
}
