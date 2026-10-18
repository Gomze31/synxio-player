package fr.synxio.player.data.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCascadeTest {

    private fun layer(source: MetaSource, data: TrackMetadata) = MetaLayer(source, data)

    @Test
    fun `les tags du fichier passent avant MediaStore`() {
        val resolved = MetadataCascade.resolve(
            listOf(
                layer(MetaSource.MEDIASTORE, TrackMetadata(title = "Titre index", artist = "Artiste index")),
                layer(MetaSource.FILE_TAGS, TrackMetadata(title = "Titre fichier", artist = "Artiste fichier")),
            )
        )
        assertEquals("Titre fichier", resolved.data.title)
        assertEquals(MetaSource.FILE_TAGS, resolved.sourceOf(MetaField.TITLE))
    }

    @Test
    fun `un remplissage cede la place a la source suivante`() {
        val resolved = MetadataCascade.resolve(
            listOf(
                layer(MetaSource.FILE_TAGS, TrackMetadata(title = "Piste 12", artist = "<unknown>", year = 0, genre = "(13)")),
                layer(MetaSource.MEDIASTORE, TrackMetadata(artist = "Artiste inconnu", year = 2019, genre = "Pop")),
                layer(MetaSource.INFERRED, TrackMetadata(title = "Le puy du fou", artist = "Laura")),
            )
        )
        assertEquals("Le puy du fou", resolved.data.title)
        assertEquals("Laura", resolved.data.artist)
        assertEquals(MetaSource.INFERRED, resolved.sourceOf(MetaField.ARTIST))
        assertEquals(2019, resolved.data.year)
        assertEquals("Pop", resolved.data.genre)
        assertTrue(resolved.isGuess(MetaField.ARTIST))
    }

    @Test
    fun `une fiche en ligne remplace une deduction mais jamais un tag`() {
        val resolved = MetadataCascade.resolve(
            listOf(
                layer(MetaSource.FILE_TAGS, TrackMetadata(title = "Deuxième option")),
                layer(MetaSource.INFERRED, TrackMetadata(title = "Coralie Fischer", artist = "Deuxième option")),
                layer(MetaSource.ONLINE, TrackMetadata(title = "Deuxième Option (Radio Edit)", artist = "Coralie Fischer", album = "Single")),
            )
        )
        assertEquals("Deuxième option", resolved.data.title)
        assertEquals("Coralie Fischer", resolved.data.artist)
        assertEquals(MetaSource.ONLINE, resolved.sourceOf(MetaField.ARTIST))
        assertEquals("Single", resolved.data.album)
    }

    @Test
    fun `sans fiche en ligne, la deduction est gardee`() {
        val resolved = MetadataCascade.resolve(
            listOf(
                layer(MetaSource.MEDIASTORE, TrackMetadata(artist = "")),
                layer(MetaSource.INFERRED, TrackMetadata(artist = "KEENV")),
            )
        )
        assertEquals("KEENV", resolved.data.artist)
    }

    @Test
    fun `les encodages sont repares et l artiste n est pas repete dans le titre`() {
        val resolved = MetadataCascade.resolve(
            listOf(
                layer(MetaSource.FILE_TAGS, TrackMetadata(title = "Yu_Gen - Elle danse", artist = "Yu_Gen", album = "CafÃ© Noir")),
            )
        )
        assertEquals("Elle danse", resolved.data.title)
        assertEquals("Café Noir", resolved.data.album)
    }

    @Test
    fun `un artiste deduit du titre des tags emporte aussi la partie titre`() {
        val inferred = FileNameInference.infer("/storage/emulated/0/Son v2/Liane Foly On a tous le droit.mp3", "Liane Foly \"On a tous le droit\"")
        val resolved = MetadataCascade.resolve(
            listOf(
                layer(MetaSource.FILE_TAGS, TrackMetadata(title = "Liane Foly \"On a tous le droit\"")),
                layer(MetaSource.INFERRED, inferred.metadata),
            )
        )
        assertEquals("Liane Foly", resolved.data.artist)
        assertEquals("On a tous le droit", resolved.data.title)

        val mentissa = MetadataCascade.resolve(
            listOf(
                layer(MetaSource.FILE_TAGS, TrackMetadata(title = "Mentissa : Et Bam (clip officiel)")),
                layer(MetaSource.INFERRED, TrackMetadata(title = "Et Bam", artist = "Mentissa")),
            )
        )
        assertEquals("Et Bam", mentissa.data.title)
    }

    @Test
    fun `le suffixe Topic des chaines YouTube est retire`() {
        val resolved = MetadataCascade.resolve(
            listOf(layer(MetaSource.FILE_TAGS, TrackMetadata(artist = "Angèle - Topic"))),
        )
        assertEquals("Angèle", resolved.data.artist)

        val zaho = MetadataCascade.resolve(
            listOf(layer(MetaSource.FILE_TAGS, TrackMetadata(title = "Zaho - Laissez-les kouma", artist = "Zaho Officiel"))),
        )
        assertEquals("Zaho", zaho.data.artist)
        assertEquals("Laissez-les kouma", zaho.data.title)
        val vevo = MetadataCascade.resolve(listOf(layer(MetaSource.FILE_TAGS, TrackMetadata(artist = "AngeleVEVO"))))
        assertEquals("Angele", vevo.data.artist)
    }

    @Test
    fun `aucune source valable ne donne aucune valeur`() {
        val resolved = MetadataCascade.resolve(
            listOf(layer(MetaSource.MEDIASTORE, TrackMetadata(album = "<unknown>", track = 0, disc = 0))),
        )
        assertNull(resolved.data.album)
        assertNull(resolved.data.track)
        assertNull(resolved.sourceOf(MetaField.ALBUM))
    }

    @Test
    fun `reconnaissance des valeurs de remplissage`() {
        assertTrue(!TagSanity.isValidTitle("Track 03"))
        assertTrue(!TagSanity.isValidAlbum("Album inconnu (04/08/2017 22:54:26)"))
        assertTrue(TagSanity.isValidTitle("Untitled Love Song"))
        assertTrue(TagSanity.isValidArtist("Various Artists"))
        assertTrue(!TagSanity.isValidYear(0))
        assertTrue(TagSanity.isValidYear(1999))
    }
}
