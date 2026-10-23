package fr.synxio.player.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fabrique de morceaux pour les tests : seuls les champs utiles sont à renseigner. */
fun song(
    id: Long,
    title: String = "Titre $id",
    artist: String = "Artiste",
    album: String = "Album",
    albumId: Long = 1L,
    albumArtist: String? = null,
    genre: String? = null,
    durationMs: Long = 200_000L,
    track: Int = id.toInt(),
    disc: Int = 1,
    year: Int = 2020,
    dateAddedSec: Long = id,
    sizeBytes: Long = 5_000_000L,
    path: String = "/storage/emulated/0/Music/$album/$title.mp3",
) = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = artist.hashCode().toLong(),
    album = album,
    albumId = albumId,
    albumArtist = albumArtist,
    genre = genre,
    durationMs = durationMs,
    track = track,
    disc = disc,
    year = year,
    dateAddedSec = dateAddedSec,
    dateModifiedSec = dateAddedSec,
    sizeBytes = sizeBytes,
    mimeType = "audio/mpeg",
    path = path,
)

class SongTest {

    @Test
    fun `chemins derives`() {
        val s = song(1, title = "Intro", album = "Feu", path = "/storage/emulated/0/Music/Feu/01 - Intro.flac")
        assertEquals("/storage/emulated/0/Music/Feu", s.folderPath)
        assertEquals("Feu", s.folderName)
        assertEquals("01 - Intro.flac", s.fileName)
        assertEquals("FLAC", s.extension)
    }

    @Test
    fun `fichier sans dossier ni extension`() {
        val s = song(1, path = "track")
        assertEquals("", s.folderPath)
        assertEquals("track", s.fileName)
        assertEquals("", s.extension)
    }

    @Test
    fun `artiste et album inconnus sont remplaces`() {
        val unknown = song(1, artist = "<unknown>", album = "   ")
        assertEquals(Song.UNKNOWN_ARTIST, unknown.displayArtist)
        assertEquals(Song.UNKNOWN_ALBUM, unknown.displayAlbum)

        val known = song(2, artist = "Nekfeu", album = "Feu")
        assertEquals("Nekfeu", known.displayArtist)
        assertEquals("Feu", known.displayAlbum)
    }

    @Test
    fun `debit approximatif`() {
        // 5 Mo sur 200 s ≈ 200 kb/s
        assertEquals(200, song(1, sizeBytes = 5_000_000L, durationMs = 200_000L).approxBitrateKbps)
        assertEquals(0, song(2, durationMs = 0L).approxBitrateKbps)
    }
}

class SplitArtistTagTest {

    @Test
    fun `un seul nom reste entier`() {
        assertEquals(listOf("Nekfeu"), splitArtistTag("Nekfeu"))
        assertEquals(listOf("AC/DC"), splitArtistTag("AC/DC"))
        assertEquals(listOf("Earth, Wind & Fire"), splitArtistTag("Earth, Wind & Fire"))
    }

    @Test
    fun `point-virgule separe toujours`() {
        assertEquals(listOf("Damso", "Nekfeu"), splitArtistTag("Damso; Nekfeu"))
    }

    @Test
    fun `deux barres obliques ou trois virgules separent`() {
        assertEquals(listOf("A", "B", "C"), splitArtistTag("A/B/C"))
        assertEquals(listOf("A", "B", "C", "D"), splitArtistTag("A, B, C, D"))
    }

    @Test
    fun `doublons et vides retires`() {
        assertEquals(listOf("A", "B"), splitArtistTag("A;a;; B ;"))
    }

    @Test
    fun `chaine vide`() {
        assertEquals(emptyList(), splitArtistTag("   "))
    }
}

class LyricsTest {

    private val synced = Lyrics(
        lines = listOf(
            LyricLine(0L, "Un"),
            LyricLine(1_000L, "Deux"),
            LyricLine(null, "(sans horodatage)"),
            LyricLine(2_500L, "Trois"),
            LyricLine(10_000L, "Quatre"),
        ),
        synced = true,
        source = "test",
    )

    @Test
    fun `avant la premiere ligne`() {
        assertEquals(-1, synced.indexAt(-1L))
        assertEquals(0, synced.indexAt(0L))
    }

