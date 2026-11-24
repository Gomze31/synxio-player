package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBuilderTest {

    private val noFilters = LibraryFilters(minDurationMs = 0, excludedFolders = emptySet(), audiobookFolders = emptySet())

    @Test
    fun `duree minimale - les titres trop courts sont ecartes, podcasts compris`() {
        val raw = listOf(
            song(id = 1, durationMs = 10_000),
            song(id = 2, durationMs = 40_000),
            song(id = 3, durationMs = 5_000).copy(isPodcast = true),
        )
        val lib = LibraryBuilder.build(raw, noFilters.copy(minDurationMs = 30_000))
        assertEquals(listOf(2L), lib.songs.map { it.id })
        assertTrue(lib.podcasts.isEmpty())
    }

    @Test
    fun `dossiers exclus - prefixe de chemin`() {
        val raw = listOf(
            song(id = 1, path = "/storage/Music/a.mp3"),
            song(id = 2, path = "/storage/WhatsApp/b.mp3"),
        )
        val lib = LibraryBuilder.build(raw, noFilters.copy(excludedFolders = setOf("/storage/WhatsApp")))
        assertEquals(listOf(1L), lib.songs.map { it.id })
    }

    @Test
    fun `dossier livre audio - le titre passe dans audiobooks, pas dans songs`() {
        val raw = listOf(
            song(id = 1, path = "/storage/Livres/ch1.mp3"),
            song(id = 2, path = "/storage/Music/a.mp3"),
        )
        val lib = LibraryBuilder.build(raw, noFilters.copy(audiobookFolders = setOf("/storage/Livres")))
        assertEquals(listOf(1L), lib.audiobooks.map { it.id })
        assertEquals(listOf(2L), lib.songs.map { it.id })
        assertTrue(lib.audiobooks.single().isAudiobook)
    }

    @Test
    fun `livre audio natif - garde son drapeau sans dossier`() {
        val raw = listOf(song(id = 1).copy(isAudiobook = true))
        val lib = LibraryBuilder.build(raw, noFilters)
        assertEquals(1, lib.audiobooks.size)
        assertTrue(lib.songs.isEmpty())
    }

    @Test
    fun `albums - pistes triees par disque puis piste, annee max`() {
        val raw = listOf(
            song(id = 1, album = "X", disc = 2, track = 1, year = 2001),
            song(id = 2, album = "X", disc = 1, track = 2, year = 2003),
            song(id = 3, album = "X", disc = 1, track = 1, year = 2002),
        )
        val album = LibraryBuilder.build(raw, noFilters).albums.single()
        assertEquals(listOf(3L, 2L, 1L), album.songs.map { it.id })
        assertEquals(2003, album.year)
    }

    @Test
    fun `artistes - un tag a point-virgule donne deux artistes`() {
        val raw = listOf(song(id = 1, artist = "Alpha; Beta"))
        val names = LibraryBuilder.build(raw, noFilters).artists.map { it.name }.sorted()
        assertEquals(listOf("Alpha", "Beta"), names)
    }

    @Test
    fun `genres - vide devient le genre inconnu, dossiers tries`() {
        val raw = listOf(
            song(id = 1, genre = "", path = "/b/1.mp3"),
            song(id = 2, genre = "Rock", path = "/a/2.mp3"),
        )
        val lib = LibraryBuilder.build(raw, noFilters)
        assertEquals(listOf("Rock", Song.UNKNOWN_GENRE).sortedBy { it.lowercase() }, lib.genres.map { it.name })
        assertEquals(listOf("/a", "/b"), lib.folders.map { it.path })
    }

    @Test
    fun `etat - hasScanned vrai, isLoading faux, isRefreshing transmis`() {
        val lib = LibraryBuilder.build(emptyList(), noFilters, isRefreshing = true)
        assertTrue(lib.hasScanned)
        assertFalse(lib.isLoading)
        assertTrue(lib.isRefreshing)
    }
}
