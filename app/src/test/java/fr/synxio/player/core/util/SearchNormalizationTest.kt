package fr.synxio.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizationTest {

    @Test
    fun `les accents et la casse sont retires`() {
        assertEquals("edith piaf", "Édith Piaf".normalizeForSearch())
        assertEquals("francoise hardy", "FRANÇOISE HARDY".normalizeForSearch())
        assertEquals("noel", "Noël".normalizeForSearch())
    }

    @Test
    fun `les ligatures sont depliees`() {
        assertEquals("coeur", "Cœur".normalizeForSearch())
        assertEquals("oeuvre", "ŒUVRE".normalizeForSearch())
        assertEquals("aegis", "Ægis".normalizeForSearch())
        assertEquals("strasse", "Straße".normalizeForSearch())
    }

    @Test
    fun `l ascii pur est simplement mis en minuscules`() {
        assertEquals("daft punk - one more time", "Daft Punk - One More Time".normalizeForSearch())
        assertEquals("", "".normalizeForSearch())
    }

    @Test
    fun `fuzzyScore ignore accents casse et ligatures`() {
        assertEquals(1000, fuzzyScore("Édith Piaf", "edith piaf"))
        assertEquals(1000, fuzzyScore("Cœur de pirate", "COEUR DE PIRATE"))
        assertTrue(fuzzyScore("Mon cœur", "coeur") > 0)
    }

    @Test
    fun `bareme du score flou`() {
        assertEquals(1000, fuzzyScore("Zazie", "zazie"))
        // Préfixe : 800 moins l'écart de longueur.
        assertEquals(800 - 4, fuzzyScore("Zazie", "z"))
        // Début d'un mot interne, séparateurs compris.
        assertEquals(600, fuzzyScore("La vie en rose", "rose"))
        assertEquals(600, fuzzyScore("Intro (Live)", "live"))
        assertEquals(600, fuzzyScore("hip-hop", "hop"))
        // Sous-chaîne quelconque.
        assertEquals(400, fuzzyScore("Macarena", "care"))
        // Lettres dans l'ordre.
        assertEquals(150, fuzzyScore("Macarena", "mcrn"))
        assertEquals(0, fuzzyScore("Macarena", "xyz"))
        assertEquals(0, fuzzyScore("Macarena", ""))
    }

    @Test
    fun `une aiguille avec separateur ne compte pas comme debut de mot`() {
        // Comme l'ancien découpage par split : la sous-chaîne reste trouvée, à 400.
        assertEquals(400, fuzzyScore("la vie en rose", "vie en"))
    }

    @Test
    fun `la variante normalisee donne le meme score`() {
        val pairs = listOf(
            "Édith Piaf" to "piaf",
            "Cœur de pirate" to "Coeur",
            "Straße" to "strasse",
            "Intro (Live)" to "LIVE",
            "Macarena" to "mcrn",
        )
        for ((haystack, needle) in pairs) {
            assertEquals(
                fuzzyScore(haystack, needle),
                fuzzyScoreNormalized(haystack.normalizeForSearch(), needle.normalizeForSearch()),
            )
        }
    }
}
