package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingsAndShowsTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `regle note - superieur a et titres non notes a zero`() {
        val songs = listOf(song(id = 1), song(id = 2), song(id = 3))
        val ratings = mapOf(1L to 5, 2L to 3)
        val rules = RulePlaylistRules(listOf(SmartRule(RuleField.RATING, RuleOperator.GREATER_THAN, "3")))
        val result = RulePlaylistEngine.evaluate(rules, songs, emptyMap(), emptySet(), now, ratings)
        assertEquals(listOf(1L), result.map { it.id })

        val unrated = RulePlaylistRules(listOf(SmartRule(RuleField.RATING, RuleOperator.EQUALS, "0")))
        assertEquals(listOf(3L), RulePlaylistEngine.evaluate(unrated, songs, emptyMap(), emptySet(), now, ratings).map { it.id })
    }

    @Test
    fun `regle note - champ numerique`() {
        assertEquals(true, RuleOperator.GREATER_THAN in RuleField.RATING.operators)
    }

    @Test
    fun `emission - podcast par album, livre audio par dossier, musique sans cle`() {
        assertEquals("podcast:Le Code a changé", ShowKeys.of(song(album = "Le Code a changé").copy(isPodcast = true)))
        assertEquals("podcast:/pod/show", ShowKeys.of(song(album = "", path = "/pod/show/ep1.mp3").copy(isPodcast = true)))
        assertEquals("book:/books/Dune", ShowKeys.of(song(path = "/books/Dune/01.mp3").copy(isAudiobook = true)))
        assertNull(ShowKeys.of(song()))
        assertNull(ShowKeys.of(null))
    }
}
