package fr.synxio.player.data.repo

import fr.synxio.player.core.prefs.Settings
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.ExcludedFolderDao
import fr.synxio.player.data.db.FavoriteDao
import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.media.MediaStoreScanner
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.data.model.song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le dépôt est exercé avec un scanner et des DAO simulés : ce qui est testé, c'est la
 * construction de la bibliothèque (albums, artistes, genres, dossiers), le tri et la
 * recherche — toute la logique qui ne dépend pas d'Android.
 */
class MusicRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val stats = MutableStateFlow<List<PlayStatEntity>>(emptyList())

    private val library = listOf(
        song(1, title = "Zulu", artist = "Nekfeu", album = "Feu", albumId = 10, track = 2, dateAddedSec = 100),
        song(2, title = "Égérie", artist = "Nekfeu", album = "Feu", albumId = 10, track = 1, dateAddedSec = 200),
        song(3, title = "around the world", artist = "Daft Punk", album = "Homework", albumId = 20, genre = "Electro", dateAddedSec = 300, durationMs = 400_000L),
        song(4, title = "Bonjour", artist = "Damso/Nekfeu/Adèle Castillon", album = "Compil", albumId = 30, albumArtist = "Various Artists", dateAddedSec = 50),
        song(5, title = "Hymne", artist = "<unknown>", album = "", albumId = 40, path = "/sdcard/Downloads/Hymne.mp3", dateAddedSec = 10),
    )

    private fun repository(songs: List<Song> = library): MusicRepository {
        val scanner = mock<MediaStoreScanner> {
            on { hasAudioPermission() } doReturn false
            on { observeChanges() } doReturn emptyFlow()
            on { scan(any(), any()) } doReturn songs
        }
        val settings = mock<SettingsRepository> {
            on { this.settings } doReturn flowOf(Settings())
        }
        val favorites = mock<FavoriteDao> {
            on { observeAll() } doReturn flowOf(emptyList())
        }
        val playStats = mock<PlayStatDao> {
            on { observeAll() } doReturn stats
            on { observeMostPlayed(any()) } doReturn flowOf(emptyList())
            on { observeRecentlyPlayed(any()) } doReturn flowOf(emptyList())
        }
        val excluded = mock<ExcludedFolderDao> {
            on { observeAll() } doReturn flowOf(emptyList())
        }
        runBlocking { whenever(excluded.paths()).thenReturn(emptyList()) }
        return MusicRepository(scanner, settings, favorites, playStats, excluded, scope)
    }

    private fun loaded(songs: List<Song> = library): MusicRepository = runBlocking {
        val repo = repository(songs)
        repo.onPermissionGranted()
        withTimeout(5_000) { repo.library.first { it.hasScanned } }
        repo
    }

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun `avant permission la bibliotheque est vide et en chargement`() {
        val repo = repository()
        assertTrue(repo.library.value.isLoading)
        assertTrue(repo.library.value.isEmpty)
        assertNull(repo.songById(1))
    }

    @Test
    fun `le scan construit albums, genres et dossiers`() {
        val lib = loaded().library.value
        assertEquals(5, lib.songs.size)
        assertEquals(4, lib.albums.size)

        val feu = lib.albumsById.getValue(10)
        assertEquals("Feu", feu.title)
        assertEquals("Nekfeu", feu.artist)
        // Ordre naturel d'écoute : par numéro de piste, pas par titre.
        assertEquals(listOf("Égérie", "Zulu"), feu.songs.map { it.title })

        assertEquals(setOf("Electro", Song.UNKNOWN_GENRE), lib.genres.map { it.name }.toSet())
        // Un dossier par album (chemin par défaut de la fabrique) + le dossier Downloads.
        assertEquals(4, lib.folders.size)
        assertEquals(1_200_000L, lib.totalDurationMs)
    }

    @Test
    fun `un tag multi-artistes cree un artiste par nom`() {
        val lib = loaded().library.value
        val names = lib.artists.map { it.name }.toSet()
        // L'album « Compil » est indexé sous son artiste d'album, pas sous « Damso/Nekfeu/... ».
        assertTrue("Various Artists" in names)
        assertTrue(names.none { '/' in it })
        assertTrue("Nekfeu" in names)
        assertTrue(Song.UNKNOWN_ARTIST in names)

        val nekfeu = lib.artists.first { it.name == "Nekfeu" }
        assertEquals(listOf("Feu"), nekfeu.albums.map { it.title })
    }

    @Test
    fun `resolution par identifiant et par nom`() {
        val repo = loaded()
        assertEquals("Zulu", repo.songById(1)?.title)
        assertEquals(listOf(3L, 1L), repo.songsByIds(listOf(3, 99, 1)).map { it.id })
        assertNotNull(repo.albumById(20))
        assertNull(repo.albumById(999))
        assertEquals("Daft Punk", repo.artistByName("daft punk")?.name)
        assertEquals("Electro", repo.genreByName("ELECTRO")?.name)
        assertNotNull(repo.folderByPath("/sdcard/Downloads"))
    }

    @Test
    fun `tri des titres insensible a la casse et aux accents`() {
        val repo = loaded()
        val sorted = repo.sortSongs(library, SongSort.TITLE, descending = false).map { it.title }
        assertEquals(listOf("around the world", "Bonjour", "Égérie", "Hymne", "Zulu"), sorted)

        val reversed = repo.sortSongs(library, SongSort.TITLE, descending = true).map { it.title }
        assertEquals(sorted.reversed(), reversed)
    }

    @Test
    fun `tri par artiste puis titre`() {
        val repo = loaded()
        val sorted = repo.sortSongs(library, SongSort.ARTIST, descending = false)
        assertEquals(listOf(Song.UNKNOWN_ARTIST, "Daft Punk", "Damso/Nekfeu/Adèle Castillon", "Nekfeu", "Nekfeu"),
            sorted.map { it.displayArtist })
        assertEquals(listOf("Égérie", "Zulu"), sorted.takeLast(2).map { it.title })
    }

    @Test
    fun `tri par nombre d ecoutes`() {
        val repo = loaded()
        stats.value = listOf(
            PlayStatEntity(3, "", playCount = 5, skipCount = 0, lastPlayedAt = 1, totalListenedMs = 1),
            PlayStatEntity(5, "", playCount = 2, skipCount = 0, lastPlayedAt = 1, totalListenedMs = 1),
        )
        val sorted = repo.sortSongs(library, SongSort.PLAY_COUNT, descending = true)
        assertEquals(listOf(3L, 5L), sorted.take(2).map { it.id })
    }

    @Test
    fun `tri des albums et des artistes`() {
        val repo = loaded()
        val lib = repo.library.value
        assertEquals(
            listOf("Album inconnu", "Compil", "Feu", "Homework"),
            repo.sortAlbums(lib.albums, AlbumSort.TITLE, descending = false).map { it.title },
        )
        assertEquals(
            listOf("Homework", "Feu", "Compil", "Album inconnu"),
            repo.sortAlbums(lib.albums, AlbumSort.DATE_ADDED, descending = true).map { it.title },
        )
        val byCount = repo.sortArtists(lib.artists, ArtistSort.SONG_COUNT, descending = true)
        assertEquals("Nekfeu", byCount.first().name)
    }

    @Test
    fun `recherche floue par titre, artiste et album`() {
        val repo = loaded()
        val byTitle = repo.search("zulu")
        assertEquals(listOf(1L), byTitle.songs.map { it.id })
        assertEquals("zulu", byTitle.query)

        val byArtist = repo.search("daft")
        assertEquals("Daft Punk", byArtist.artists.single().name)
        // Le titre de Daft Punk vient en tête ; une correspondance lointaine par
        // sous-séquence (« d·a·f·t » dans « Damso/Nekfeu/… ») peut suivre, avec un score bas.
        assertEquals(3L, byArtist.songs.first().id)

        val accentInsensitive = repo.search("egerie")
        assertEquals(listOf(2L), accentInsensitive.songs.map { it.id })

        val byAlbum = repo.search("homework")
        assertEquals("Homework", byAlbum.albums.single().title)
    }

    @Test
    fun `recherche vide ou sans resultat`() {
        val repo = loaded()
        assertTrue(repo.search("   ").isEmpty)
        assertTrue(repo.search("xyzzy").isEmpty)
    }

    @Test
    fun `nouveautes triees par date d ajout`() = runBlocking {
        val repo = loaded()
        val recent = withTimeout(5_000) { repo.recentlyAdded.first { it.isNotEmpty() } }
        assertEquals(listOf(3L, 2L, 1L, 4L, 5L), recent.map { it.id })
    }

    @Test
    fun `aleatoire sans les titres zappes garde une file suffisante`() {
        val repo = loaded()
        stats.value = listOf(
            PlayStatEntity(1, "", playCount = 0, skipCount = 3, lastPlayedAt = 0, totalListenedMs = 0),
        )
        // Cinq titres seulement : filtrer tomberait sous le minimum, on garde tout.
        assertEquals(5, repo.withoutDisliked(library).size)

        val big = (1L..40L).map { song(it) }
        val filtered = repo.withoutDisliked(big)
        assertEquals(39, filtered.size)
        assertTrue(filtered.none { it.id == 1L })
    }
}
