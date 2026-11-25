package fr.synxio.player.playback.engine

import fr.synxio.player.playback.engine.DualDeckScheduler.Decision
import fr.synxio.player.playback.engine.DualDeckScheduler.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualDeckSchedulerTest {

    private val fade6 = DualDeckScheduler.Config(fadeMs = 6_000, energyAligned = false)
    private val duration = 180_000L

    /** Horloge simulée : avance la position de [stepMs] et renvoie la décision. */
    private class Clock(
        private val scheduler: DualDeckScheduler,
        private val config: DualDeckScheduler.Config,
        private val durationMs: Long,
        private val current: DualDeckScheduler.Traits? = null,
        private val next: DualDeckScheduler.Traits? = null,
        var nextIndex: Int? = 1,
    ) {
        var positionMs = 0L
        fun at(positionMs: Long): Decision {
            this.positionMs = positionMs
            return scheduler.tick(positionMs, durationMs, nextIndex, config, current, next)
        }
        fun step(stepMs: Long) = at(positionMs + stepMs)
    }

    @Test
    fun `preparation trois secondes avant le fondu puis chevauchement a T moins fade`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        assertEquals(Decision.Idle, clock.at(0))
        assertEquals(Decision.Idle, clock.at(170_999))
        assertEquals(Decision.Prepare(nextIndex = 1, startMs = 0), clock.at(171_000))
        assertEquals(Phase.PREPARED, s.phase)
        assertEquals(Decision.Idle, clock.at(173_999))
        val first = clock.at(174_000)
        assertTrue(first is Decision.Overlap)
        assertEquals(0f, (first as Decision.Overlap).progress, 1e-6f)
        assertEquals(Phase.OVERLAP, s.phase)
    }

    @Test
    fun `progression du chevauchement puis bascule`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        clock.at(174_000)
        assertEquals(0.5f, (clock.at(177_000) as Decision.Overlap).progress, 1e-6f)
        assertEquals(0.99f, (clock.at(179_940) as Decision.Overlap).progress, 1e-3f)
        assertEquals(Decision.Swap, clock.at(180_000))
        assertEquals(Phase.IDLE, s.phase)
        assertNull(s.preparedIndex)
    }

    @Test
    fun `fondu desactive rien ne se passe`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, DualDeckScheduler.Config(0, false), duration)
        assertEquals(Decision.Idle, clock.at(175_000))
        assertEquals(Phase.IDLE, s.phase)
    }

    @Test
    fun `sans titre suivant rien ne se passe`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration, nextIndex = null)
        assertEquals(Decision.Idle, clock.at(175_000))
        assertEquals(Phase.IDLE, s.phase)
    }

    @Test
    fun `titre trop court pour deux fondus ou duree inconnue`() {
        val s = DualDeckScheduler()
        assertNull(s.plan(11_000, fade6, null))
        assertNull(s.plan(0, fade6, null))
        assertNull(s.plan(-1, fade6, null))
        assertEquals(Decision.Idle, Clock(s, fade6, 11_000).at(6_000))
        val plan = s.plan(12_000, fade6, null)!!
        assertEquals(6_000, plan.overlapStartMs)
        assertEquals(3_000, plan.prepareAtMs)
    }

    @Test
    fun `seek juste avant la fin ne declenche pas de fondu`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        assertEquals(Decision.Idle, clock.at(176_000))
        assertEquals(Phase.IDLE, s.phase)
    }

    @Test
    fun `caler sur l energie, fin a outroMs et depart a introMs`() {
        val s = DualDeckScheduler()
        val config = DualDeckScheduler.Config(6_000, energyAligned = true)
        val current = DualDeckScheduler.Traits(introMs = 1_000, outroMs = 170_000)
        val next = DualDeckScheduler.Traits(introMs = 2_500, outroMs = null)
        val clock = Clock(s, config, duration, current, next)
        assertEquals(Decision.Idle, clock.at(160_999))
        assertEquals(Decision.Prepare(1, 2_500), clock.at(161_000))
        assertEquals(Decision.Idle, clock.at(163_999))
        assertEquals(0f, (clock.at(164_000) as Decision.Overlap).progress, 1e-6f)
        assertEquals(Decision.Swap, clock.at(170_000))
    }

    @Test
    fun `caler sur l energie inactif ignore les traits`() {
        val s = DualDeckScheduler()
        val current = DualDeckScheduler.Traits(introMs = 1_000, outroMs = 170_000)
        val next = DualDeckScheduler.Traits(introMs = 2_500, outroMs = null)
        val clock = Clock(s, fade6, duration, current, next)
        assertEquals(Decision.Idle, clock.at(165_000))
        assertEquals(Decision.Prepare(1, 0), clock.at(171_000))
    }

    @Test
    fun `outro trop tot ou trop tard est ignore`() {
        val s = DualDeckScheduler()
        val config = DualDeckScheduler.Config(6_000, energyAligned = true)
        // Outro plus court que le fondu : on garde la durée.
        assertEquals(174_000, s.plan(duration, config, DualDeckScheduler.Traits(null, 5_000))!!.overlapStartMs)
        // Outro au-delà de la durée : on garde la durée.
        assertEquals(174_000, s.plan(duration, config, DualDeckScheduler.Traits(null, 200_000))!!.overlapStartMs)
    }

    @Test
    fun `seek pendant le chevauchement abandonne`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        clock.at(175_000)
        assertEquals(Decision.Abort, s.seek())
        assertEquals(Phase.IDLE, s.phase)
        assertNull(s.preparedIndex)
    }

    @Test
    fun `pause pendant le chevauchement abandonne, pendant la preparation non`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        assertEquals(Decision.Idle, s.pause())
        assertEquals(Phase.IDLE, s.phase)
        clock.at(172_000)
        clock.at(175_000)
        assertEquals(Decision.Abort, s.pause())
    }

    @Test
    fun `file changee pendant le chevauchement abandonne`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        clock.at(175_000)
        assertEquals(Decision.Abort, s.queueChanged())
        // Puis le cycle repart : encore dans la fenêtre de fondu, trop tard pour re-préparer.
        assertEquals(Decision.Idle, clock.at(175_500))
    }

    @Test
    fun `interruption au repos reste au repos`() {
        val s = DualDeckScheduler()
        assertEquals(Decision.Idle, s.seek())
        assertEquals(Decision.Idle, s.pause())
        assertEquals(Decision.Idle, s.queueChanged())
        assertEquals(Decision.Idle, s.reserveFailed())
        assertEquals(Decision.Idle, s.transition())
    }

    @Test
    fun `suivant change pendant la preparation, nouvelle preparation`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        assertEquals(Decision.Prepare(1, 0), clock.at(171_000))
        clock.nextIndex = 7
        assertEquals(Decision.Prepare(7, 0), clock.at(172_000))
        assertEquals(7, s.preparedIndex)
    }

    @Test
    fun `suivant disparu pendant la preparation, repos`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        clock.nextIndex = null
        assertEquals(Decision.Idle, clock.at(172_000))
        assertEquals(Phase.IDLE, s.phase)
    }

    @Test
    fun `suivant disparu pendant le chevauchement, abandon`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        clock.at(175_000)
        clock.nextIndex = null
        assertEquals(Decision.Abort, clock.at(175_100))
    }

    @Test
    fun `reserve en echec pendant la preparation ou le chevauchement abandonne`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        assertEquals(Decision.Abort, s.reserveFailed())
        clock.at(171_500)
        clock.at(175_000)
        assertEquals(Decision.Abort, s.reserveFailed())
    }

    @Test
    fun `transition remet a zero et le cycle repart sur le titre suivant`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        clock.at(171_000)
        clock.at(175_000)
        assertEquals(Decision.Idle, s.transition())
        assertEquals(Phase.IDLE, s.phase)
        assertEquals(Decision.Idle, clock.at(1_000))
        assertEquals(Decision.Prepare(1, 0), clock.at(171_000))
    }

    @Test
    fun `delai du prochain tick selon la phase`() {
        val s = DualDeckScheduler()
        val clock = Clock(s, fade6, duration)
        // Au repos : jusqu'à la préparation, borné à 2 s.
        assertEquals(2_000, s.nextTickDelayMs(0, duration, fade6, null))
        assertEquals(1_000, s.nextTickDelayMs(170_000, duration, fade6, null))
        assertEquals(250, s.nextTickDelayMs(170_900, duration, fade6, null))
        // Fondu désactivé : on dort longuement.
        assertEquals(2_000, s.nextTickDelayMs(0, duration, DualDeckScheduler.Config(0, false), null))
        clock.at(171_000)
        assertEquals(250, s.nextTickDelayMs(171_000, duration, fade6, null))
        assertEquals(100, s.nextTickDelayMs(173_900, duration, fade6, null))
        clock.at(174_000)
        assertEquals(50, s.nextTickDelayMs(174_000, duration, fade6, null))
    }

    @Test
    fun `preparation personnalisee`() {
        val s = DualDeckScheduler(prepareLeadMs = 5_000)
        val clock = Clock(s, fade6, duration)
        assertEquals(Decision.Idle, clock.at(168_999))
        assertEquals(Decision.Prepare(1, 0), clock.at(169_000))
    }
}
