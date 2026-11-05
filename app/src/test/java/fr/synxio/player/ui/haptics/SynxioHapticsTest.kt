package fr.synxio.player.ui.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SynxioHapticsTest {
    private class Recorder : HapticFeedback {
        val received = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { received += hapticFeedbackType }
    }

    @Test
    fun `chaque geste envoie le type attendu`() {
        val recorder = Recorder()
        val haptics = ComposeHaptics(recorder)
        haptics.tap(); haptics.confirm(); haptics.tick(); haptics.release()
        assertEquals(
            listOf(
                HapticFeedbackType.VirtualKey,
                HapticFeedbackType.Confirm,
                HapticFeedbackType.SegmentTick,
                HapticFeedbackType.GestureEnd,
            ),
            recorder.received,
        )
    }

    @Test
    fun `None est un singleton`() {
        assertSame(SynxioHaptics.None, SynxioHaptics.None)
    }
}
