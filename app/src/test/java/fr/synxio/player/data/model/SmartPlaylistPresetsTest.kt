package fr.synxio.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistPresetsTest {

    @Test
    fun `il y a exactement six presets`() {
        assertEquals(6, SmartPlaylistPresets.all.size)
    }

    @Test
    fun `chaque preset a un nom non vide et au moins une regle`() {
        SmartPlaylistPresets.all.forEach { preset ->
            assertTrue("nom vide pour ${preset.key}", preset.name.isNotBlank())
            assertTrue("aucune règle pour ${preset.key}", preset.rules.rules.isNotEmpty())
        }
    }

    @Test
    fun `les cles sont uniques`() {
        val keys = SmartPlaylistPresets.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `byKey retrouve chaque preset par sa cle`() {
        SmartPlaylistPresets.all.forEach { preset ->
            assertEquals(preset, SmartPlaylistPresets.byKey(preset.key))
        }
    }

    @Test
    fun `byKey renvoie null pour une cle inconnue`() {
        assertEquals(null, SmartPlaylistPresets.byKey("inexistant"))
    }

    @Test
    fun `les regles de chaque preset survivent a un aller-retour JSON`() {
        SmartPlaylistPresets.all.forEach { preset ->
            val roundTripped = SmartPlaylistRules.fromJson(preset.rules.toJson())
            assertEquals("aller-retour JSON incohérent pour ${preset.key}", preset.rules, roundTripped)
        }
    }
}
