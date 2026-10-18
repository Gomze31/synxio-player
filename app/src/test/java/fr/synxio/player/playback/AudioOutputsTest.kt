package fr.synxio.player.playback

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputsTest {

    private val speaker = OutputDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "BKQ-N49")
    private val wired = OutputDevice(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "BKQ-N49")
    private val buds = OutputDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Galaxy Buds2 Pro")

    @Test
    fun `rien de branche - haut-parleur, non signale`() {
        val output = AudioOutputs.pick(routed = emptyList(), connected = listOf(speaker))
        assertEquals(AudioOutputKind.SPEAKER, output.kind)
        assertFalse(output.isExternal)
    }

    @Test
    fun `le routage annonce par le systeme l'emporte`() {
        val output = AudioOutputs.pick(routed = listOf(wired), connected = listOf(speaker, buds, wired))
        assertEquals(AudioOutput(AudioOutputKind.WIRED, "Casque filaire"), output)
    }

    @Test
    fun `routage sur le haut-parleur malgre un casque present - haut-parleur`() {
        val output = AudioOutputs.pick(routed = listOf(speaker), connected = listOf(speaker, buds))
        assertEquals(AudioOutputKind.SPEAKER, output.kind)
    }

    @Test
    fun `sans routage connu - le Bluetooth passe avant le haut-parleur, avec son nom`() {
        val output = AudioOutputs.pick(routed = emptyList(), connected = listOf(speaker, buds))
        assertEquals(AudioOutput(AudioOutputKind.BLUETOOTH, "Galaxy Buds2 Pro"), output)
        assertTrue(output.isExternal)
    }

    @Test
    fun `prise jack - le nom du telephone n'est pas affiche`() {
        assertEquals("Casque filaire", AudioOutputs.describe(wired).name)
    }

    @Test
    fun `Bluetooth sans nom - libelle generique`() {
        val output = AudioOutputs.describe(OutputDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "  "))
        assertEquals("Appareil Bluetooth", output.name)
    }

    @Test
    fun `autoradio reconnu a son nom, sur mot entier seulement`() {
        assertEquals(AudioOutputKind.CAR, AudioOutputs.describe(OutputDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Peugeot 208")).kind)
        assertEquals(AudioOutputKind.CAR, AudioOutputs.describe(OutputDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "MY CAR")).kind)
        assertFalse(AudioOutputs.looksLikeCar("Oscar's Buds"))
        assertFalse(AudioOutputs.looksLikeCar("Scarlett Solo"))
    }

    @Test
    fun `reprise au branchement - seulement demandee, en pause, avec une file, hors appel`() {
        assertTrue(AudioOutputs.shouldResumeOnConnect(enabled = true, hasQueue = true, playWhenReady = false, inCall = false))
        assertFalse(AudioOutputs.shouldResumeOnConnect(enabled = false, hasQueue = true, playWhenReady = false, inCall = false))
        assertFalse(AudioOutputs.shouldResumeOnConnect(enabled = true, hasQueue = false, playWhenReady = false, inCall = false))
        assertFalse(AudioOutputs.shouldResumeOnConnect(enabled = true, hasQueue = true, playWhenReady = true, inCall = false))
        assertFalse(AudioOutputs.shouldResumeOnConnect(enabled = true, hasQueue = true, playWhenReady = false, inCall = true))
    }

    @Test
    fun `types de casque - jack, USB, Bluetooth, mais pas le haut-parleur`() {
        assertTrue(AudioDeviceInfo.TYPE_WIRED_HEADSET in AudioOutputs.HEADSET_TYPES)
        assertTrue(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in AudioOutputs.HEADSET_TYPES)
        assertFalse(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER in AudioOutputs.HEADSET_TYPES)
    }
}
