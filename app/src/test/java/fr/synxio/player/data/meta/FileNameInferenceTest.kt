package fr.synxio.player.data.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les noms de fichiers viennent de l'appareil de test, là où la bibliothèque affichait « Artiste inconnu ». */
class FileNameInferenceTest {

    private val root = "/storage/emulated/0"

    private fun infer(path: String, tagTitle: String? = null, known: Set<String> = emptySet()) =
        FileNameInference.infer(path, tagTitle) { it.lowercase() in known }.metadata

    @Test
    fun `artiste puis titre, bruit de video retire`() {
        val meta = infer("$root/Son v2/Laura - Plus de problèmes (Clip officiel).mp3")
        assertEquals("Laura", meta.artist)
        assertEquals("Plus de problèmes", meta.title)
    }

    @Test
    fun `titre puis artiste reconnu a la casse de phrase du titre`() {
        val meta = infer("$root/Son v2/Deuxième option - Coralie Fischer (Clip officiel).mp3")
        assertEquals("Coralie Fischer", meta.artist)
        assertEquals("Deuxième option", meta.title)

        val other = infer("$root/Son v2/Et tu grandis - Leyla Ever-White (Clip officiel).mp3")
        assertEquals("Leyla Ever-White", other.artist)
        assertEquals("Et tu grandis", other.title)
    }

    @Test
    fun `la collaboration designe le cote artiste`() {
        val meta = infer("$root/Son v2/Grand Corps Malade ft. Suzane - Pendant 24h (Paroles).mp3")
        assertEquals("Grand Corps Malade ft. Suzane", meta.artist)
        assertEquals("Pendant 24h", meta.title)

        val cover = infer("$root/Son v2/Can You Feel The Love Tonight - The Lion King & Elton John (Peyton Parrish ROCK Cover).mp3")
        assertEquals("The Lion King & Elton John", cover.artist)
        assertEquals("Can You Feel The Love Tonight (Peyton Parrish ROCK Cover)", cover.title)
    }

    @Test
    fun `un artiste deja connu de la bibliotheque l emporte`() {
        val meta = infer("$root/Download/Liberté - César.mp3", known = setOf("césar"))
        assertEquals("César", meta.artist)
        assertEquals("Liberté", meta.title)
    }

    @Test
    fun `la precision entre parentheses reste avec le titre`() {
        val meta = infer("$root/Son v2/Ne pleure pas - Aslane (la berceuse de ma maman).mp3")
        assertEquals("Aslane", meta.artist)
        assertEquals("Ne pleure pas (la berceuse de ma maman)", meta.title)
    }

    @Test
    fun `le sous-titre apres un double espace est ignore`() {
        val meta = infer("$root/Son v2/KEENV - Que tu restes (Paroles)  Chanson Française.mp3")
        assertEquals("KEENV", meta.artist)
        assertEquals("Que tu restes", meta.title)
    }

    @Test
    fun `les segments de bruit et les mots parasites sont retires`() {
        val meta = infer("$root/Son v2/Pidi - Dans ma folie Parole FRPower - Parole.mp3")
        assertEquals("Pidi", meta.artist)
        assertEquals("Dans ma folie", meta.title)
    }

    @Test
    fun `titre by artiste`() {
        val meta = infer("$root/Son v2/Take Me Home, Country Roads by John Denver  acoustic cover by Jada Facer.mp3")
        assertEquals("John Denver", meta.artist)
        assertEquals("Take Me Home, Country Roads", meta.title)
    }

    @Test
    fun `le titre des tags est analyse quand il porte artiste et titre`() {
        // yt-dlp a remplacé « : » par « _ » dans le nom, le tag l'a gardé.
        val inference = FileNameInference.infer("$root/Download/Mentissa _ Et Bam (clip officiel).m4a", "Mentissa : Et Bam (clip officiel)")
        assertEquals("Mentissa", inference.metadata.artist)
        assertEquals("Et Bam", inference.metadata.title)
        // Rien ne permet de trancher : l'ordre inverse est gardé pour la recherche en ligne.
        assertNotNull(inference.alternative)
        assertEquals("Et Bam", inference.alternative!!.artist)
    }

    @Test
    fun `artiste et titre entre guillemets`() {
        val meta = infer("$root/Son v2/Liane Foly On a tous le droit.mp3", tagTitle = "Liane Foly \"On a tous le droit\"")
        assertEquals("Liane Foly", meta.artist)
        assertEquals("On a tous le droit", meta.title)
    }

    @Test
    fun `un titre de rip Piste N cede la place au nom du fichier`() {
        val meta = infer("$root/Son v2/Tourne les Violons.mp3", tagTitle = "Piste 20")
        assertEquals("Tourne les Violons", meta.title)
        assertNull(meta.artist)
    }

    @Test
    fun `un nombre en tete n est un numero de piste que suivi d un separateur`() {
        val meta = infer("$root/Son v2/50 Cent - In Da Club.mp3")
        assertEquals("50 Cent", meta.artist)
        assertEquals("In Da Club", meta.title)
        assertNull(meta.track)

        val numbered = FileNameInference.parseLeadingNumber("1-03 Le vent")
        assertEquals(3, numbered.track)
        assertEquals(1, numbered.disc)
        assertEquals("Le vent", numbered.rest)
        assertEquals(7, FileNameInference.parseLeadingNumber("07. Intro").track)
    }

    @Test
    fun `artiste, album, disque et piste depuis l arborescence`() {
        val meta = infer("$root/Music/Daft Punk/Discovery/CD2/03 - Digital Love.flac")
        assertEquals("Digital Love", meta.title)
        assertEquals("Discovery", meta.album)
        assertEquals("Daft Punk", meta.artist)
        assertEquals(2, meta.disc)
        assertEquals(3, meta.track)
    }

    @Test
    fun `dossier de rip artiste - album nettoye de sa signature`() {
        val meta = infer("$root/Son v2/Jeck, Carla - Oxygène FLAC HI-RES-G11/05. Le vent.flac")
        assertEquals("Oxygène", meta.album)
        assertEquals("Jeck, Carla", meta.albumArtist)
        assertEquals(5, meta.track)

        val nrj = infer("$root/Son v2/NRJ 300% Hits 2024-G11DF3/Titre.mp3")
        assertEquals("NRJ 300% Hits 2024", nrj.album)
    }

    @Test
    fun `les dossiers fourre-tout ne donnent pas d album`() {
        assertNull(infer("$root/Son v2/Titre.mp3").album)
        assertNull(infer("$root/Download/Titre.mp3").album)
        assertNull(infer("$root/Music/Synxio/Titre.mp3").album)
        assertTrue(FileNameInference.isGeneric("Son v2"))
        assertTrue(FileNameInference.isGeneric("Download"))
        assertFalse(FileNameInference.isGeneric("NRJ Summer Hits 2024"))
    }

    @Test
    fun `un nom de site de conversion sans contenu ne donne rien`() {
        val parsed = FileNameInference.parseArtistTitle("ssstik.io_1774251960633")
        assertNull(parsed.artist)
        assertNull(parsed.title)
        val site = FileNameInference.parseArtistTitle("y2mate.com - Angèle - Balance ton quoi")
        assertEquals("Angèle", site.artist)
        assertEquals("Balance ton quoi", site.title)
    }
}
