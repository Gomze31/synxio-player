package fr.synxio.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FlowEngineTest {

    /** Tonalités par code Camelot, pour lire les tests comme un DJ. */
    private fun key(camelot: String): Int = KeyNames.parse(camelot) ?: error("tonalité inconnue $camelot")

    private fun track(id: Long, bpm: Float = 120f, camelot: String = "8A", energy: Float = 0.5f, artist: String = "A$id") =
        FlowTrack(id, artist, bpm, key(camelot), energy)

    private fun unknown(id: Long) = FlowTrack(id, "X$id", 0f, -1, 0f)

    @Test
    fun `voisinage Camelot - 8A vers 9A vaut 1, 8A vers 8B vaut 1, 8A vers 3A vaut 3`() {
        assertEquals(1, KeyNames.distance(key("8A"), key("9A")))
        assertEquals(1, KeyNames.distance(key("8A"), key("8B")))
        assertEquals(3, KeyNames.distance(key("8A"), key("3A")))
        assertEquals(0, KeyNames.distance(key("8A"), key("8A")))
        assertEquals(2, KeyNames.distance(key("8A"), key("10A")))
    }

    @Test
    fun `tolerance d'octave - 85 et 170 BPM s'enchainent sans cout`() {
        assertEquals(0f, FlowEngine.relativeBpmDelta(85f, 170f), 1e-6f)
        assertEquals(0f, FlowEngine.relativeBpmDelta(170f, 85f), 1e-6f)
        val same = FlowEngine.cost(track(1, bpm = 85f), track(2, bpm = 170f))
        assertEquals(0f, same, 1e-6f)
        val far = FlowEngine.cost(track(1, bpm = 100f), track(2, bpm = 130f))
        assertTrue(far > 0.1f)
    }

    @Test
    fun `cout - symetrique, pondere, penalise le meme artiste`() {
        val a = track(1, bpm = 120f, camelot = "8A", energy = 0.5f)
        val b = track(2, bpm = 126f, camelot = "9A", energy = 0.7f)
        assertEquals(FlowEngine.cost(a, b), FlowEngine.cost(b, a), 1e-6f)
        // 0,5 × 6/126 + 0,35 × 1/3 + 0,15 × 0,2
        assertEquals(0.5f * 6f / 126f + 0.35f / 3f + 0.15f * 0.2f, FlowEngine.cost(a, b), 1e-5f)
        val sameArtist = b.copy(artist = a.artist)
        assertEquals(FlowEngine.cost(a, b) + 0.25f, FlowEngine.cost(a, sameArtist), 1e-6f)
    }

    @Test
    fun `cout - un titre sans donnees coute 0,6 vers n'importe quel autre`() {
        assertEquals(0.6f, FlowEngine.cost(unknown(1), track(2)), 1e-6f)
        assertEquals(0.6f, FlowEngine.cost(track(2), unknown(1)), 1e-6f)
        assertEquals(0.6f, FlowEngine.cost(unknown(1), unknown(3)), 1e-6f)
    }

    @Test
    fun `glouton - commence par le depart et prend le plus proche voisin`() {
        val tracks = listOf(
            track(1, bpm = 100f, camelot = "1A"),
            track(2, bpm = 128f, camelot = "8A"),
            track(3, bpm = 129f, camelot = "9A"),
            track(4, bpm = 60f, camelot = "5B"),
        )
        val order = FlowEngine.order(tracks, startId = 2)
        assertEquals(2L, order.first())
        assertEquals(3L, order[1])
        assertEquals(tracks.map { it.id }.toSet(), order.toSet())
    }

    @Test
    fun `glouton - sans depart connu, le premier titre ouvre`() {
        val tracks = listOf(track(5), track(6), track(7))
        assertEquals(5L, FlowEngine.order(tracks, startId = null).first())
        assertEquals(5L, FlowEngine.order(tracks, startId = 42).first())
    }

    @Test
    fun `2-opt - n'empire jamais le cout total du glouton`() {
        val random = Random(7)
        repeat(20) {
            val n = 8 + random.nextInt(40)
            val tracks = (1L..n).map {
                FlowTrack(
                    id = it,
                    artist = "Artiste ${random.nextInt(6)}",
                    bpm = 70f + random.nextInt(110),
                    keyIndex = random.nextInt(24),
                    energy = random.nextFloat(),
                )
            }
            val byId = tracks.associateBy { it.id }
            for (arc in FlowArc.entries) {
                val greedy = FlowEngine.order(tracks, startId = 1, arc, optimize = false).map { byId.getValue(it) }
                val order = FlowEngine.order(tracks, startId = 1, arc)
                val path = order.map { byId.getValue(it) }
                assertEquals(n, order.toSet().size)
                assertEquals(1L, order.first())
                assertTrue(
                    "arc $arc : ${FlowEngine.totalCost(path, arc)} > ${FlowEngine.totalCost(greedy, arc)}",
                    FlowEngine.totalCost(path, arc) <= FlowEngine.totalCost(greedy, arc) + 1e-4f,
                )
            }
        }
    }

    @Test
    fun `titres sans donnees - rejetes en fin de liste`() {
        val tracks = listOf(
            unknown(1),
            track(2, bpm = 120f, camelot = "8A"),
            unknown(3),
            track(4, bpm = 122f, camelot = "8A"),
            track(5, bpm = 124f, camelot = "9A"),
        )
        val order = FlowEngine.order(tracks, startId = 2)
        assertEquals(listOf(2L, 4L, 5L), order.take(3))
        assertEquals(setOf(1L, 3L), order.drop(3).toSet())
    }

    @Test
    fun `arc RISE - depuis le plus calme, l'energie monte`() {
        val energies = listOf(0.1f, 0.9f, 0.5f, 0.3f, 0.7f)
        val tracks = energies.mapIndexed { i, e -> track(i + 1L, energy = e) }
        val order = FlowEngine.order(tracks, startId = 1, FlowArc.RISE)
        val path = order.map { id -> tracks.first { it.id == id }.energy }
        assertEquals(path.sorted(), path)
    }

    @Test
    fun `arc WAVE - le pic d'energie est aux deux tiers`() {
        val tracks = (1L..9L).map { track(it, energy = it / 10f) }
        val order = FlowEngine.order(tracks, startId = 1, FlowArc.WAVE)
        val path = order.map { id -> tracks.first { it.id == id }.energy }
        val peak = path.indexOf(path.max())
        assertTrue("pic en $peak sur ${path.size} : $path", peak in 4..6)
        // Monte avant le pic, descend après.
        assertEquals(path.take(peak + 1).sorted(), path.take(peak + 1))
        assertEquals(path.drop(peak).sortedDescending(), path.drop(peak))
    }

    @Test
    fun `explain - trois faits lisibles`() {
        val e = FlowEngine.explain(
            track(1, bpm = 127f, camelot = "8A", energy = 0.5f),
            track(2, bpm = 129f, camelot = "9A", energy = 0.6f),
        )
        assertEquals("127 → 129 BPM", e.bpm)
        assertEquals("8A → 9A, voisines", e.key)
        assertEquals("énergie +0,1", e.energy)
        assertEquals("127 → 129 BPM · 8A → 9A, voisines · énergie +0,1", e.summary)

        val relative = FlowEngine.explain(track(1, camelot = "8A"), track(2, camelot = "8B", energy = 0.3f))
        assertEquals("8A → 8B, relatives", relative.key)
        assertEquals("énergie −0,2", relative.energy)
        assertEquals("8A, même tonalité", FlowEngine.explain(track(1), track(2)).key)
        assertEquals("même énergie", FlowEngine.explain(track(1), track(2)).energy)
    }

    @Test
    fun `explain - titre non analyse`() {
        val e = FlowEngine.explain(track(1), unknown(2))
        assertNull(e.bpm)
        assertEquals("titre non analysé", e.summary)
    }
}
