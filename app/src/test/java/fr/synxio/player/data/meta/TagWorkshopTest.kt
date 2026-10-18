package fr.synxio.player.data.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les cas viennent de la relecture de la bibliothèque de l'appareil de test. */
class TagWorkshopTest {

    private fun input(
        path: String = "/storage/emulated/0/Download/Deuxième option - Coralie Fischer (Clip officiel).mp3",
        fileTags: TrackMetadata? = TrackMetadata(),
        resolved: TrackMetadata,
        sources: Map<MetaField, MetaSource>,
        hasEmbeddedArt: Boolean = true,
        artSource: ArtSource = ArtSource.EMBEDDED,
        artUri: String? = "content://media/external/audio/media/1/albumart",
        ambiguous: Boolean = false,
        lookup: LookupInfo? = null,
    ) = WorkshopInput(1, path, fileTags, hasEmbeddedArt, resolved, sources, artSource, artUri, ambiguous, lookup)

    @Test
    fun `artiste et titre tires du nom de fichier sont proposes et surs`() {
        val fix = TagWorkshop.propose(
            input(
                resolved = TrackMetadata(title = "Deuxième option", artist = "Coralie Fischer"),
                sources = mapOf(MetaField.TITLE to MetaSource.INFERRED, MetaField.ARTIST to MetaSource.INFERRED),
            )
        )
        assertNotNull(fix)
        assertEquals("Coralie Fischer", fix!!.fix(FixField.ARTIST)?.proposed)
        assertEquals(FixOrigin.FILE_NAME, fix.fix(FixField.ARTIST)?.origin)
        assertTrue(fix.sure)
    }

    @Test
    fun `un ordre artiste titre indecidable est a verifier`() {
        val fix = TagWorkshop.propose(
            input(
                resolved = TrackMetadata(title = "Et Bam", artist = "Mentissa"),
                sources = mapOf(MetaField.TITLE to MetaSource.INFERRED, MetaField.ARTIST to MetaSource.INFERRED),
                ambiguous = true,
            )
        )!!
        assertTrue(!fix.sure)
        assertNotNull(fix.fix(FixField.ARTIST)?.doubt)
    }

    @Test
    fun `un artiste deduit douteux rend douteux le titre du meme decoupage`() {
        val fix = TagWorkshop.propose(
            input(
                path = "/storage/emulated/0/Son v2/Je T’Aime Là Où Ça Fait Mal – Chanson d’Amour Française Très Romantique.mp3",
                resolved = TrackMetadata(title = "Chanson d’Amour Française Très Romantique", artist = "Je T’Aime Là Où Ça Fait Mal"),
                sources = mapOf(MetaField.TITLE to MetaSource.INFERRED, MetaField.ARTIST to MetaSource.INFERRED),
            )
        )!!
        assertNotNull(fix.fix(FixField.ARTIST)?.doubt)
        assertNotNull(fix.fix(FixField.TITLE)?.doubt)
    }

    @Test
    fun `une fiche trouvee par le titre seul est a verifier`() {
        val fix = TagWorkshop.propose(
            input(
                path = "/storage/emulated/0/Son v2/Skyfall.mp3",
                resolved = TrackMetadata(title = "Skyfall", artist = "Jun Sung Ahn", album = "Cover Volume 2"),
                sources = mapOf(MetaField.TITLE to MetaSource.INFERRED, MetaField.ARTIST to MetaSource.ONLINE, MetaField.ALBUM to MetaSource.ONLINE),
                lookup = LookupInfo(confidence = 98, byTitleOnly = true),
            )
        )!!
        assertTrue(fix.fix(FixField.ARTIST)!!.doubt!!.contains("titre seul"))
        // Le titre recopié du nom de fichier n'apporte rien.
        assertNull(fix.fix(FixField.TITLE))
    }

    @Test
    fun `un album de compilation est a verifier, l artiste reste sur`() {
        val fix = TagWorkshop.propose(
            input(
                fileTags = TrackMetadata(title = "Alors On Danse", artist = "Stromae"),
                resolved = TrackMetadata(title = "Alors On Danse", artist = "Stromae", album = "Foute CD van Q-Music, Vol.7", year = 2011),
                sources = mapOf(
                    MetaField.TITLE to MetaSource.FILE_TAGS, MetaField.ARTIST to MetaSource.FILE_TAGS,
                    MetaField.ALBUM to MetaSource.ONLINE, MetaField.YEAR to MetaSource.ONLINE,
                ),
                lookup = LookupInfo(confidence = 100, byTitleOnly = false),
            )
        )!!
        assertNotNull(fix.fix(FixField.ALBUM)?.doubt)
        assertNull(fix.fix(FixField.ARTIST))
    }

