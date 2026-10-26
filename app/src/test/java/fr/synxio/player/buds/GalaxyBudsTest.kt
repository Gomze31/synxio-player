package fr.synxio.player.buds

import android.bluetooth.BluetoothCodecConfig
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalaxyBudsTest {

    @Test
    fun `nom par defaut des Buds3 Pro, avec ou sans suffixe`() {
        assertEquals(BudsModel.BUDS3_PRO, GalaxyBuds.detect("Galaxy Buds3 Pro"))
        assertEquals(BudsModel.BUDS3_PRO, GalaxyBuds.detect("Galaxy Buds3 Pro (A1B2)"))
        assertEquals(BudsModel.BUDS3_PRO, GalaxyBuds.detect("Buds 3 Pro"))
    }

    @Test
    fun `nom par defaut des Buds FE`() {
        assertEquals(BudsModel.BUDS_FE, GalaxyBuds.detect("Galaxy Buds FE (4C1D)"))
        assertEquals(BudsModel.BUDS_FE, GalaxyBuds.detect("galaxy buds fe"))
    }

    @Test
    fun `autres Galaxy Buds reconnus sans modele precis`() {
        assertEquals(BudsModel.OTHER, GalaxyBuds.detect("Galaxy Buds2 Pro"))
        assertEquals(BudsModel.OTHER, GalaxyBuds.detect("Galaxy Buds3"))
        assertEquals(BudsModel.OTHER, GalaxyBuds.detect("Galaxy Buds+ (1234)"))
    }

    @Test
    fun `les ecouteurs d'autres marques ne sont pas des Galaxy Buds`() {
        assertNull(GalaxyBuds.detect("Pixel Buds Pro"))
        assertNull(GalaxyBuds.detect("Redmi Buds 5"))
        assertNull(GalaxyBuds.detect("HONOR Earbuds 3 Pro"))
        assertNull(GalaxyBuds.detect("HUAWEI FreeBuds 6i"))
        assertNull(GalaxyBuds.detect("OnePlus Buds Pro 2"))
        assertNull(GalaxyBuds.detect("Galaxy Watch6"))
        assertNull(GalaxyBuds.detect(""))
        assertNull(GalaxyBuds.detect(null))
    }

    @Test
    fun `nom du codec - le nom etendu l'emporte, sinon le type`() {
        assertEquals("SSC", GalaxyBuds.codecName(BudsCodecs.INVALID, "SSC"))
        assertEquals("AAC", GalaxyBuds.codecName(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC, null))
        assertEquals("SBC", GalaxyBuds.codecName(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, " "))
        assertEquals("LC3", GalaxyBuds.codecName(BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3, null))
        assertNull(GalaxyBuds.codecName(BudsCodecs.INVALID, null))
    }

    @Test
    fun `gestes - remappes seulement sur des Buds et hors action par defaut`() {
        val next = KeyEvent.KEYCODE_MEDIA_NEXT
        val previous = KeyEvent.KEYCODE_MEDIA_PREVIOUS
        assertEquals(
            BudsTapAction.FAVORITE,
            BudsGestures.remap(next, onBuds = true, doubleTap = BudsTapAction.FAVORITE, tripleTap = BudsTapAction.DEFAULT),
        )
        assertEquals(
            BudsTapAction.RESTART,
            BudsGestures.remap(previous, onBuds = true, doubleTap = BudsTapAction.DEFAULT, tripleTap = BudsTapAction.RESTART),
        )
        assertNull(BudsGestures.remap(previous, onBuds = true, doubleTap = BudsTapAction.FAVORITE, tripleTap = BudsTapAction.DEFAULT))
        assertNull(BudsGestures.remap(next, onBuds = false, doubleTap = BudsTapAction.FAVORITE, tripleTap = BudsTapAction.FAVORITE))
        assertNull(
            BudsGestures.remap(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, onBuds = true, doubleTap = BudsTapAction.FAVORITE, tripleTap = BudsTapAction.FAVORITE),
        )
    }

    @Test
    fun `alerte batterie - une fois par seuil franchi en descendant`() {
        assertNull(BudsAlerts.crossed(previous = null, current = 10))
        assertNull(BudsAlerts.crossed(previous = 40, current = 30))
        assertEquals(15, BudsAlerts.crossed(previous = 20, current = 15))
        assertNull(BudsAlerts.crossed(previous = 15, current = 10))
        assertEquals(5, BudsAlerts.crossed(previous = 10, current = 5))
        assertEquals(5, BudsAlerts.crossed(previous = 30, current = 4))
        assertNull(BudsAlerts.crossed(previous = 5, current = 60))
        assertNull(BudsAlerts.crossed(previous = 20, current = null))
    }

    @Test
    fun `connexion - la reprise passe avant la source`() {
        assertEquals(
            BudsConnect.Action.RESUME,
            BudsConnect.decide(resumeEnabled = true, source = BudsConnectSource.DAILY_MIX, hasQueue = true, playWhenReady = false, inCall = false),
        )
        assertEquals(
            BudsConnect.Action.START_SOURCE,
            BudsConnect.decide(resumeEnabled = true, source = BudsConnectSource.DAILY_MIX, hasQueue = false, playWhenReady = false, inCall = false),
        )
        assertEquals(
            BudsConnect.Action.START_SOURCE,
            BudsConnect.decide(resumeEnabled = false, source = BudsConnectSource.FAVORITES, hasQueue = true, playWhenReady = false, inCall = false),
        )
    }

    @Test
    fun `connexion - rien pendant un appel, une lecture, ou sans reglage`() {
        assertEquals(
            BudsConnect.Action.NOTHING,
            BudsConnect.decide(resumeEnabled = true, source = BudsConnectSource.SHUFFLE, hasQueue = true, playWhenReady = false, inCall = true),
        )
        assertEquals(
            BudsConnect.Action.NOTHING,
            BudsConnect.decide(resumeEnabled = true, source = BudsConnectSource.SHUFFLE, hasQueue = true, playWhenReady = true, inCall = false),
        )
        assertEquals(
            BudsConnect.Action.NOTHING,
            BudsConnect.decide(resumeEnabled = false, source = BudsConnectSource.NONE, hasQueue = true, playWhenReady = false, inCall = false),
        )
    }
}
