package fr.synxio.player.ui.screens.settings

import fr.synxio.player.ui.screens.SettingsSubMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsCatalogTest {

    @Test
    fun `entrees uniques et chaque sous-menu couvert`() {
        val keys = SettingsCatalog.entries.map { Triple(it.menu, it.group, it.title) }
        assertEquals(keys.size, keys.toSet().size)
        SettingsSubMenu.entries.forEach { menu ->
            assertTrue("sous-menu sans entrée : $menu", SettingsCatalog.entries.any { it.menu == menu })
            assertTrue("sous-menu sans groupe : $menu", !SettingsCatalog.groups[menu].isNullOrEmpty())
        }
        SettingsCatalog.entries.forEach { entry ->
            assertTrue("groupe inconnu ${entry.group} pour ${entry.title}", SettingsCatalog.groups.getValue(entry.menu).any { it.key == entry.group })
        }
    }

    @Test
    fun `synchronisation avec le source de l'ecran`() {
        val source = File(SOURCE).readLines()
        val menuRegex = Regex("""SettingsSubMenu\.([A-Z_]+) -> \{""")
        val groupRegex = Regex("""group\(\d+, "([^"]+)", "([^"]+)"\)""")
        val titleRegex = Regex("""^\s+title = "([^"$]+)"""")
        var menu: SettingsSubMenu? = null
        var group: String? = null
        val found = mutableListOf<Triple<SettingsSubMenu, String, String>>()
        for (line in source) {
            if (line.contains("// HUB")) break
            menuRegex.find(line)?.let { menu = SettingsSubMenu.valueOf(it.groupValues[1]); group = null }
            groupRegex.find(line)?.let { m ->
                group = m.groupValues[1]
                val current = menu ?: return@let
                assertEquals("surtitre du groupe ${m.groupValues[1]}", m.groupValues[2], SettingsCatalog.groupTitle(current, m.groupValues[1]))
            }
            titleRegex.find(line)?.let { m ->
                val currentMenu = menu ?: return@let
                val currentGroup = group ?: "updates"
                found += Triple(currentMenu, currentGroup, m.groupValues[1])
            }
        }
        assertTrue("aucun titre lu dans le source", found.size > 40)
        val catalogued = SettingsCatalog.entries.map { Triple(it.menu, it.group, it.title) }.toSet()
        val missing = found.filter { it !in catalogued }
        assertTrue("réglages absents du catalogue : $missing", missing.isEmpty())

        val text = source.joinToString("\n")
        val phantom = SettingsCatalog.entries.filter { it.inSource && !text.contains("\"${it.title}\"") }
        assertTrue("entrées du catalogue sans titre dans l'écran : ${phantom.map { it.title }}", phantom.isEmpty())
    }

    @Test
    fun `recherche - titre, mot-cle, accents, rien`() {
        val entries = SettingsCatalog.entries
        assertEquals("Fondu entre les morceaux", SettingsSearch.search("fondu", entries).first().title)
        assertEquals("Fondu entre les morceaux", SettingsSearch.search("crossfade", entries).first().title)
        assertTrue(SettingsSearch.search("egaliseur", entries).any { it.title == "Ouvrir l'égaliseur complet" })
        assertTrue(SettingsSearch.search("casque bouton", entries).first().title == "Gestes du bouton de casque")
        assertTrue(SettingsSearch.search("zzz", entries).isEmpty())
        assertTrue(SettingsSearch.search("a", entries).isEmpty())
    }

    private companion object {
        const val SOURCE = "src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt"
    }
}
