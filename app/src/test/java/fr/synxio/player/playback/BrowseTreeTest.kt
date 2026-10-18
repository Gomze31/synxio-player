package fr.synxio.player.playback

import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.Genre
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.RadioStation
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.SearchResults
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTreeTest {

    private val s1 = song(id = 1, title = "Alors on danse", artist = "Stromae", album = "Cheese", track = 1)
    private val s2 = song(id = 2, title = "Te quiero", artist = "Stromae", album = "Cheese", track = 2)
    private val s3 = song(id = 3, title = "House'llelujah", artist = "Stromae", album = "Cheese", track = 3)
    private val s4 = song(id = 4, title = "Papaoutai", artist = "Stromae", album = "Racine carrée", track = 1)
    private val s5 = song(id = 5, title = "One More Time", artist = "Daft Punk", album = "Discovery", track = 1)

    private val cheese = Album(10, "Cheese", "Stromae", 1, 2010, listOf(s3, s1, s2))
    private val racine = Album(11, "Racine carrée", "Stromae", 1, 2013, listOf(s4))
    private val discovery = Album(12, "Discovery", "Daft Punk", 2, 2001, listOf(s5))
    private val stromae = Artist(1, "Stromae", listOf(cheese, racine))
    private val daftPunk = Artist(2, "Daft Punk", listOf(discovery))

    private fun radio(uuid: String, name: String, tags: String = "") = RadioStation(
        stationUuid = uuid, name = name, url = "https://radio/$uuid", urlResolved = "https://radio/$uuid",
        homepage = "", favicon = "", tags = tags, country = "France", language = "fr", votes = 0,
        codec = "MP3", bitrate = 128, hls = 0,
    )

    private class FakeSource(
        override var hasPermission: Boolean = true,
        override var hasScanned: Boolean = true,
        override var songs: List<Song> = emptyList(),
        override var albums: List<Album> = emptyList(),
        override var artists: List<Artist> = emptyList(),
        override var genres: List<Genre> = emptyList(),
        override var playlists: List<Playlist> = emptyList(),
        override var favorites: List<Song> = emptyList(),
        override var recentlyPlayed: List<Song> = emptyList(),
        override var mostPlayed: List<Song> = emptyList(),
        override var recentlyAdded: List<Song> = emptyList(),
        override var radios: List<RadioStation> = emptyList(),
    ) : BrowseSource {
        override fun search(query: String, limitPerSection: Int): SearchResults {
            val q = query.lowercase()
            return SearchResults(
                songs = songs.filter { it.title.lowercase().contains(q) },
                albums = albums.filter { it.title.lowercase().contains(q) },
                artists = artists.filter { it.name.lowercase().contains(q) },
            )
        }
    }

    private fun fullSource() = FakeSource(
        songs = listOf(s1, s2, s3, s4, s5),
        albums = listOf(cheese, racine, discovery),
        artists = listOf(stromae, daftPunk),
        genres = listOf(Genre("Électro", listOf(s5))),
        playlists = listOf(Playlist(7, "Route", 0, 0, listOf(s5, s1)), Playlist(8, "Vide", 0, 0, emptyList())),
        favorites = listOf(s4),
        radios = listOf(radio("fip", "FIP", "jazz,eclectic")),
    )

    /** Mélange prévisible : l'ordre inverse. */
    private fun tree(source: BrowseSource = fullSource()) = BrowseTree(source) { it.reversed() }

    @Test
    fun `racine - quatre onglets dans l'ordre, chacun avec son style`() {
        val root = tree().children(BrowseIds.ROOT)
        assertEquals(
            listOf(BrowseIds.TAB_HOME, BrowseIds.TAB_LIBRARY, BrowseIds.TAB_PLAYLISTS, BrowseIds.TAB_RADIOS),
            root.map { it.id },
        )
        assertTrue(root.all { it.kind.browsable && it.art is NodeArt.Icon })
        assertEquals(ChildStyle.GRID, root.first { it.id == BrowseIds.TAB_RADIOS }.playableStyle)
        assertEquals(ChildStyle.CATEGORY_LIST, root.first { it.id == BrowseIds.TAB_LIBRARY }.browsableStyle)
    }

    @Test
    fun `bibliotheque - albums en grille, artistes en grille, genres et titres en liste`() {
        val library = tree().children(BrowseIds.TAB_LIBRARY)
        assertEquals(
            listOf(BrowseIds.CAT_ALBUMS, BrowseIds.CAT_ARTISTS, BrowseIds.CAT_GENRES, BrowseIds.CAT_SONGS),
            library.map { it.id },
        )
        assertEquals(ChildStyle.GRID, library[0].browsableStyle)
        assertEquals(ChildStyle.GRID, library[1].browsableStyle)
        assertEquals(ChildStyle.LIST, library[2].browsableStyle)
        assertEquals(ChildStyle.LIST, library[3].playableStyle)
        assertEquals("3 albums", library[0].subtitle)
        assertEquals("5 titres", library[3].subtitle)
    }

    @Test
    fun `accueil - lecture aleatoire puis seulement les categories non vides`() {
        val home = tree().children(BrowseIds.TAB_HOME)
        assertEquals(listOf(BrowseIds.SHUFFLE_ALL, BrowseIds.CAT_FAVORITES), home.map { it.id })
        assertEquals(NodeKind.ACTION, home[0].kind)
        assertTrue(home[0].kind.playable)
        assertEquals("1 titre", home[1].subtitle)
    }

    @Test
    fun `album - aleatoire en tete, titres dans l'ordre des pistes, identifiants en contexte`() {
        val parent = BrowseIds.ALBUM_PREFIX + cheese.id
        val children = tree().children(parent)
        assertEquals(BrowseIds.SHUFFLE_PREFIX + parent, children[0].id)
        val songs = children.drop(1)
        assertEquals(listOf(1L, 2L, 3L), songs.map { it.song!!.id })
        assertEquals(BrowseIds.songInContext(parent, 1), songs[0].id)
        // Dans l'album, le sous-titre se limite à l'artiste.
        assertEquals("Stromae", songs[0].subtitle)
        assertTrue(songs.all { it.kind == NodeKind.SONG && it.art is NodeArt.OfSong })
    }

    @Test
    fun `titre choisi dans un album - la file est l'album, a partir de ce titre`() {
        val parent = BrowseIds.ALBUM_PREFIX + cheese.id
        val queue = tree().queueFor(BrowseIds.songInContext(parent, 2))!!
        assertEquals(listOf(1L, 2L, 3L), queue.songs.map { it.id })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun `titre choisi dans une playlist - l'ordre de la playlist est garde`() {
        val parent = BrowseIds.PLAYLIST_PREFIX + 7
        val queue = tree().queueFor(BrowseIds.songInContext(parent, 1))!!
        assertEquals(listOf(5L, 1L), queue.songs.map { it.id })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun `lecture aleatoire d'un parent - ses titres melanges, depuis le debut`() {
        val queue = tree().queueFor(BrowseIds.SHUFFLE_PREFIX + BrowseIds.ALBUM_PREFIX + cheese.id)!!
        assertEquals(listOf(3L, 2L, 1L), queue.songs.map { it.id })
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `identifiant inconnu ou titre absent du parent - aucune file`() {
        assertNull(tree().queueFor("synxio://nope|1"))
        assertNull(tree().queueFor(BrowseIds.songInContext(BrowseIds.ALBUM_PREFIX + cheese.id, 5)))
        assertNull(tree().queueFor("42"))
    }

    @Test
    fun `contexte - le titre se lit apres le dernier separateur, meme si le nom en contient un`() {
        val id = BrowseIds.songInContext(BrowseIds.ARTIST_PREFIX + "AC|DC", 9)
        assertEquals(BrowseIds.ARTIST_PREFIX + "AC|DC" to 9L, BrowseIds.parseSongInContext(id))
        assertNull(BrowseIds.parseSongInContext(BrowseIds.CAT_SONGS))
    }

    @Test
    fun `artiste a plusieurs albums - aleatoire, section albums puis section titres`() {
        val children = tree().children(BrowseIds.ARTIST_PREFIX + "Stromae")
        assertEquals(NodeKind.ACTION, children[0].kind)
        val albums = children.filter { it.kind == NodeKind.ALBUM }
        assertEquals(listOf("Racine carrée", "Cheese"), albums.map { it.title })
        assertTrue(albums.all { it.groupTitle == "Albums" })
        val titles = children.filter { it.kind == NodeKind.SONG }
        assertEquals(4, titles.size)
        assertTrue(titles.all { it.groupTitle == "Titres" })
    }

    @Test
    fun `playlists - les playlists vides sont masquees`() {
        val playlists = tree().children(BrowseIds.TAB_PLAYLISTS)
        assertEquals(listOf(BrowseIds.PLAYLIST_PREFIX + 7), playlists.map { it.id })
        assertEquals("2 titres", playlists[0].subtitle)
    }

    @Test
    fun `aucune playlist - un message plutot qu'un ecran vide`() {
        val source = fullSource().apply { playlists = emptyList() }
        val children = tree(source).children(BrowseIds.TAB_PLAYLISTS)
        assertEquals(1, children.size)
        assertEquals(NodeKind.INFO, children[0].kind)
        assertFalse(children[0].kind.playable || children[0].kind.browsable)
    }

    @Test
    fun `sans permission - message d'autorisation, mais les radios restent accessibles`() {
        val source = fullSource().apply { hasPermission = false }
        val library = tree(source).children(BrowseIds.TAB_LIBRARY)
        assertEquals(listOf(BrowseIds.INFO_PREFIX + "permission"), library.map { it.id })
        val radios = tree(source).children(BrowseIds.TAB_RADIOS)
        assertEquals(listOf(BrowseIds.RADIO_PREFIX + "fip"), radios.map { it.id })
        assertEquals("Jazz", radios[0].subtitle)
        assertTrue(tree(source).search("stromae").isEmpty())
    }

    @Test
    fun `scan en cours - message de chargement`() {
        val source = FakeSource(hasScanned = false)
        val home = tree(source).children(BrowseIds.TAB_HOME)
        assertEquals(listOf(BrowseIds.INFO_PREFIX + "loading"), home.map { it.id })
    }

    @Test
    fun `bibliotheque vide apres scan - aucun titre trouve`() {
        val children = tree(FakeSource()).children(BrowseIds.TAB_LIBRARY)
        assertEquals(listOf(BrowseIds.INFO_PREFIX + "empty"), children.map { it.id })
    }

    @Test
    fun `aucune radio favorite - message`() {
        val source = fullSource().apply { radios = emptyList() }
        assertEquals(listOf(BrowseIds.INFO_PREFIX + "radios"), tree(source).children(BrowseIds.TAB_RADIOS).map { it.id })
    }

    @Test
    fun `radio - retrouvee par son identifiant`() {
        assertEquals("FIP", tree().radioFor(BrowseIds.RADIO_PREFIX + "fip")?.name)
        assertNull(tree().radioFor(BrowseIds.RADIO_PREFIX + "inconnue"))
        assertNull(tree().radioFor("fip"))
    }

    @Test
    fun `element isole - categorie, titre en contexte, titre nu`() {
        val t = tree()
        assertEquals("Albums", t.item(BrowseIds.CAT_ALBUMS)?.title)
        assertEquals("Te quiero", t.item(BrowseIds.songInContext(BrowseIds.ALBUM_PREFIX + cheese.id, 2))?.title)
        assertEquals("2", t.item("2")?.id)
        assertNull(t.item("999"))
    }

    @Test
    fun `recherche - sections artistes, albums, titres`() {
        val results = tree().search("stromae")
        val artist = results.first { it.kind == NodeKind.ARTIST }
        assertEquals("Artistes", artist.groupTitle)
        val songs = tree().search("te quiero").filter { it.kind == NodeKind.SONG }
        assertEquals(listOf(2L), songs.map { it.song!!.id })
        assertEquals("Titres", songs[0].groupTitle)
        // Le titre trouvé se joue dans la file de la recherche.
        val queue = tree().queueFor(songs[0].id)!!
        assertEquals(2L, queue.songs[queue.startIndex].id)
    }

    @Test
    fun `voix - artiste reconnu par l'assistant`() {
        val queue = tree().resolveVoice(VoiceRequest("daft punk", focus = VoiceRequest.FOCUS_ARTIST, artist = "Daft Punk"))!!
        assertEquals(listOf(5L), queue.songs.map { it.id })
    }

    @Test
    fun `voix - requete vide, toute la bibliotheque melangee`() {
        val queue = tree().resolveVoice(VoiceRequest(""))!!
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), queue.songs.map { it.id })
    }

    @Test
    fun `voix - « mes favoris »`() {
        val queue = tree().resolveVoice(VoiceRequest("Mes favoris"))!!
        assertEquals(listOf(4L), queue.songs.map { it.id })
    }

    @Test
    fun `voix - titre precis, joue dans son album`() {
        val queue = tree().resolveVoice(
            VoiceRequest("te quiero", focus = VoiceRequest.FOCUS_TITLE, title = "Te quiero", artist = "Stromae")
        )!!
        assertEquals(listOf(1L, 2L, 3L), queue.songs.map { it.id })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun `voix - sans mot-cle, l'artiste passe avant les titres`() {
        val queue = tree().resolveVoice(VoiceRequest("stromae"))!!
        assertEquals(4, queue.songs.size)
    }

    @Test
    fun `voix - rien ne correspond, ou pas de permission`() {
        assertNull(tree().resolveVoice(VoiceRequest("zzzz")))
        assertNull(tree(fullSource().apply { hasPermission = false }).resolveVoice(VoiceRequest("stromae")))
    }

    @Test
    fun `pluriels`() {
        assertEquals("0 titre", BrowseTree.titles(0))
        assertEquals("1 titre", BrowseTree.titles(1))
        assertEquals("2 titres", BrowseTree.titles(2))
        assertNotNull(BrowseTree.plural(3, "album"))
    }
}
