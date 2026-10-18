package fr.synxio.player.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class TidyTagTest {

    @Test
    fun `un accent decompose est recompose en un seul caractere`() {
        val decomposed = "Réveiller le feu"
        val tidy = decomposed.tidyTag()
        assertEquals("Réveiller le feu", tidy)
        assertEquals(16, tidy.length)
    }

    @Test
    fun `les espaces parasites sont retires`() {
        assertEquals("nos souvenirs", "  nos souvenirs ".tidyTag())
    }

    @Test
    fun `un tag deja propre est rendu tel quel`() {
        val clean = "Stick Season"
        assertEquals(clean, clean.tidyTag())
    }

    @Test
    fun `un tag UTF-8 lu comme du Latin-1 est repare`() {
        // Octets réels du MediaStore de l'appareil : 52 65 C3 8C C2 81 76 …
        assertEquals("Réveiller le feu", "Re\u00CC\u0081veiller le feu".tidyTag())
        assertEquals("Café", "Caf\u00C3\u00A9".tidyTag())
        assertEquals("Beyoncé", "Beyonc\u00C3\u00A9".tidyTag())
    }

    @Test
    fun `un tag Latin-1 sain n est jamais modifie`() {
        assertEquals("Café", "Café".tidyTag())
        assertEquals("Noël à Paris", "Noël à Paris".tidyTag())
        assertEquals("Æon", "Æon".tidyTag())
    }

    @Test
    fun `un texte hors Latin-1 n est pas touche`() {
        assertEquals("東京 \u00C3\u00A9", "東京 \u00C3\u00A9".tidyTag())
    }
}
