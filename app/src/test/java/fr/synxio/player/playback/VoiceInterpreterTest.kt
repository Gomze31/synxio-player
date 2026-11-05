package fr.synxio.player.playback

import fr.synxio.player.core.util.normalizeForSearch
import fr.synxio.player.playback.VoiceIntent.Action
import fr.synxio.player.playback.VoiceIntent.Subject
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInterpreterTest {

    private val year = 2026

    private fun intent(
        phrase: String,
        action: Action = Action.PLAY,
        subject: Subject? = null,
        name: String? = null,
        years: IntRange? = null,
        shuffle: Boolean = false,
        text: String? = null,
    ) = phrase to VoiceIntent(action, subject, name, years, shuffle, text)

    private fun interpret(phrase: String, request: VoiceRequest = VoiceRequest(phrase)) =
        VoiceInterpreter.interpret(phrase.normalizeForSearch(), request, year)

    /** [text] n'est vérifié que lorsqu'il est attendu : il porte le reste de la phrase. */
    private fun check(cases: List<Pair<String, VoiceIntent>>) {
        for ((phrase, expected) in cases) {
            val actual = interpret(phrase)
            val compared = if (expected.text == null) actual.copy(text = null) else actual
            assertEquals("« $phrase »", expected, compared)
        }
    }

    @Test
    fun `verbes, determinants, periodes`() = check(
        listOf(
            intent("joue du rock", name = "rock"),
            intent("mets-moi du rock des années 90", name = "rock", years = 1990..1999),
            intent("lance les années 80", years = 1980..1989, text = "annees 80"),
            intent("joue années 80", years = 1980..1989, text = "annees 80"),
            intent("écoute de la pop de 2015", name = "pop", years = 2015..2015),
            intent("de 2015", years = 2015..2015),
            intent("du jazz de 2015 à 2018", name = "jazz", years = 2015..2018),
            intent("de la musique avant 2000", years = 1900..1999),
            intent("du rap récent", name = "rap", years = 2024..2026),
            intent("les nineties", years = 1990..1999),
            intent("du rock 2010s", name = "rock", years = 2010..2019),
            intent("du blues depuis 2020", name = "blues", years = 2020..2026),
            intent("de la soul après 2015", name = "soul", years = 2016..2026),
            intent("joue un peu de jazz", name = "jazz"),
            intent("mets de la musique"),
            intent(""),
        )
    )

    @Test
    fun `melange, favoris, souvenirs`() = check(
        listOf(
            intent("stromae en aléatoire", name = "stromae", shuffle = true),
            intent("au hasard", action = Action.SHUFFLE, shuffle = true),
            intent("lecture aléatoire", action = Action.SHUFFLE, shuffle = true),
            intent("passe du stromae en mélange", name = "stromae", shuffle = true),
            intent("mes favoris", subject = Subject.FAVORITES),
            intent("mes coups de cœur en aléatoire", subject = Subject.FAVORITES, shuffle = true),
            intent("ce jour-là", subject = Subject.MEMORY),
            intent("mes souvenirs", subject = Subject.MEMORY),
            intent("il y a un an", subject = Subject.MEMORY),
        )
    )

    @Test
    fun `mixs, reprise, radio, pieges`() = check(
        listOf(
            intent("mon mix du jour", subject = Subject.MIX, name = "daily"),
            intent("ma découverte du jour", subject = Subject.MIX, name = "discovery"),
            intent("le mix stromae", subject = Subject.MIX, name = "stromae"),
            intent("joue le mix de daft punk", subject = Subject.MIX, name = "daft punk"),
            intent("joue mix", name = "mix"),
            intent("continue", action = Action.RESUME_BOOK),
            intent("reprends mon livre", action = Action.RESUME_BOOK),
            intent("mon podcast", action = Action.RESUME_BOOK),
            intent("là où j'en étais", action = Action.RESUME_BOOK),
            intent("ce que j'écoutais", action = Action.RESUME_LAST),
            intent("reprends ma dernière file", action = Action.RESUME_LAST),
            intent("la radio fip", subject = Subject.RADIO, name = "fip"),
            intent("fip en radio", subject = Subject.RADIO, name = "fip"),
            intent("joue radio ga ga", subject = Subject.RADIO, name = "ga ga"),
        )
    )

    @Test
    fun `focus de l'assistant - sujet et nom fixes, la grammaire ajoute periode et melange`() {
        val artist = interpret(
            "daft punk des années 2000 en aléatoire",
            VoiceRequest("daft punk des années 2000 en aléatoire", focus = VoiceRequest.FOCUS_ARTIST, artist = "Daft Punk"),
        )
        assertEquals(Subject.ARTIST, artist.subject)
        assertEquals("Daft Punk", artist.name)
        assertEquals(2000..2009, artist.years)
        assertEquals(true, artist.shuffle)

        val title = interpret("te quiero", VoiceRequest("te quiero", focus = VoiceRequest.FOCUS_TITLE, title = "Te quiero", artist = "Stromae"))
        assertEquals(Subject.TITLE, title.subject)
        assertEquals("Te quiero", title.name)

        val genre = interpret("du rock", VoiceRequest("du rock", focus = VoiceRequest.FOCUS_GENRE, genre = "Rock"))
        assertEquals(Subject.GENRE, genre.subject)
        assertEquals("Rock", genre.name)

        val playlist = interpret("route", VoiceRequest("route", focus = VoiceRequest.FOCUS_PLAYLIST, playlist = "Route"))
        assertEquals(Subject.PLAYLIST, playlist.subject)
        assertEquals("Route", playlist.name)

        val album = interpret("cheese", VoiceRequest("cheese", focus = VoiceRequest.FOCUS_ALBUM, album = "Cheese", artist = "Stromae"))
        assertEquals(Subject.ALBUM, album.subject)
        assertEquals("Cheese", album.name)
    }

    @Test
    fun `texte - reste de la phrase sans verbe ni melange, periode comprise`() {
        assertEquals("rock des annees 90", interpret("mets-moi du rock des années 90 au hasard").text)
        assertEquals(null, interpret("au hasard").text)
        assertEquals("de 2015", interpret("de 2015").text)
    }
}