    @Test
    fun `un tag sain n est jamais remplace, un tag abime est repare`() {
        val healthy = TagWorkshop.propose(
            input(
                fileTags = TrackMetadata(title = "Le jeu", artist = "Julien Lieb"),
                resolved = TrackMetadata(title = "Le jeu", artist = "Julien Lieb Officiel"),
                sources = mapOf(MetaField.TITLE to MetaSource.FILE_TAGS, MetaField.ARTIST to MetaSource.ONLINE),
            )
        )
        assertNull(healthy)

        val broken = TagWorkshop.propose(
            input(
                fileTags = TrackMetadata(title = "RÃ©veiller le feu", artist = "Zaho Officiel"),
                resolved = TrackMetadata(title = "Réveiller le feu", artist = "Zaho"),
                sources = mapOf(MetaField.TITLE to MetaSource.FILE_TAGS, MetaField.ARTIST to MetaSource.FILE_TAGS),
            )
        )!!
        assertEquals(FixOrigin.ENCODING, broken.fix(FixField.TITLE)?.origin)
        assertEquals(FixOrigin.CLEANUP, broken.fix(FixField.ARTIST)?.origin)
    }

    @Test
    fun `un titre de rip Piste N est remplace par le nom du fichier`() {
        val fix = TagWorkshop.propose(
            input(
                path = "/storage/emulated/0/Son v2/Le puy du fou.mp3",
                fileTags = TrackMetadata(title = "Piste 12", track = 12),
                resolved = TrackMetadata(title = "Le Puy du Fou", artist = "Didier Barbelivien", track = 12),
                sources = mapOf(MetaField.TITLE to MetaSource.INFERRED, MetaField.ARTIST to MetaSource.ONLINE, MetaField.TRACK to MetaSource.FILE_TAGS),
                lookup = LookupInfo(95, byTitleOnly = false),
            )
        )!!
        assertEquals("Piste 12", fix.fix(FixField.TITLE)?.current)
        assertEquals("Le Puy du Fou", fix.fix(FixField.TITLE)?.proposed)
        assertNull(fix.fix(FixField.TRACK))
    }

    @Test
    fun `la pochette en ligne ou du dossier est proposee si le fichier n en a pas`() {
        val fix = TagWorkshop.propose(
            input(
                resolved = TrackMetadata(),
                sources = emptyMap(),
                hasEmbeddedArt = false,
                artSource = ArtSource.ONLINE,
                artUri = "file:///data/user/0/app/files/meta-artwork/abc.jpg",
                lookup = LookupInfo(100, byTitleOnly = false),
            )
        )!!
        assertEquals(FixOrigin.COVER_ART, fix.fix(FixField.ARTWORK)?.origin)
        assertEquals("/data/user/0/app/files/meta-artwork/abc.jpg", fix.artworkPath)

        // Pochette déjà intégrée : rien à écrire.
        assertNull(
            TagWorkshop.propose(input(resolved = TrackMetadata(), sources = emptyMap(), hasEmbeddedArt = true, artSource = ArtSource.ONLINE, artUri = "file:///x.jpg"))
        )
    }

    @Test
    fun `numero de piste tire du nom pour un album numerote`() {
        val fix = TagWorkshop.propose(
            input(
                path = "/storage/emulated/0/Son v2/NRJ 300% Hits 2024-G11DF3/23 - Maluma - COCO LOCO.mp3",
                fileTags = TrackMetadata(title = "COCO LOCO", artist = "Maluma"),
                resolved = TrackMetadata(title = "COCO LOCO", artist = "Maluma", track = 23),
                sources = mapOf(MetaField.TITLE to MetaSource.FILE_TAGS, MetaField.ARTIST to MetaSource.FILE_TAGS, MetaField.TRACK to MetaSource.INFERRED),
            )
        )!!
        assertEquals("23", fix.fix(FixField.TRACK)?.proposed)
        assertTrue(fix.sure)
    }

    @Test
    fun `un fichier illisible n est pas propose`() {
        assertNull(TagWorkshop.propose(input(fileTags = null, resolved = TrackMetadata(artist = "X"), sources = mapOf(MetaField.ARTIST to MetaSource.INFERRED))))
    }
}
