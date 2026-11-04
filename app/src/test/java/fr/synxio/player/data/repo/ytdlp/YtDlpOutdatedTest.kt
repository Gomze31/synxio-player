package fr.synxio.player.data.repo.ytdlp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpOutdatedTest {

    @Test
    fun `motifs connus`() {
        assertTrue(YtDlpOutdated.looksOutdated("ERROR: [youtube] abc: Unable to extract player version"))
        assertTrue(YtDlpOutdated.looksOutdated("ERROR: Sign in to confirm you're not a bot"))
        assertTrue(YtDlpOutdated.looksOutdated("HTTP Error 403: Forbidden"))
        assertTrue(YtDlpOutdated.looksOutdated("ERROR: Requested format is not available"))
    }

    @Test
    fun `autres erreurs`() {
        assertFalse(YtDlpOutdated.looksOutdated("ERROR: Video unavailable"))
        assertFalse(YtDlpOutdated.looksOutdated(""))
    }
}
