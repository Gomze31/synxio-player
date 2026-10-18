package fr.synxio.player.data.meta

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAndArtTest {

    // --- Images de dossier ----------------------------------------------------------------

    @Test
    fun `cover passe avant folder et front, le dos est ignore`() {
        assertEquals("Cover.jpg", FolderArtFinder.pickCover(listOf("Back.jpg", "Front.jpg", "Cover.jpg", "01.mp3"), true))
        assertEquals("Front.jpg", FolderArtFinder.pickCover(listOf("Back.jpg", "Front.jpg"), true))
        assertEquals(
            "AlbumArt_{X}_Large.jpg",
            FolderArtFinder.pickCover(listOf("AlbumArt_{X}_Small.jpg", "AlbumArt_{X}_Large.jpg"), true),
        )
    }

    @Test
    fun `une image seule sans nom parlant depend du dossier`() {
        assertEquals("scan.png", FolderArtFinder.pickCover(listOf("scan.png", "back.png"), allowSingle = true))
        assertNull(FolderArtFinder.pickCover(listOf("scan.png"), allowSingle = false))
        assertNull(FolderArtFinder.pickCover(listOf("titre.mp3", "notes.txt"), allowSingle = true))
    }

    // --- MusicBrainz ----------------------------------------------------------------------

    private val response = """
        {"recordings":[
          {"id":"r1","score":100,"title":"Deuxième option","length":201000,
           "first-release-date":"2023-05-12",
           "artist-credit":[{"name":"Coralie Fischer","joinphrase":""}],
           "tags":[{"count":2,"name":"pop"}],
           "releases":[
             {"id":"comp","title":"NRJ Hits 2024","status":"Official","date":"2024-01-01",
              "release-group":{"id":"g-comp","primary-type":"Album","secondary-types":["Compilation"]},
              "media":[{"position":1,"track":[{"number":"12"}]}]},
             {"id":"single","title":"Deuxième option","status":"Official","date":"2023-05-12",
              "artist-credit":[{"name":"Coralie Fischer"}],
              "release-group":{"id":"g-single","primary-type":"Single"},
              "media":[{"position":1,"track":[{"number":"1"}]}]}
           ]},
          {"id":"r2","score":90,"title":"Deuxième option","length":95000,
           "artist-credit":[{"name":"Autre groupe"}],"releases":[]}
        ]}
    """.trimIndent()

    @Test
    fun `la fiche retenue recoupe titre, artiste et duree, sortie d origine preferee`() {
        val recordings = MusicBrainzClient.parseRecordings(response)
        assertEquals(2, recordings.size)
        val match = MusicBrainzClient.bestMatch(recordings, "Deuxième option", "Coralie Fischer", 202_000)
        assertNotNull(match)
        assertEquals("Coralie Fischer", match!!.metadata.artist)
        // Le single d'origine plutôt que la compilation.
        assertEquals("single", match.releaseId)
        assertEquals("Deuxième option", match.metadata.album)
        assertEquals(2023, match.metadata.year)
        assertEquals("Pop", match.metadata.genre)
        assertTrue(match.confidence >= MusicBrainzClient.MIN_CONFIDENCE)
    }

    @Test
    fun `sans artiste connu, une duree differente fait rejeter la fiche`() {
        val recordings = MusicBrainzClient.parseRecordings(response)
        assertNull(MusicBrainzClient.bestMatch(recordings, "Deuxième option", null, 150_000))
        assertNotNull(MusicBrainzClient.bestMatch(recordings, "Deuxième option", null, 200_500))
    }

    @Test
    fun `une autre version, plus de dix secondes d ecart, est rejetee`() {
        val recordings = MusicBrainzClient.parseRecordings(response)
        assertNull(MusicBrainzClient.bestMatch(recordings, "Deuxième option", "Coralie Fischer", 215_000))
        // Sans artiste, deux secondes au plus.
        assertNull(MusicBrainzClient.bestMatch(recordings, "Deuxième option", null, 203_500))
    }

    @Test
    fun `une image dans un dossier fourre-tout n est pas une pochette`() {
        assertTrue(!FileNameInference.isAlbumFolder("/storage/emulated/0/Download"))
        assertTrue(!FileNameInference.isAlbumFolder("/storage/emulated/0/Son v2"))
        assertTrue(FileNameInference.isAlbumFolder("/storage/emulated/0/Son v2/NRJ Summer Hits 2024"))
        assertTrue(FileNameInference.isAlbumFolder("/storage/emulated/0/Son v2/Les 100 Plus Belles Chansons Disney/CD4"))
    }

    @Test
    fun `un artiste different fait rejeter la fiche`() {
        val recordings = MusicBrainzClient.parseRecordings(response)
        assertNull(MusicBrainzClient.bestMatch(recordings, "Deuxième option", "Laura", 201_000))
    }

    @Test
    fun `la requete Lucene echappe les caracteres speciaux`() {
        val query = MusicBrainzClient.buildQuery("AC/DC: Back (Live)", "AC/DC", null)
        assertEquals("recording:\"AC\\/DC\\: Back \\(Live\\)\" AND artist:\"AC\\/DC\"", query)
        assertEquals(
            "recording:\"Liberté\" AND dur:[196000 TO 204000]",
            MusicBrainzClient.buildQuery("Liberté", null, 200_000),
        )
        assertNull(MusicBrainzClient.buildQuery("  ", "X", null))
    }

    @Test
    fun `le limiteur espace les requetes`() = runBlocking {
        var now = 10_000L
        val waits = mutableListOf<Long>()
        val limiter = RateLimiter(1_000, clock = { now }, sleep = { waits += it; now += it })
        limiter.run { }
        now += 200
        limiter.run { }
        now += 1_500
        limiter.run { }
        assertEquals(listOf(800L), waits)
    }

    @Test
    fun `annee, numero et genre ID3`() {
        assertEquals(2017, FileTagReader.parseYear("2017-03-03"))
        assertEquals(1998, FileTagReader.parseYear("19981201"))
        assertEquals(3, FileTagReader.parseNumber("03/12"))
        assertNull(FileTagReader.parseNumber("0"))
        assertEquals("Pop", FileTagReader.parseGenre("(13)"))
        assertEquals("Rock", FileTagReader.parseGenre("(17)Rock"))
        assertEquals("Chanson française", FileTagReader.parseGenre("Chanson française"))
    }

    @Test
    fun `un album corrige a un identifiant negatif et stable`() {
        val a = MetadataEnricher.syntheticAlbumId("Discovery", "Daft Punk")
        assertTrue(a < 0)
        assertEquals(a, MetadataEnricher.syntheticAlbumId("discovery", "DAFT PUNK"))
        assertTrue(a != MetadataEnricher.syntheticAlbumId("Discovery", "Autre"))
    }
}
