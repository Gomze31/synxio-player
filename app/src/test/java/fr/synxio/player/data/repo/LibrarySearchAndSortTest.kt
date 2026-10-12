package fr.synxio.player.data.repo

import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.Genre
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LibrarySearchAndSortTest {

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun album(id: Long, title: String, artist: String = "Artiste", year: Int = 2020, dateAdded: Long = 0) =
        Album(id, title, artist, 1, year, listOf(song(id = id, title = title, dateAddedSec = dateAdded)))

    @Test
    fun `tri par titre range les accents avec leur lettre`() = withLocale(Locale.FRANCE) {
        val songs = listOf(
            song(id = 1, title = "Zazie"),
            song(id = 2, title = "Édith"),
            song(id = 3, title = "alors on danse"),
            song(id = 4, title = "Bohemian"),
        )
        val sorted = sortSongsWith(songs, SongSort.TITLE, descending = false) { 0 }
        assertEquals(listOf("alors on danse", "Bohemian", "Édith", "Zazie"), sorted.map { it.title })

        val reversed = sortSongsWith(songs, SongSort.TITLE, descending = true) { 0 }
        assertEquals(listOf("Zazie", "Édith", "Bohemian", "alors on danse"), reversed.map { it.title })
    }

    @Test
    fun `tri par artiste puis titre`() = withLocale(Locale.FRANCE) {
        val songs = listOf(
            song(id = 1, artist = "Zebda", title = "Tomber la chemise"),
            song(id = 2, artist = "Angèle", title = "Flou"),
            song(id = 3, artist = "angele", title = "Balance ton quoi"),
        )
        val sorted = sortSongsWith(songs, SongSort.ARTIST, descending = false) { 0 }
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `tri par nombre d ecoutes`() {
        val songs = listOf(song(id = 1), song(id = 2), song(id = 3))
        val plays = mapOf(1L to 5, 2L to 10, 3L to 1)
        val sorted = sortSongsWith(songs, SongSort.PLAY_COUNT, descending = true) { plays[it.id] ?: 0 }
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun `tri des albums et artistes`() = withLocale(Locale.FRANCE) {
        val albums = listOf(album(1, "Zénith"), album(2, "Été"), album(3, "Abbey Road"))
        assertEquals(
            listOf("Abbey Road", "Été", "Zénith"),
            sortAlbumsWith(albums, AlbumSort.TITLE, descending = false).map { it.title },
        )

        val artists = listOf(
            Artist(1, "Zaz", listOf(albums[0])),
            Artist(2, "Édith Piaf", listOf(albums[1], albums[2])),
        )
        assertEquals(
            listOf("Édith Piaf", "Zaz"),
            sortArtistsWith(artists, ArtistSort.NAME, descending = false).map { it.name },
        )
        assertEquals(
            listOf("Édith Piaf", "Zaz"),
            sortArtistsWith(artists, ArtistSort.SONG_COUNT, descending = true).map { it.name },
        )
    }

    @Test
    fun `la date d ajout d un album est celle de son titre le plus recent`() {
        val album = Album(
            1, "Album", "Artiste", 1, 2020,
            listOf(song(id = 1, dateAddedSec = 10), song(id = 2, dateAddedSec = 30), song(id = 3, dateAddedSec = 20)),
        )
        assertEquals(30L, album.dateAddedSec)
    }

    @Test
    fun `recherche insensible aux accents et classee`() {
        val songs = listOf(
            song(id = 1, title = "La vie en rose", artist = "Édith Piaf"),
            song(id = 2, title = "Rose", artist = "Autre"),
            song(id = 3, title = "Cœur de pirate", artist = "Béatrice"),
            song(id = 4, title = "Sans rapport", artist = "Personne"),
        )
        val library = Library(
            songs = songs,
            albums = emptyList(),
            artists = emptyList(),
            genres = listOf(Genre("Chanson française", songs)),
            isLoading = false,
            hasScanned = true,
        )

        // Titre exact d'abord, puis début de mot.
        assertEquals(listOf(2L, 1L), searchLibrary(library, "rose", 20).songs.map { it.id })
        // Correspondance par l'artiste, accentué dans la bibliothèque mais pas dans la requête.
        assertEquals(listOf(1L), searchLibrary(library, "edith", 20).songs.map { it.id })
        // Ligature.
        assertEquals(listOf(3L), searchLibrary(library, "COEUR", 20).songs.map { it.id })
        assertEquals(listOf("Chanson française"), searchLibrary(library, "francaise", 20).genres.map { it.name })
        assertTrue(searchLibrary(library, "   ", 20).isEmpty)
        assertEquals(1, searchLibrary(library, "rose", 1).songs.size)
    }

    @Test
    fun `filtre de la recherche avancee`() {
        val songs = listOf(
            song(id = 1, title = "Été indien", artist = "Joe Dassin"),
            song(id = 2, title = "Autre", album = "Cœur sauvage"),
            song(id = 3, title = "Rien"),
        )
        val library = Library(songs = songs, isLoading = false, hasScanned = true)
        assertEquals(listOf(1L), filterLibrarySongs(library, "ete").map { it.id })
        assertEquals(listOf(1L), filterLibrarySongs(library, "DASSIN").map { it.id })
        assertEquals(listOf(2L), filterLibrarySongs(library, "coeur").map { it.id })
        assertEquals(songs, filterLibrarySongs(library, ""))
    }
}
