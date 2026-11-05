package fr.synxio.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoKeysTest {

    @Test
    fun `cles relevees dans androidx media utils MediaConstants 1_7_0`() {
        assertEquals("androidx.media.utils.MediaBrowserCompat.extras.CUSTOM_BROWSER_ACTION_LIMIT", AutoKeys.ROOT_HINT_ACTION_LIMIT)
        assertEquals("androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ROOT_LIST", AutoKeys.ROOT_EXTRAS_ACTION_LIST)
        assertEquals("androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ID_LIST", AutoKeys.ITEM_ACTION_ID_LIST)
        assertEquals("androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ID", AutoKeys.ACTION_ID)
        assertEquals("androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_LABEL", AutoKeys.ACTION_LABEL)
        assertEquals("androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ICON_URI", AutoKeys.ACTION_ICON_URI)
        assertEquals("androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_EXTRAS", AutoKeys.ACTION_EXTRAS)
        assertEquals("androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID", AutoKeys.ACTION_MEDIA_ITEM_ID)
        assertEquals("androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE", AutoKeys.RESULT_MESSAGE)
        assertEquals("androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM", AutoKeys.RESULT_REFRESH_ITEM)
    }

    @Test
    fun `cinq actions, identifiants stables, le favori avant son retrait`() {
        assertEquals(listOf("favorite", "unfavorite", "play_next", "add_to_queue", "shuffle"), AutoKeys.ALL_ACTIONS)
    }
}
