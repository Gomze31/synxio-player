package fr.synxio.player.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFlowCardsTest {

    @Test
    fun `carte course - capteur present et course deja lancee`() {
        assertTrue(showRunCard(hasStepDetector = true, runDone = true))
    }

    @Test
    fun `carte course - absente sans capteur ou sans course`() {
        assertFalse(showRunCard(hasStepDetector = false, runDone = true))
        assertFalse(showRunCard(hasStepDetector = true, runDone = false))
        assertFalse(showRunCard(hasStepDetector = false, runDone = false))
    }
}
