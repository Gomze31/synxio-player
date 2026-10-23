package fr.synxio.player.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGrammarTest {

    private fun command(text: String) = (VoiceGrammar.parse(text) as? VoiceHeard.Command)?.command

    @Test
    fun `mot-cle suivi d'une commande l'execute directement`() {
        assertEquals(VoiceHeard.Command(VoiceCommand.Pause, afterWake = true), VoiceGrammar.parse("synchro pause"))
        assertEquals(VoiceCommand.Next, command("synchro chanson suivante"))
        assertEquals(VoiceCommand.Previous, command("synchro titre précédent"))
    }

    @Test
    fun `mot-cle seul ou suivi de bruit ouvre la fenetre d'ecoute`() {
        assertEquals(VoiceHeard.Wake, VoiceGrammar.parse("synchro"))
        assertEquals(VoiceHeard.Wake, VoiceGrammar.parse("synchro [unk]"))
        assertEquals(VoiceHeard.Wake, VoiceGrammar.parse("synchro dix-sept son"))
    }

    @Test
    fun `commande sans mot-cle est marquee comme telle`() {
        assertEquals(VoiceHeard.Command(VoiceCommand.VolumeUp, afterWake = false), VoiceGrammar.parse("plus fort"))
    }

    @Test
    fun `suite de mots hors phrase connue est ignoree`() {
        assertEquals(VoiceHeard.Nothing, VoiceGrammar.parse("dix-sept son morceau répète [unk]"))
        assertEquals(VoiceHeard.Nothing, VoiceGrammar.parse("le cinq d'une [unk]"))
        assertEquals(VoiceHeard.Nothing, VoiceGrammar.parse("[unk]"))
        assertEquals(VoiceHeard.Nothing, VoiceGrammar.parse(""))
    }

    @Test
    fun `nombres du minuteur et du volume`() {
        assertEquals(VoiceCommand.SleepTimer(20), command("arrête dans vingt minutes"))
        assertEquals(VoiceCommand.SleepTimer(45), command("minuteur quarante-cinq minutes"))
        assertEquals(VoiceCommand.SleepTimer(60), command("arrête dans une heure"))
        assertEquals(VoiceCommand.SetVolume(70), command("volume soixante-dix"))
        assertEquals(VoiceCommand.SetVolume(100), command("synchro volume cent"))
    }

    @Test
    fun `avance recule et aleatoire`() {
        assertEquals(VoiceCommand.SeekBy(30_000), command("avance de trente secondes"))
        assertEquals(VoiceCommand.SeekBy(-60_000), command("recule d'une minute"))
        assertEquals(VoiceCommand.Shuffle(true), command("active le mode aléatoire"))
        assertEquals(VoiceCommand.Shuffle(null), command("mélange"))
    }

    @Test
    fun `chaque phrase de la grammaire est comprise`() {
        val phrases = VoiceGrammar.phrases()
        assertEquals(phrases.size, phrases.toSet().size)
        phrases.filter { it != "[unk]" && it != VoiceGrammar.WAKE_WORD }.forEach { phrase ->
            assertTrue(phrase, VoiceGrammar.parse(phrase) is VoiceHeard.Command)
        }
    }
}
