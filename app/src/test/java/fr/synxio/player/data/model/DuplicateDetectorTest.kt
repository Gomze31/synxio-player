package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun `la normalisation retire accents casse et ponctuation`() {
        assertEquals("elleestdouee", DuplicateDetector.normalize("Elle est douée !"))
        assertEquals("cestlavie", DuplicateDetector.normalize("C'est la vie..."))
    }

    @Test
    fun `la normalisation retire les suffixes parasites`() {
        val base = DuplicateDetector.normalize("Formidable")
        assertEquals(base, DuplicateDetector.normalize("Formidable (Remastered 2011)"))
        assertEquals(base, DuplicateDetector.normalize("Formidable [Live]"))
        assertEquals(base, DuplicateDetector.normalize("Formidable - Radio Edit"))
    }

    @Test
    fun `la normalisation retire les mentions feat`() {
        val base = DuplicateDetector.normalize("Bruxelles")
        assertEquals(base, DuplicateDetector.normalize("Bruxelles feat. Damso"))
        assertEquals(base, DuplicateDetector.normalize("Bruxelles ft. Damso"))
    }

    @Test
    fun `deux copies du meme morceau forment un groupe`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", path = "/a/1.mp3"),
            song(id = 2, title = "Formidable ", artist = "stromae", path = "/b/2.mp3"),
        )
        val groups = DuplicateDetector.findGroups(songs)
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().songs.size)
    }

    @Test
    fun `un morceau unique ne forme pas de groupe`() {
        val groups = DuplicateDetector.findGroups(listOf(song(id = 1), song(id = 2, title = "Autre")))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `un live de duree differente n'est pas un doublon`() {
        // Le garde-fou central : même titre, même artiste, mais 40 s d'écart.
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", durationMs = 210_000),
            song(id = 2, title = "Formidable", artist = "Stromae", durationMs = 250_000),
        )
        assertTrue(DuplicateDetector.findGroups(songs).isEmpty())
    }

    @Test
    fun `un ecart de deux secondes reste un doublon`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", durationMs = 210_000),
            song(id = 2, title = "Formidable", artist = "Stromae", durationMs = 212_000),
        )
        assertEquals(1, DuplicateDetector.findGroups(songs).size)
    }

    @Test
    fun `le meme titre par deux artistes differents n'est pas un doublon`() {
        val songs = listOf(
            song(id = 1, title = "Hello", artist = "Adele"),
            song(id = 2, title = "Hello", artist = "Lionel Richie"),
        )
        assertTrue(DuplicateDetector.findGroups(songs).isEmpty())
    }

    @Test
    fun `le meilleur exemplaire est celui au debit le plus eleve`() {
        // 5 Mo pour 180 s ≈ 222 kbps, 2 Mo ≈ 89 kbps
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", sizeBytes = 2_000_000),
            song(id = 2, title = "Formidable", artist = "Stromae", sizeBytes = 5_000_000),
        )
        val group = DuplicateDetector.findGroups(songs).single()
        assertEquals(2L, group.keeper.id)
        assertEquals(listOf(1L), group.removable.map { it.id })
    }

    @Test
    fun `a debit egal les tags les plus complets l'emportent`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae"),
            song(id = 2, title = "Formidable", artist = "Stromae",
                albumArtist = "Stromae", genre = "Pop", year = 2013, track = 4),
        )
        assertEquals(2L, DuplicateDetector.findGroups(songs).single().keeper.id)
    }

    @Test
    fun `l'espace recuperable exclut l'exemplaire garde`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", sizeBytes = 5_000_000),
            song(id = 2, title = "Formidable", artist = "Stromae", sizeBytes = 3_000_000),
            song(id = 3, title = "Formidable", artist = "Stromae", sizeBytes = 2_000_000),
        )
        val group = DuplicateDetector.findGroups(songs).single()
        assertEquals(1L, group.keeper.id)
        assertEquals(5_000_000L, group.reclaimableBytes)
    }

    @Test
    fun `une bibliotheque vide ne plante pas`() {
        assertTrue(DuplicateDetector.findGroups(emptyList()).isEmpty())
    }
}
