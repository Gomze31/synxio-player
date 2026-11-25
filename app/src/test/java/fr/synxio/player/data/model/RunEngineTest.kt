package fr.synxio.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RunEngineTest {

    private fun track(id: Long, bpm: Float, energy: Float = 0.8f) = FlowTrack(id, "A$id", bpm, 0, energy)

    private val random = Random(7)

    // --- retarget ---------------------------------------------------------------------

    @Test
    fun `retarget - sans cible, la premiere cadence stable devient la cible arrondie`() {
        val t = RunEngine.retarget(RunTarget.NONE, 169.6f, stable = true)
        assertEquals(170f, t.bpm, 0f)
        assertTrue(!t.locked)
    }

    @Test
    fun `retarget - cadence instable ou nulle ignoree`() {
        val current = RunTarget(170f, locked = false)
        assertEquals(current, RunEngine.retarget(current, 190f, stable = false))
        assertEquals(current, RunEngine.retarget(current, null, stable = true))
        assertEquals(current, RunEngine.retarget(current, 0f, stable = true))
    }

    @Test
    fun `retarget - hysteresis 4 pour cent - 3 pour cent ignore, 5 pour cent suivi`() {
        val current = RunTarget(170f, locked = false)
        assertEquals(current, RunEngine.retarget(current, 175f, stable = true))   // +2,9 %
        assertEquals(179f, RunEngine.retarget(current, 179f, stable = true).bpm, 0f) // +5,3 %
    }

    @Test
    fun `retarget - verrouille, la cible ne bouge pas`() {
        val locked = RunTarget(160f, locked = true)
        assertEquals(locked, RunEngine.retarget(locked, 185f, stable = true))
    }

    // --- pick -------------------------------------------------------------------------

    @Test
    fun `pick exact - BPM a 3 pour cent et energie suffisante - vitesse 1`() {
        val pick = RunEngine.pick(170f, listOf(track(1, 128f), track(2, 172f), track(3, 200f)), emptySet(), random)
        assertNotNull(pick)
        assertEquals(2L, pick!!.id)
        assertEquals(1f, pick.speed, 0f)
    }

    @Test
    fun `pick etire - a 8 pour cent, vitesse cible sur bpm bornee a 5 pour cent`() {
        // 160 pour une cible de 170 : +6,25 % → vitesse 1,0625 bornée à 1,05.
        val pick = RunEngine.pick(170f, listOf(track(1, 160f), track(2, 100f)), emptySet(), random)
        assertNotNull(pick)
        assertEquals(1L, pick!!.id)
        assertEquals(1.05f, pick.speed, 1e-4f)
        // 163 pour 170 : +4,1 % → hors palier exact, vitesse 1,043 non bornée.
        val soft = RunEngine.pick(170f, listOf(track(3, 163f)), emptySet(), random)
        assertEquals(170f / 163f, soft!!.speed, 1e-4f)
    }

    @Test
    fun `pick - octave - 85 BPM convient pour 170`() {
        val pick = RunEngine.pick(170f, listOf(track(1, 85f), track(2, 130f)), emptySet(), random)
        assertEquals(1L, pick!!.id)
        assertEquals(1f, pick.speed, 0f)
        assertEquals(170f, RunEngine.effectiveBpm(170f, 85f), 0f)
        assertEquals(170f, RunEngine.effectiveBpm(170f, 340f), 0f)
    }

    @Test
    fun `pick - energie faible ecartee du palier exact mais acceptee en etire`() {
        val calm = track(1, 170f, energy = 0.2f)
        val pick = RunEngine.pick(170f, listOf(calm), emptySet(), random)
        assertEquals(1L, pick!!.id)
        assertEquals(1f, pick.speed, 1e-4f) // vitesse cible / bpm = 1,0 mais via le palier étiré
        val energetic = track(2, 172f, energy = 0.9f)
        assertEquals(2L, RunEngine.pick(170f, listOf(calm, energetic), emptySet(), random)!!.id)
    }

    @Test
    fun `pick - aucun candidat au tempo - null`() {
        assertNull(RunEngine.pick(170f, listOf(track(1, 120f), track(2, 0f)), emptySet(), random))
        assertNull(RunEngine.pick(0f, listOf(track(1, 170f)), emptySet(), random))
        assertNull(RunEngine.pick(170f, emptyList(), emptySet(), random))
    }

    @Test
    fun `pick - les titres recents sont evites puis repeches si rien d'autre`() {
        val a = track(1, 170f); val b = track(2, 171f)
        val pick = RunEngine.pick(170f, listOf(a, b), setOf(1L), random)
        assertEquals(2L, pick!!.id)
        val again = RunEngine.pick(170f, listOf(a, b), setOf(1L, 2L), random)
        assertNotNull(again)
    }
}
