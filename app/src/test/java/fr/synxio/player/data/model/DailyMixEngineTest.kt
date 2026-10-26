package fr.synxio.player.data.model

import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMixEngineTest {

    private val now = 1_800_000_000_000L
    private val day = now / DailyMixEngine.DAY_MS

    /** 60 titres, 12 artistes, 30 albums. */
    private val library = (1L..60L).map { id ->
        song(
            id = id,
            title = "T$id",
            artist = "Artiste ${id % 12}",
            genre = if (id % 2 == 0L) "Pop" else "Rap",
        ).copy(albumId = id % 30)
    }

    private fun stat(id: Long, plays: Int, skips: Int = 0, lastPlayedAt: Long = now) =
        id to PlayStatEntity(id, "/music/$id.mp3", plays, skips, lastPlayedAt, plays * 180_000L)

    /** Les 20 premiers titres écoutés, les plus petits identifiants le plus souvent. */
    private val stats = (1L..20L).associate { stat(it, plays = 25 - it.toInt()) }

    private fun build(
        songs: List<Song> = library,
        stats: Map<Long, PlayStatEntity> = this.stats,
        favorites: Set<Long> = setOf(1, 2, 3),
        day: Long = this.day,
        similarSeed: Song? = null,
        similar: List<Song> = emptyList(),
    ) = DailyMixEngine.build(songs, stats, favorites, day, similarSeed, similar)

    @Test
    fun `les mixs sont stables dans la journee et changent le lendemain`() {
        val today = build().first { it.kind == DailyMixKind.DAILY }.songs
        val again = build().first { it.kind == DailyMixKind.DAILY }.songs
        val tomorrow = build(day = day + 1).first { it.kind == DailyMixKind.DAILY }.songs
        assertEquals(today, again)
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun `aucun mix ne depasse 25 titres ni ne repete un titre`() {
        build().forEach { mix ->
            assertTrue(mix.title, mix.songs.size <= DailyMixEngine.MIX_SIZE)
            assertEquals(mix.title, mix.songs.size, mix.songs.distinctBy { it.id }.size)
        }
    }

    @Test
    fun `au plus deux titres par artiste et jamais deux d'affilee`() {
        val mix = build().first { it.kind == DailyMixKind.DAILY }
        val perArtist = mix.songs.groupingBy { it.displayArtist }.eachCount()
        assertTrue(perArtist.values.all { it <= 2 })
        mix.songs.zipWithNext().forEach { (a, b) -> assertNotEquals(a.displayArtist, b.displayArtist) }
    }

    @Test
    fun `le mix du jour melange familiers et nouveautes`() {
        val mix = build().first { it.kind == DailyMixKind.DAILY }
        val familiar = mix.songs.count { (stats[it.id]?.playCount ?: 0) >= 2 }
        val fresh = mix.songs.size - familiar
        assertTrue("familiers : $familiar", familiar >= 10)
        assertTrue("nouveautés : $fresh", fresh >= 5)
    }

    @Test
    fun `la decouverte ne garde que des titres peu ecoutes, un par album`() {
        val mix = build().first { it.kind == DailyMixKind.DISCOVERY }
        assertTrue(mix.songs.all { (stats[it.id]?.playCount ?: 0) <= 1 })
        assertEquals(mix.songs.size, mix.songs.distinctBy { it.albumId }.size)
    }

    @Test
    fun `les titres souvent zappes sont ecartes`() {
        val zapped = stats + stat(40, plays = 0, skips = 5)
        assertTrue(build(stats = zapped).flatMap { it.songs }.none { it.id == 40L })
    }

    @Test
    fun `un mix par artiste parmi les trois plus ecoutes, sans artiste inconnu`() {
        val unknown = library.map { if (it.id <= 6) it.copy(artist = "") else it }
        val mixes = build(songs = unknown).filter { it.kind == DailyMixKind.ARTIST }
        assertTrue(mixes.size in 1..3)
        assertTrue(mixes.none { it.title.contains(Song.UNKNOWN_ARTIST) })
        mixes.forEach { mix ->
            val artist = mix.title.removePrefix("Mix ")
            assertTrue(mix.songs.any { it.displayArtist == artist })
        }
    }

    @Test
    fun `un mix d'artiste regroupe les featurings sous l'artiste principal`() {
        val feats = library.map { if (it.id in 1L..4L) it.copy(artist = "Star ft. Invité ${it.id}") else it }
        val heavy = (1L..4L).associate { stat(it, plays = 50) } + stats
        val mixes = build(songs = feats, stats = heavy).filter { it.kind == DailyMixKind.ARTIST }
        assertEquals("Mix Star", mixes.first().title)
        assertTrue(mixes.none { it.title.contains(" ft. ") })
    }

    @Test
    fun `le mix dans la lignee n'existe qu'avec une graine et des voisins`() {
        assertNull(build().firstOrNull { it.kind == DailyMixKind.SIMILAR })
        val seed = library[0]
        val mix = build(similarSeed = seed, similar = library.drop(30).take(20))
            .first { it.kind == DailyMixKind.SIMILAR }
        assertEquals(seed, mix.songs.first())
        assertTrue(mix.title.contains(seed.title))
    }

    @Test
    fun `une petite bibliotheque ne produit pas de mix trop court`() {
        val tiny = library.take(5)
        assertTrue(build(songs = tiny, stats = emptyMap()).isEmpty())
    }
}