    @Test
    fun `ligne active a la position`() {
        assertEquals(0, synced.indexAt(999L))
        assertEquals(1, synced.indexAt(1_000L))
        assertEquals(1, synced.indexAt(2_499L))
        assertEquals(3, synced.indexAt(2_500L))
        assertEquals(3, synced.indexAt(9_999L))
        assertEquals(4, synced.indexAt(10_000L))
        assertEquals(4, synced.indexAt(Long.MAX_VALUE))
    }

    @Test
    fun `les lignes sans horodatage ne sont jamais actives`() {
        for (pos in 0L..12_000L step 250L) {
            check(synced.indexAt(pos) != 2) { "ligne 2 active à $pos" }
        }
    }

    @Test
    fun `paroles non synchronisees`() {
        val plain = Lyrics(listOf(LyricLine(null, "a"), LyricLine(null, "b")), synced = false, source = "")
        assertEquals(-1, plain.indexAt(5_000L))
        assertEquals("a\nb", plain.plainText)
    }

    @Test
    fun `paroles vides`() {
        assertEquals(-1, Lyrics.EMPTY.indexAt(0L))
        assertEquals(-1, Lyrics(emptyList(), synced = true, source = "").indexAt(0L))
    }

    @Test
    fun `resultat identique a un parcours lineaire`() {
        val lines = (0 until 300).map { LyricLine(it * 1_337L, "l$it") }
        val lyrics = Lyrics(lines, synced = true, source = "")
        for (pos in 0L..(300 * 1_337L) step 97L) {
            val linear = lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= pos }
            assertEquals(linear, lyrics.indexAt(pos), "position $pos")
        }
    }
}

class AlbumArtistGenreTest {

    @Test
    fun `agregats d album et d artiste`() {
        val a1 = song(1, durationMs = 100L, dateAddedSec = 5)
        val a2 = song(2, durationMs = 200L, dateAddedSec = 9)
        val album = Album(id = 1, title = "A", artist = "X", artistId = 1, year = 2001, songs = listOf(a1, a2))
        assertEquals(2, album.songCount)
        assertEquals(300L, album.durationMs)
        assertEquals(9L, album.dateAddedSec)

        val artist = Artist(id = 1, name = "X", albums = listOf(album, album.copy(id = 2, songs = listOf(a1))))
        assertEquals(3, artist.songCount)
        assertEquals(2, artist.albumCount)
        assertEquals(400L, artist.durationMs)
    }

    @Test
    fun `artiste sans album n a pas de pochette`() {
        assertNull(Artist(1, "X", emptyList()).artworkUri)
    }

    @Test
    fun `nom de dossier`() {
        assertEquals("Music", Folder("/storage/emulated/0/Music", emptyList()).name)
        assertEquals("", Folder("", emptyList()).name)
    }
}

class EqCurveTest {

    private val curve = EqCurve(
        id = "t", name = "t", description = "", category = EqCategory.CONTENT,
        points = listOf(EqCurve.Point(100, -6f), EqCurve.Point(1000, 0f), EqCurve.Point(10000, 6f)),
    )

    @Test
    fun `interpolation logarithmique entre les points`() {
        val levels = curve.toBandLevels(listOf(100, 316, 1000, 3162, 10000), minMb = -1500, maxMb = 1500)
        assertEquals(listOf<Short>(-600, -300, 0, 300, 600), levels)
    }

    @Test
    fun `hors de la plage on prolonge les extremites`() {
        val levels = curve.toBandLevels(listOf(20, 20000), minMb = -1500, maxMb = 1500)
        assertEquals(listOf<Short>(-600, 600), levels)
    }

    @Test
    fun `bornes de l appareil respectees`() {
        val levels = curve.toBandLevels(listOf(100, 10000), minMb = -300, maxMb = 300)
        assertEquals(listOf<Short>(-300, 300), levels)
    }

    @Test
    fun `courbe vide est plate`() {
        val flat = curve.copy(points = emptyList())
        assertEquals(listOf<Short>(0, 0), flat.toBandLevels(listOf(100, 1000), -1500, 1500))
    }

    @Test
    fun `profils integres retrouves par identifiant`() {
        val all = EqCurves.ALL
        check(all.isNotEmpty())
        all.forEach { assertEquals(it, EqCurves.byId(it.id)) }
        assertNull(EqCurves.byId("inexistant"))
    }
}
