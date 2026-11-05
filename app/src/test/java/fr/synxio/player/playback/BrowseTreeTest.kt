package fr.synxio.player.playback

import fr.synxio.player.data.db.PodcastProgressEntity
import fr.synxio.player.data.model.DailyMix
import fr.synxio.player.data.model.DailyMixKind
import fr.synxio.player.data.model.Memory
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
        override var audiobooks: List<Song> = emptyList(),
        override var podcasts: List<Song> = emptyList(),
        override var savedPositions: Map<Long, PodcastProgressEntity> = emptyMap(),
        override var dailyMixes: List<DailyMix> = emptyList(),
        override var memories: List<Memory> = emptyList(),
        override var playCounts: Map<Long, Int> = emptyMap(),
        override var lastQueue: BrowseQueue? = null,
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

    /** Mélange prévisible : l'ordre inverse. Jour fixe : « Surprends-moi » stable. */
    private fun tree(source: BrowseSource = fullSource(), day: Long = 20_762L) = BrowseTree(source, { it.reversed() }, { day })

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
    fun `accueil - raccourcis seulement, groupes vides omis`() {
        val home = tree().children(BrowseIds.TAB_HOME)
        assertEquals(listOf(BrowseIds.SHUFFLE_ALL, BrowseIds.CAT_FAVORITES, BrowseIds.SURPRISE), home.map { it.id })
        assertTrue(home.all { it.groupTitle == BrowseTree.GROUP_SHORTCUTS })
        assertEquals(NodeKind.ACTION, home[0].kind)
        assertTrue(home[0].kind.playable)
        assertEquals("1 titre", home[1].subtitle)
    }

    @Test
    fun `accueil - groupes dans l'ordre reprendre, pour toi, raccourcis, recents`() {
        val mix = DailyMix("daily", DailyMixKind.DAILY, "Ton mix du jour", "", listOf(s1, s5))
        val memory = Memory.FirstTimes(listOf(s2, s4, s5))
        val source = fullSource().apply {
            dailyMixes = listOf(mix)
            memories = listOf(memory)
            recentlyPlayed = listOf(s3)
            lastQueue = BrowseQueue(listOf(s5, s1), startIndex = 1, startPositionMs = 42_000L)
        }
        val home = tree(source).children(BrowseIds.TAB_HOME)
        assertEquals(
            listOf(BrowseIds.LAST_QUEUE, BrowseIds.MIX_PREFIX + "daily", BrowseIds.MEMORY_PREFIX + "firsttimes",
                BrowseIds.SHUFFLE_ALL, BrowseIds.CAT_FAVORITES, BrowseIds.SURPRISE, BrowseIds.CAT_RECENTLY_PLAYED),
            home.map { it.id },
        )
        assertEquals(
            listOf(BrowseTree.GROUP_RESUME, BrowseTree.GROUP_FOR_YOU, BrowseTree.GROUP_FOR_YOU, BrowseTree.GROUP_SHORTCUTS,
                BrowseTree.GROUP_SHORTCUTS, BrowseTree.GROUP_SHORTCUTS, BrowseTree.GROUP_RECENT),
            home.map { it.groupTitle },
        )
        val last = home[0]
        assertEquals("Dernière file", last.title)
        assertEquals("Alors on danse · 2 titres", last.subtitle)
        assertEquals(NodeKind.ACTION, last.kind)
        assertEquals(ChildStyle.GRID, home[1].selfStyle)
        assertTrue(home[1].art is NodeArt.Mosaic)
        assertEquals("Tes premières fois du mois", home[2].title)
    }

    @Test
    fun `mix - ses titres dans l'ordre, un titre choisi joue le mix`() {
        val mix = DailyMix("daily", DailyMixKind.DAILY, "Ton mix du jour", "", listOf(s5, s1, s2))
        val t = tree(fullSource().apply { dailyMixes = listOf(mix) })
        val id = BrowseIds.MIX_PREFIX + "daily"
        val children = t.children(id)
        assertEquals(BrowseIds.SHUFFLE_PREFIX + id, children[0].id)
        assertEquals(listOf(5L, 1L, 2L), children.drop(1).map { it.song!!.id })
        val queue = t.queueFor(BrowseIds.songInContext(id, 1))!!
        assertEquals(listOf(5L, 1L, 2L), queue.songs.map { it.id })
        assertEquals(1, queue.startIndex)
        assertEquals("Ton mix du jour", t.item(id)?.title)
    }

    @Test
    fun `souvenir - les titres de la carte, mosaique sur quatre albums distincts`() {
        // La fabrique de test met tous les titres dans l'album 1 : on distingue les albums ici.
        val memory = Memory.ThatDay(1, listOf(s1, s2, s4.copy(albumId = 11), s5.copy(albumId = 12)))
        val t = tree(fullSource().apply { memories = listOf(memory) })
        val node = t.children(BrowseIds.TAB_HOME).first { it.id == BrowseIds.MEMORY_PREFIX + "thatday-1" }
        assertEquals("Ce jour-là, il y a 1 an", node.title)
        assertEquals(listOf(1L, 4L, 5L), (node.art as NodeArt.Mosaic).songs.map { it.id })
        assertEquals(listOf(1L, 2L, 4L, 5L), t.children(node.id).filter { it.kind == NodeKind.SONG }.map { it.song!!.id })
    }

    @Test
    fun `surprends-moi - favoris puis titres rarement joues, deux par artiste, stable dans la journee`() {
        val source = fullSource().apply {
            favorites = listOf(s4)
            playCounts = mapOf(1L to 5, 2L to 0, 3L to 1, 5L to 0)
        }
        val first = tree(source).queueFor(BrowseIds.SURPRISE)!!.songs
        val second = tree(source).queueFor(BrowseIds.SURPRISE)!!.songs
        assertEquals(first.map { it.id }, second.map { it.id })
        // s1 est joué cinq fois : écarté. s4 favori + un seul autre Stromae + Daft Punk.
        assertTrue(first.any { it.id == 4L })
        assertTrue(first.any { it.id == 5L })
        assertEquals(3, first.size)
        assertTrue(first.count { it.artist == "Stromae" } <= 2)
        assertFalse(first.any { it.id == 1L })
    }

    @Test
    fun `surprends-moi - absent sous trois titres`() {
        val source = FakeSource(songs = listOf(s1, s2), albums = listOf(cheese))
        assertTrue(tree(source).children(BrowseIds.TAB_HOME).none { it.id == BrowseIds.SURPRISE })
        assertNull(tree(source).queueFor(BrowseIds.SURPRISE))
    }

    @Test
    fun `tuiles jouables - albums, playlists, genres, mixs, souvenirs, mais pas les livres`() {
        val mix = DailyMix("daily", DailyMixKind.DAILY, "Ton mix du jour", "", listOf(s5, s1))
        val memory = Memory.FirstTimes(listOf(s2, s4, s5))
        val t = tree(listeningSource().apply { dailyMixes = listOf(mix); memories = listOf(memory) })
        assertTrue(t.children(BrowseIds.CAT_ALBUMS).all { it.kind == NodeKind.FOLDER_PLAYABLE })
        assertTrue(t.children(BrowseIds.TAB_PLAYLISTS).all { it.kind == NodeKind.FOLDER_PLAYABLE })
        assertTrue(t.children(BrowseIds.CAT_GENRES).all { it.kind == NodeKind.FOLDER_PLAYABLE })
        val home = t.children(BrowseIds.TAB_HOME)
        assertEquals(NodeKind.FOLDER_PLAYABLE, home.first { it.id == BrowseIds.MIX_PREFIX + "daily" }.kind)
        assertEquals(NodeKind.FOLDER_PLAYABLE, home.first { it.id == BrowseIds.MEMORY_PREFIX + "firsttimes" }.kind)
        assertEquals(NodeKind.ALBUM, t.children(BrowseIds.CAT_AUDIOBOOKS).single().kind)
        assertTrue(NodeKind.FOLDER_PLAYABLE.browsable && NodeKind.FOLDER_PLAYABLE.playable)
    }

    @Test
    fun `tuile jouable - l'album entier dans l'ordre, le genre melange, le mix tel quel`() {
        val mix = DailyMix("daily", DailyMixKind.DAILY, "Ton mix du jour", "", listOf(s5, s1))
        val t = tree(fullSource().apply { dailyMixes = listOf(mix); genres = listOf(Genre("Électro", listOf(s5, s1, s2))) })
        assertEquals(listOf(1L, 2L, 3L), t.queueFor(BrowseIds.ALBUM_PREFIX + cheese.id)!!.songs.map { it.id })
        assertEquals(listOf(5L, 1L), t.queueFor(BrowseIds.PLAYLIST_PREFIX + 7)!!.songs.map { it.id })
        assertEquals(listOf(2L, 1L, 5L), t.queueFor(BrowseIds.GENRE_PREFIX + "Électro")!!.songs.map { it.id })
        assertEquals(listOf(5L, 1L), t.queueFor(BrowseIds.MIX_PREFIX + "daily")!!.songs.map { it.id })
        assertEquals(0, t.queueFor(BrowseIds.ALBUM_PREFIX + cheese.id)!!.startIndex)
        assertNull(t.queueFor(BrowseIds.PLAYLIST_PREFIX + 8))
        assertNull(t.queueFor(BrowseIds.ALBUM_PREFIX + 999))
    }

    @Test
    fun `actions - par type de noeud`() {
        val mix = DailyMix("daily", DailyMixKind.DAILY, "Ton mix du jour", "", listOf(s5, s1))
        val t = tree(fullSource().apply { dailyMixes = listOf(mix) })
        val album = BrowseIds.ALBUM_PREFIX + cheese.id
        val songs = t.children(album).filter { it.kind == NodeKind.SONG }
        assertEquals(listOf("favorite", "play_next", "add_to_queue", "shuffle"), songs[0].actions)
        // s4 est favori : l'action proposée est le retrait.
        val favorite = t.children(BrowseIds.CAT_FAVORITES).first { it.song?.id == 4L }
        assertEquals("unfavorite", favorite.actions[0])
        assertEquals(listOf("play_next", "add_to_queue", "shuffle"), t.children(BrowseIds.CAT_ALBUMS)[0].actions)
        assertEquals(listOf("play_next", "add_to_queue", "shuffle"), t.children(BrowseIds.TAB_PLAYLISTS)[0].actions)
        assertEquals(listOf("play_next", "add_to_queue", "shuffle"), t.children(BrowseIds.TAB_HOME).first { it.id == BrowseIds.MIX_PREFIX + "daily" }.actions)
        assertTrue(t.children(BrowseIds.TAB_RADIOS)[0].actions.isEmpty())
        assertTrue(t.children(BrowseIds.TAB_HOME).first { it.id == BrowseIds.SHUFFLE_ALL }.actions.isEmpty())
    }

    @Test
    fun `actions - execution sur un titre et sur un album`() {
        val t = tree()
        val album = BrowseIds.ALBUM_PREFIX + cheese.id
        val song = BrowseIds.songInContext(album, 2)

        val favorite = t.action("favorite", song) as AutoAction.SetFavorite
        assertEquals(2L, favorite.song.id)
        assertTrue(favorite.favorite)
        assertEquals("Ajouté aux favoris", favorite.message)
        val unfavorite = t.action("unfavorite", BrowseIds.songInContext(BrowseIds.CAT_FAVORITES, 4)) as AutoAction.SetFavorite
        assertFalse(unfavorite.favorite)
        assertEquals("Retiré des favoris", unfavorite.message)

        assertEquals(listOf(2L), (t.action("play_next", song) as AutoAction.PlayNext).songs.map { it.id })
        assertEquals(listOf(2L), (t.action("add_to_queue", song) as AutoAction.AddToQueue).songs.map { it.id })
        // Aléatoire sur un titre : son parent mélangé.
        assertEquals(listOf(3L, 2L, 1L), (t.action("shuffle", song) as AutoAction.Shuffle).songs.map { it.id })

        assertEquals(listOf(1L, 2L, 3L), (t.action("play_next", album) as AutoAction.PlayNext).songs.map { it.id })
        assertEquals("Sera lu ensuite", t.action("play_next", album)!!.message)
        assertEquals(listOf(3L, 2L, 1L), (t.action("shuffle", album) as AutoAction.Shuffle).songs.map { it.id })
    }

    @Test
    fun `actions - refusees sur une radio, un album pour le favori, un identifiant inconnu`() {
        val t = tree()
        assertNull(t.action("favorite", BrowseIds.ALBUM_PREFIX + cheese.id))
        assertNull(t.action("play_next", BrowseIds.RADIO_PREFIX + "fip"))
        assertNull(t.action("play_next", "synxio://nope"))
        assertNull(t.action("danser", BrowseIds.ALBUM_PREFIX + cheese.id))
    }

    @Test
    fun `derniere file - reprise a l'index et a la position sauvegardes`() {
        val source = fullSource().apply { lastQueue = BrowseQueue(listOf(s5, s1), 1, 42_000L) }
        val queue = tree(source).queueFor(BrowseIds.LAST_QUEUE)!!
        assertEquals(listOf(5L, 1L), queue.songs.map { it.id })
        assertEquals(1, queue.startIndex)
        assertEquals(42_000L, queue.startPositionMs)
        assertNull(tree().queueFor(BrowseIds.LAST_QUEUE))
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
        val albums = children.filter { it.kind == NodeKind.FOLDER_PLAYABLE }
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
        // Même album (fabrique de test) : une seule pochette dans la mosaïque.
        assertEquals(listOf(5L), (playlists[0].art as NodeArt.Mosaic).songs.map { it.id })
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
    fun `voix - rien ne correspond, favoris au hasard, sans permission, rien`() {
        val queue = tree().resolveVoice(VoiceRequest("zzzz"))!!
        assertEquals(listOf(4L), queue.songs.map { it.id })
        assertNull(tree(fullSource().apply { hasPermission = false }).resolveVoice(VoiceRequest("stromae")))
        // Sans favori : toute la bibliothèque.
        assertEquals(5, tree(fullSource().apply { favorites = emptyList() }).resolveVoice(VoiceRequest("zzzz"))!!.songs.size)
    }

    /** Titres datés : s1 2010, s2 1995, s3 1998, s4 2013, s5 2001, dans les albums aussi. */
    private fun datedSource(): FakeSource {
        val d1 = s1.copy(year = 2010); val d2 = s2.copy(year = 1995); val d3 = s3.copy(year = 1998)
        val d4 = s4.copy(year = 2013); val d5 = s5.copy(year = 2001)
        val cheeseD = cheese.copy(songs = listOf(d3, d1, d2))
        val racineD = racine.copy(songs = listOf(d4))
        val discoveryD = discovery.copy(songs = listOf(d5))
        return fullSource().apply {
            songs = listOf(d1, d2, d3, d4, d5)
            albums = listOf(cheeseD, racineD, discoveryD)
            artists = listOf(Artist(1, "Stromae", listOf(cheeseD, racineD)), Artist(2, "Daft Punk", listOf(discoveryD)))
            favorites = listOf(d4)
            genres = listOf(Genre("Électro", listOf(d5, d1)))
        }
    }

    @Test
    fun `voix - periode sans genre, et genre inconnu avec periode`() {
        val t = tree(datedSource())
        assertEquals(listOf(3L, 2L), t.resolveVoice(VoiceRequest("des années 90"))!!.songs.map { it.id })
        assertEquals(listOf(3L, 2L), t.resolveVoice(VoiceRequest("du metal des années 90"))!!.songs.map { it.id })
        assertEquals(listOf(2L), t.resolveVoice(VoiceRequest("de 1995"))!!.songs.map { it.id })
    }

    @Test
    fun `voix - artiste et genre filtres par la periode, filtre vide ignore`() {
        val t = tree(datedSource())
        assertEquals(listOf(1L, 4L), t.resolveVoice(VoiceRequest("stromae des années 2010"))!!.songs.map { it.id })
        assertEquals(listOf(1L), t.resolveVoice(VoiceRequest("de l'électro de 2010"))!!.songs.map { it.id })
        // Aucun titre de Daft Punk dans les années 90 : on garde l'artiste entier.
        assertEquals(listOf(5L), t.resolveVoice(VoiceRequest("daft punk des années 90"))!!.songs.map { it.id })
    }

    @Test
    fun `voix - genre ou periode sans correspondance, favoris au hasard`() {
        val t = tree(datedSource())
        assertEquals(listOf(4L), t.resolveVoice(VoiceRequest("du metal"))!!.songs.map { it.id })
        assertEquals(listOf(4L), t.resolveVoice(VoiceRequest("des années 70"))!!.songs.map { it.id })
    }

    @Test
    fun `voix - la playlist Années 80 gagne sur la periode`() {
        val source = datedSource().apply { playlists = listOf(Playlist(9, "Années 80", 0, 0, listOf(s1))) }
        assertEquals(listOf(1L), tree(source).resolveVoice(VoiceRequest("joue années 80"))!!.songs.map { it.id })
        assertEquals(listOf(1L), tree(source).resolveVoice(VoiceRequest("lance les années 80"))!!.songs.map { it.id })
    }

    @Test
    fun `voix - reprendre un livre, reprendre la derniere file`() {
        val t = tree(listeningSource().apply { lastQueue = BrowseQueue(listOf(s5, s1), 1, 42_000L) })
        val book = t.resolveVoice(VoiceRequest("continue"))!!
        assertEquals(listOf(100L, 101L, 102L), book.songs.map { it.id })
        assertEquals(1, book.startIndex)
        assertEquals(120_000L, book.startPositionMs)
        val last = t.resolveVoice(VoiceRequest("ce que j'écoutais"))!!
        assertEquals(listOf(5L, 1L), last.songs.map { it.id })
        assertEquals(42_000L, last.startPositionMs)
        // Rien à reprendre : repli sur les favoris.
        assertEquals(listOf(4L), tree().resolveVoice(VoiceRequest("continue"))!!.songs.map { it.id })
    }

    @Test
    fun `voix - mix du jour, mix par nom d'artiste, premier mix sinon`() {
        val daily = DailyMix("daily", DailyMixKind.DAILY, "Ton mix du jour", "", listOf(s1, s5))
        val stromae = DailyMix("artist-Stromae", DailyMixKind.ARTIST, "Mix Stromae", "", listOf(s2, s3))
        val t = tree(fullSource().apply { dailyMixes = listOf(daily, stromae) })
        assertEquals(listOf(1L, 5L), t.resolveVoice(VoiceRequest("mon mix du jour"))!!.songs.map { it.id })
        assertEquals(listOf(2L, 3L), t.resolveVoice(VoiceRequest("le mix stromae"))!!.songs.map { it.id })
        assertEquals(listOf(1L, 5L), t.resolveVoice(VoiceRequest("le mix de daft punk"))!!.songs.map { it.id })
        assertEquals(listOf(1L, 5L), t.resolveVoice(VoiceRequest("ma découverte du jour"))!!.songs.map { it.id })
    }

    @Test
    fun `voix - souvenirs et favoris melanges`() {
        val t = tree(fullSource().apply { memories = listOf(Memory.FirstTimes(listOf(s2, s4, s5))) })
        assertEquals(listOf(2L, 4L, 5L), t.resolveVoice(VoiceRequest("mes souvenirs"))!!.songs.map { it.id })
        assertEquals(listOf(4L), t.resolveVoice(VoiceRequest("mes favoris en aléatoire"))!!.songs.map { it.id })
    }

    @Test
    fun `voix - radio par son nom, album nomme radio sinon`() {
        val t = tree()
        val radio = t.resolveVoice(VoiceRequest("la radio fip"))!!
        assertEquals("fip", radio.radio?.stationUuid)
        assertTrue(radio.songs.isEmpty())
        val source = fullSource().apply {
            albums = albums + Album(13, "Radio Ga Ga", "Queen", 3, 1984, listOf(s5))
            radios = emptyList()
        }
        assertEquals(listOf(5L), tree(source).resolveVoice(VoiceRequest("joue radio ga ga"))!!.songs.map { it.id })
        assertNull(tree(source).resolveVoice(VoiceRequest("joue radio ga ga"))!!.radio)
    }

    @Test
    fun `erreur resoluble - seulement sans permission`() {
        assertEquals("Ouvrir Synxio sur le téléphone", BrowseTree.phoneActionLabel(hasPermission = false))
        assertNull(BrowseTree.phoneActionLabel(hasPermission = true))
    }

    @Test
    fun `pluriels`() {
        assertEquals("0 titre", BrowseTree.titles(0))
        assertEquals("1 titre", BrowseTree.titles(1))
        assertEquals("2 titres", BrowseTree.titles(2))
        assertNotNull(BrowseTree.plural(3, "album"))
    }

    // --- Livres audio et podcasts ------------------------------------------------------

    private fun chapter(id: Long, track: Int, book: String = "Dune") =
        song(id = id, title = "Partie $track", track = track, durationMs = 600_000, path = "/livres/$book/$track.mp3")
            .copy(isAudiobook = true)

    private fun episode(id: Long, title: String) =
        song(id = id, title = title, album = "Le Code a changé", durationMs = 1_200_000).copy(isPodcast = true)

    private val dune = listOf(chapter(101, 2), chapter(100, 1), chapter(102, 3))
    private val episodes = listOf(episode(200, "Épisode 1"), episode(201, "Épisode 2"))

    private fun at(songId: Long, positionMs: Long, lastPlayedSec: Long) =
        PodcastProgressEntity(songId = songId, positionMs = positionMs, lastPlayedSec = lastPlayedSec)

    private fun listeningSource() = fullSource().apply {
        audiobooks = dune
        podcasts = episodes
        savedPositions = listOf(
            at(100, 600_000, lastPlayedSec = 10), // chapitre 1 fini
            at(101, 120_000, lastPlayedSec = 20), // chapitre 2 entamé
            at(201, 300_000, lastPlayedSec = 5), // épisode 2 entamé
        ).associateBy { it.songId }
    }

    @Test
    fun `bibliotheque - livres audio et podcasts apres la musique`() {
        val library = tree(listeningSource()).children(BrowseIds.TAB_LIBRARY)
        assertEquals(
            listOf(BrowseIds.CAT_ALBUMS, BrowseIds.CAT_ARTISTS, BrowseIds.CAT_GENRES, BrowseIds.CAT_SONGS,
                BrowseIds.CAT_AUDIOBOOKS, BrowseIds.CAT_PODCASTS),
            library.map { it.id },
        )
        assertEquals("1 livre", library[4].subtitle)
        assertEquals("2 épisodes", library[5].subtitle)
    }

    @Test
    fun `livre - chapitres dans l'ordre, sans lecture aleatoire, progression en sous-titre`() {
        val t = tree(listeningSource())
        val book = t.children(BrowseIds.CAT_AUDIOBOOKS).single()
        assertEquals(BrowseIds.BOOK_PREFIX + "/livres/Dune", book.id)
        assertEquals("Dune", book.title)
        assertEquals("Chapitre 2 sur 3 · encore 18 min", book.subtitle)
        val chapters = t.children(book.id)
        assertEquals(listOf(100L, 101L, 102L), chapters.map { it.song?.id })
        assertTrue(chapters.none { it.kind == NodeKind.ACTION })
        assertEquals(BrowseQueue(listOf(dune[1], dune[0], dune[2]), 1), t.queueFor(chapters[1].id))
    }

    @Test
    fun `continuer l'ecoute - en tete de l'accueil, livre puis podcast, reprise a la position`() {
        val t = tree(listeningSource())
        val home = t.children(BrowseIds.TAB_HOME)
        assertEquals(BrowseIds.CAT_CONTINUE, home.first().id)
        assertEquals("Dune", home.first().subtitle)

        val items = t.children(BrowseIds.CAT_CONTINUE)
        assertEquals(listOf(101L, 201L), items.map { it.song?.id })
        assertEquals("Dune", items[0].title)
        assertEquals("Le Code a changé · encore 15 min", items[1].subtitle)

        val book = t.queueFor(items[0].id)!!
        assertEquals(listOf(100L, 101L, 102L), book.songs.map { it.id })
        assertEquals(1, book.startIndex)
        assertEquals(120_000L, book.startPositionMs)

        val podcast = t.queueFor(items[1].id)!!
        assertEquals(1, podcast.startIndex)
        assertEquals(300_000L, podcast.startPositionMs)
        assertEquals("Dune", t.item(items[0].id)?.title)
    }

    @Test
    fun `progression - livre entame, chapitres fini, en cours et non commence`() {
        val t = tree(listeningSource())
        val book = t.children(BrowseIds.CAT_AUDIOBOOKS).single()
        assertEquals(Completion.PARTIALLY_PLAYED, book.progress!!.completion)
        // 600 000 ms lus (chapitre 1) + 120 000 (chapitre 2) sur 1 800 000.
        assertEquals(0.4f, book.progress!!.fraction, 0.001f)
        val chapters = t.children(book.id)
        assertEquals(Progress(Completion.FULLY_PLAYED, 1f), chapters[0].progress)
        assertEquals(Completion.PARTIALLY_PLAYED, chapters[1].progress!!.completion)
        assertEquals(0.2f, chapters[1].progress!!.fraction, 0.001f)
        assertEquals(Progress(Completion.NOT_PLAYED, 0f), chapters[2].progress)
    }

    @Test
    fun `progression - episodes et livre marque comme lu`() {
        val source = listeningSource().apply {
            savedPositions = mapOf(102L to at(102, 600_000, 1), 200L to at(200, 1_200_000, 2), 201L to at(201, 300_000, 3))
        }
        val t = tree(source)
        val episodes = t.children(BrowseIds.CAT_PODCASTS)
        assertEquals(Progress(Completion.FULLY_PLAYED, 1f), episodes[0].progress)
        assertEquals(0.25f, episodes[1].progress!!.fraction, 0.001f)
        assertEquals(Progress(Completion.FULLY_PLAYED, 1f), t.children(BrowseIds.CAT_AUDIOBOOKS).single().progress)
        assertNull(t.children(BrowseIds.CAT_ALBUMS)[0].progress)
    }

    @Test
    fun `continuer l'ecoute - absent sans rien d'entame, livres finis ecartes`() {
        val source = listeningSource().apply {
            savedPositions = mapOf(102L to at(102, 600_000, lastPlayedSec = 1), 200L to at(200, 1_200_000, lastPlayedSec = 2))
        }
        val t = tree(source)
        assertTrue(t.children(BrowseIds.TAB_HOME).none { it.id == BrowseIds.CAT_CONTINUE })
        assertTrue(t.children(BrowseIds.CAT_PODCASTS).first().subtitle!!.endsWith("Lu"))
    }
}
