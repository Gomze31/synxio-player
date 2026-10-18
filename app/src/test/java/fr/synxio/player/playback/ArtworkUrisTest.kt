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
}
