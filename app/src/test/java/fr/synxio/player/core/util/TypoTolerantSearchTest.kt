package fr.synxio.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypoTolerantSearchTest {

    @Test
    fun `distance OSA compte remplacement, ajout, oubli et inversion pour une faute`() {
        assertEquals(0, osaDistance("kendji", "kendji", 2))
        assertEquals(1, osaDistance("kendij", "kendji", 2)) // inversion
        assertEquals(1, osaDistance("kandji", "kendji", 2)) // remplacement
        assertEquals(1, osaDistance("kendjii", "kendji", 2)) // ajout
        assertEquals(1, osaDistance("kndji", "kendji", 2)) // oubli
    }

    @Test
    fun `distance OSA s arrete au dela de la limite`() {
        assertEquals(2, osaDistance("abcdef", "uvwxyz", 1))
        assertEquals(2, osaDistance("a", "abcd", 1))
    }

    @Test
    fun `une faute de frappe retrouve le titre`() {
        assertTrue(fuzzyScore("Si seulement...", "seulmeent") > 0)
        assertTrue(fuzzyScore("Kendji Girac", "kendij") > 0)
        assertTrue(fuzzyScore("Stick Season", "stik season") > 0)
    }

    @Test
    fun `les mots peuvent etre dans le desordre`() {
        assertEquals(300, fuzzyScore("Stick Season", "season stick"))
    }

    @Test
    fun `une saisie en cours avec faute correspond au debut du mot`() {
        assertTrue(fuzzyScore("Réveiller le feu", "reveillr") > 0)
        assertTrue(fuzzyScore("Kendji Girac", "kednj") > 0)
    }

    @Test
    fun `les mots courts restent exacts`() {
        // Une faute tolérée sur trois lettres ferait correspondre presque tout.
        assertEquals(0, fuzzyScore("Zazie", "zaz x"))
        assertEquals(0, fuzzyScore("Noah Kahan", "bob"))
    }

    @Test
    fun `un titre exact passe toujours avant une correction`() {
        val exact = fuzzyScore("Kendji", "kendji")
        val typo = fuzzyScore("Kendji", "kendij")
        assertTrue(exact > typo)
        // Et une correction passe avant une simple sous-séquence de lettres.
        assertTrue(fuzzyScore("Kendji Girac", "kendij") > fuzzyScore("Kendji Girac", "kgc"))
    }

    @Test
    fun `sans rapport, aucun resultat`() {
        assertEquals(0, fuzzyScore("Stick Season", "metallica"))
    }

    @Test
    fun `des lettres eparpillees sur tout le titre ne correspondent pas`() {
        assertEquals(0, fuzzyScore("Take Me Home, Country Roads", "kendij"))
        // Initiales groupées : toujours trouvées.
        assertEquals(150, fuzzyScore("Daft Punk", "dfpk"))
    }
}
