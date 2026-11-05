package fr.synxio.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUrisTest {

    private val sha = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `pochette privee de la cascade - servie sous art, par son empreinte`() {
        val uri = "file:///data/user/0/fr.synxio.player/files/meta-artwork/$sha.jpg"
        assertEquals(listOf(ArtworkUris.SEGMENT_ART, "$sha.jpg"), ArtworkUris.providerSegments(uri))
    }

    @Test
    fun `fichier du dossier prive qui n'est pas une empreinte - refuse`() {
        assertNull(ArtworkUris.providerSegments("file:///data/user/0/fr.synxio.player/files/meta-artwork/../databases/synxio.db"))
        assertFalse(ArtworkUris.isPrivateArtName("synxio.db"))
    }

    @Test
    fun `image de dossier - chemin decode, espaces et plus compris`() {
        val uri = "file:///storage/emulated/0/Music/Daft%20Punk/Discovery%2B/cover.jpg"
        assertEquals(
            listOf(ArtworkUris.SEGMENT_FOLDER, "/storage/emulated/0/Music/Daft Punk/Discovery+/cover.jpg"),
            ArtworkUris.providerSegments(uri),
        )
    }

    @Test
    fun `adresses deja lisibles - laissees telles quelles`() {
        assertNull(ArtworkUris.providerSegments("content://media/external/audio/albumart/12"))
        assertNull(ArtworkUris.providerSegments("content://media/external/audio/media/5/albumart?v=3"))
        assertNull(ArtworkUris.providerSegments("https://example.com/logo.png"))
    }

    @Test
    fun `le fournisseur ne sert que des images du stockage partage`() {
        assertTrue(ArtworkUris.isServableFolderImage("/storage/emulated/0/Music/A/folder.PNG"))
        assertTrue(ArtworkUris.isServableFolderImage("/storage/1A2B-3C4D/Musique/cover.webp"))
        assertFalse(ArtworkUris.isServableFolderImage("/data/user/0/fr.synxio.player/shared_prefs/x.jpg"))
        assertFalse(ArtworkUris.isServableFolderImage("/storage/emulated/0/../../data/x.jpg"))
        assertFalse(ArtworkUris.isServableFolderImage("/storage/emulated/0/Music/A/notes.txt"))
        assertFalse(ArtworkUris.isServableFolderImage("storage/emulated/0/a.jpg"))
    }

    @Test
    fun `types MIME`() {
        assertEquals("image/png", ArtworkUris.mimeTypeFor("cover.png"))
        assertEquals("image/webp", ArtworkUris.mimeTypeFor("a.WEBP"))
        assertEquals("image/jpeg", ArtworkUris.mimeTypeFor("$sha.jpg"))
    }

    @Test
    fun `pochette MediaStore - segment album avec l'identifiant`() {
        assertEquals(listOf("album", "42"), ArtworkUris.albumArtSegments("content://media/external/audio/albumart/42"))
        assertNull(ArtworkUris.albumArtSegments("content://media/external/audio/albumart/42/x"))
        assertNull(ArtworkUris.albumArtSegments("content://media/external/audio/albumart/abc"))
        assertNull(ArtworkUris.albumArtSegments("file:///storage/emulated/0/Music/cover.jpg"))
    }

    @Test
    fun `taille demandee - bornee entre 64 et 2048, sinon ignoree`() {
        assertEquals(512, ArtworkUris.sizeOf("512"))
        assertEquals(64, ArtworkUris.sizeOf("10"))
        assertEquals(2048, ArtworkUris.sizeOf("9000"))
        assertNull(ArtworkUris.sizeOf(null))
        assertNull(ArtworkUris.sizeOf("grand"))
        assertNull(ArtworkUris.sizeOf("0"))
    }

    @Test
    fun `mosaique - un a quatre identifiants de titres`() {
        assertEquals(listOf(1L, 22L, 333L, 4L), ArtworkUris.mosaicIds("1,22,333,4"))
        assertEquals(listOf(7L), ArtworkUris.mosaicIds("7"))
        assertNull(ArtworkUris.mosaicIds("1,2,3,4,5"))
        assertNull(ArtworkUris.mosaicIds(""))
        assertNull(ArtworkUris.mosaicIds("1,,2"))
        assertNull(ArtworkUris.mosaicIds("a,b"))
    }
}
