package fr.synxio.player.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatTest {

    @Test
    fun `asDuration formate minutes et heures`() {
        assertEquals("0:00", 0L.asDuration())
        assertEquals("0:00", (-5L).asDuration())
        assertEquals("3:07", 187_000L.asDuration())
        assertEquals("1:02:33", (3_600_000L + 2 * 60_000L + 33_000L).asDuration())
    }

    @Test
    fun `asLongDuration arrondit a la minute`() {
        assertEquals("0 min", 0L.asLongDuration())
        assertEquals("47 min", (47L * 60_000L).asLongDuration())
        assertEquals("2 h", (2L * 3_600_000L).asLongDuration())
        assertEquals("2 h 14 min", (2L * 3_600_000L + 14L * 60_000L).asLongDuration())
    }

    @Test
    fun `asFileSize choisit l unite`() {
        assertEquals("512 o", 512L.asFileSize())
        assertTrue(2_048L.asFileSize().endsWith("Ko"))
        assertTrue((5L * 1024 * 1024).asFileSize().endsWith("Mo"))
        assertTrue((3L * 1024 * 1024 * 1024).asFileSize().endsWith("Go"))
    }

    @Test
    fun `pluriels`() {
        assertEquals("0 titre", 0.pluralSongs())
        assertEquals("1 titre", 1.pluralSongs())
        assertEquals("12 titres", 12.pluralSongs())
        assertEquals("1 album", 1.pluralAlbums())
        assertEquals("3 albums", 3.pluralAlbums())
    }

    @Test
    fun `normalizeForSearch retire accents et casse`() {
        assertEquals("edith piaf", "Édith Piaf".normalizeForSearch())
        assertEquals("ac/dc", "AC/DC".normalizeForSearch())
        assertEquals("francoise", "Françoise".normalizeForSearch())
        assertEquals("", "".normalizeForSearch())
    }

    @Test
    fun `fuzzyScore ordonne exact, prefixe, mot, sous-chaine, sous-sequence`() {
        val exact = fuzzyScore("Nekfeu", "nekfeu")
        val prefix = fuzzyScore("Nekfeu live", "nekfeu")
        val word = fuzzyScore("Le monde de Nekfeu", "nekfeu")
        val substring = fuzzyScore("xnekfeux", "nekfeu")
        val subsequence = fuzzyScore("n-e-k-f-e-u", "nekfeu")
        val none = fuzzyScore("Daft Punk", "nekfeu")

        assertEquals(1000, exact)
        assertTrue(exact > prefix)
        assertTrue(prefix > word)
        assertTrue(word > substring)
        assertTrue(substring > subsequence)
        assertEquals(0, none)
    }

    @Test
    fun `fuzzyScore est insensible aux accents`() {
        assertEquals(1000, fuzzyScore("Édith Piaf", "edith piaf"))
        assertTrue(fuzzyScore("Édith Piaf", "edith") >= 600)
    }

    @Test
    fun `fuzzyScore vide ne matche rien`() {
        assertEquals(0, fuzzyScore("quelque chose", ""))
        assertEquals(0, fuzzyScoreNormalized("quelque chose", ""))
    }

    @Test
    fun `fuzzyScoreNormalized egale fuzzyScore pour une requete deja normalisee`() {
        val needle = "Élé".normalizeForSearch()
        assertEquals(fuzzyScore("Éléphant", "Élé"), fuzzyScoreNormalized("Éléphant", needle))
    }

    @Test
    fun `un prefixe plus court vaut plus qu un prefixe long`() {
        assertTrue(fuzzyScore("Nek", "ne") > fuzzyScore("Nekfeu feat. tout le monde", "ne"))
    }

    @Test
    fun `un mot interne commence par la requete apres un tiret ou une parenthese`() {
        assertEquals(600, fuzzyScore("Intro (Live)", "live"))
        assertEquals(600, fuzzyScore("Rock-Star", "star"))
    }
}
