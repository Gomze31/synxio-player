package fr.synxio.player.data.repo

import fr.synxio.player.data.db.BookmarkEntity
import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.data.model.BackupBookmark
import fr.synxio.player.data.model.BackupData
import fr.synxio.player.data.model.BackupPosition
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningBackupTest {

    /** Sur le nouveau téléphone, le fichier /livres/a.mp3 porte l'identifiant 900. */
    private val idByPath = mapOf("/livres/a.mp3" to 900L, "/podcasts/b.mp3" to 901L)
    private val known = setOf(900L, 901L, 5L)

    private fun position(songId: Long, positionMs: Long, lastPlayedSec: Long) =
        PodcastProgressEntity(songId = songId, positionMs = positionMs, lastPlayedSec = lastPlayedSec)

    @Test
    fun `export - positions avec leur chemin, morceaux disparus ecartes`() {
        val paths = mapOf(1L to "/livres/a.mp3")
        val exported = ListeningBackup.export(listOf(position(1, 60_000, 10), position(2, 5_000, 11)), paths::get)
        assertEquals(listOf(BackupPosition(1, "/livres/a.mp3", 60_000, 10)), exported)
    }

    @Test
    fun `restauration - par chemin d'abord, puis par ancien identifiant encore connu`() {
        assertEquals(900L, ListeningBackup.resolve(1, "/livres/a.mp3", idByPath::get, known))
        assertEquals(5L, ListeningBackup.resolve(5, "/ailleurs.mp3", idByPath::get, known))
        assertEquals(null, ListeningBackup.resolve(7, "/ailleurs.mp3", idByPath::get, known))
    }

    @Test
    fun `restauration des positions - jamais en recul sur une position plus recente`() {
        val backup = listOf(
            BackupPosition(1, "/livres/a.mp3", 60_000, lastPlayedSec = 100),
            BackupPosition(2, "/podcasts/b.mp3", 30_000, lastPlayedSec = 100),
            BackupPosition(3, "/disparu.mp3", 10_000, lastPlayedSec = 100),
        )
        // Le podcast a été écouté depuis la sauvegarde : sa position actuelle reste.
        val existing = mapOf(901L to position(901, 45_000, lastPlayedSec = 200))
        val restored = ListeningBackup.positionsToRestore(backup, existing, idByPath::get, known)
        assertEquals(listOf(position(900, 60_000, 100)), restored)
    }

    @Test
    fun `restauration des positions - doublons, la plus recente gagne`() {
        val backup = listOf(
            BackupPosition(1, "/livres/a.mp3", 60_000, lastPlayedSec = 100),
            BackupPosition(900, "/livres/a.mp3", 90_000, lastPlayedSec = 150),
        )
        val restored = ListeningBackup.positionsToRestore(backup, emptyMap(), idByPath::get, known)
        assertEquals(listOf(position(900, 90_000, 150)), restored)
    }

    @Test
    fun `restauration des signets - remappes, sans doublon a la seconde importation`() {
        val backup = listOf(
            BackupBookmark(1, "/livres/a.mp3", "/livres", 60_000, "Le duel", 10),
            BackupBookmark(1, "/livres/a.mp3", "/livres", 90_000, "", 11),
            BackupBookmark(3, "/disparu.mp3", "/livres", 1_000, "", 12),
        )
        val first = ListeningBackup.bookmarksToRestore(backup, emptyList(), idByPath::get, known)
        assertEquals(listOf(60_000L, 90_000L), first.map { it.positionMs })
        assertTrue(first.all { it.songId == 900L && it.id == 0L })
        assertEquals("Le duel", first[0].note)

        val again = ListeningBackup.bookmarksToRestore(backup, first.mapIndexed { i, b -> b.copy(id = i + 1L) }, idByPath::get, known)
        assertTrue(again.isEmpty())
    }

    @Test
    fun `ancienne sauvegarde sans positions ni signets - lue sans erreur`() {
        val json = Json { ignoreUnknownKeys = true }
        val data = json.decodeFromString<BackupData>("""{"version":1,"exportedAt":0,"favorites":[]}""")
        assertTrue(data.positions.isEmpty())
        assertTrue(data.bookmarks.isEmpty())
    }

    @Test
    fun `export des signets - tous les champs`() {
        val bookmark = BookmarkEntity(4, 1, "/livres/a.mp3", "/livres", 60_000, "Note", 10)
        assertEquals(listOf(BackupBookmark(1, "/livres/a.mp3", "/livres", 60_000, "Note", 10)), ListeningBackup.export(listOf(bookmark)))
    }
}
