package fr.synxio.player.core.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarRulesTest {
    @Test
    fun `classe audio de voiture - reconnue sans liste`() {
        assertTrue(CarRules.isCar(CarRules.AUDIO_VIDEO_CAR_AUDIO, "00:11:22:33:44:55", emptySet()))
    }

    @Test
    fun `appareil choisi - reconnu quelle que soit sa classe`() {
        assertTrue(CarRules.isCar(0x0404, "AA:BB:CC:DD:EE:FF", setOf("AA:BB:CC:DD:EE:FF")))
    }

    @Test
    fun `casque inconnu - pas une voiture`() {
        assertFalse(CarRules.isCar(0x0404, "AA:BB:CC:DD:EE:FF", setOf("00:11:22:33:44:55")))
        assertFalse(CarRules.isCar(0, "", emptySet()))
    }
}
