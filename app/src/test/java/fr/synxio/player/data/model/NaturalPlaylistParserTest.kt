package fr.synxio.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalPlaylistParserTest {

    private val year = 2026
    private val vocabulary = LibraryVocabulary(
        genres = listOf("Rock", "Métal", "Pop", "Rap", "Électro", "Chanson française", "Jazz", "Hard Rock"),
        artists = listOf("Daft Punk", "Nirvana", "Drake", "Stromae"),
        decades = listOf(1990, 2000, 2010),
    )

    private fun parse(text: String, vocab: LibraryVocabulary = vocabulary) = NaturalPlaylistParser.parse(text, vocab, year)
    private fun rule(field: RuleField, op: RuleOperator, value: String) = SmartRule(field, op, value)
    private fun rules(text: String) = parse(text).rules.rules

    @Test
    fun `une heure de rock calme des annees 90 - quatre clauses, rien d'ignore`() {
        val parsed = parse("une heure de rock calme des années 90")
        assertEquals(listOf(ClauseKind.GENRE, ClauseKind.PERIOD, ClauseKind.ENERGY, ClauseKind.QUANTITY), parsed.understood.map { it.kind })
        assertEquals(emptyList<String>(), parsed.ignored)
        val r = parsed.rules
        assertTrue(rule(RuleField.GENRE, RuleOperator.CONTAINS, "rock") in r.rules)
        assertTrue(rule(RuleField.ENERGY, RuleOperator.LESS_THAN, "36") in r.rules)
        assertTrue(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "1989") in r.rules)
        assertTrue(rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2000") in r.rules)
        assertEquals(15, r.limit)
        assertEquals(SmartSort.RANDOM, r.sort)
        assertTrue(r.anyOf.isEmpty())
    }

    @Test
    fun `rock ou metal - groupe l'un de`() {
        val parsed = parse("rock ou métal")
        val genre = parsed.understood.single()
        assertEquals(ClauseKind.GENRE, genre.kind)
        assertEquals("Rock ou Métal", genre.label)
        assertEquals(
            setOf(rule(RuleField.GENRE, RuleOperator.CONTAINS, "rock"), rule(RuleField.GENRE, RuleOperator.CONTAINS, "metal")),
            parsed.rules.anyOf.toSet(),
        )
        assertTrue(parsed.rules.rules.isEmpty())
        assertEquals(emptyList<String>(), parsed.ignored)
    }

    @Test
    fun `sans rap - exclusion de genre`() {
        val parsed = parse("du rock sans rap")
        assertTrue(rule(RuleField.GENRE, RuleOperator.NOT_CONTAINS, "rap") in parsed.rules.rules)
        assertTrue(rule(RuleField.GENRE, RuleOperator.CONTAINS, "rock") in parsed.rules.rules)
        assertEquals("Sans rap", parsed.understood.first { it.kind == ClauseKind.GENRE_EXCLUDED }.label)
    }

    @Test
    fun `une heure de chanson francaise calme`() {
        val parsed = parse("une heure de chanson française calme")
        // « chanson » suffit : la bibliothèque a « Chanson française », la cible la plus courte l'attrape.
        assertTrue(rule(RuleField.GENRE, RuleOperator.CONTAINS, "chanson") in parsed.rules.rules)
        assertEquals("Chanson française", parsed.understood.first { it.kind == ClauseKind.GENRE }.label)
        assertTrue(rule(RuleField.ENERGY, RuleOperator.LESS_THAN, "36") in parsed.rules.rules)
        assertEquals(15, parsed.rules.limit)
        assertEquals(emptyList<String>(), parsed.ignored)
    }

    @Test
    fun `accents - avec ou sans, meme resultat, faute de frappe ignoree`() {
        assertEquals(parse("musique énergique").rules, parse("musique energique").rules)
        assertEquals(parse("du métal des années 80").rules, parse("du metal des annees 80").rules)
        val typo = parse("musique enerjique")
        assertTrue(typo.understood.isEmpty())
        assertEquals(listOf("enerjique"), typo.ignored)
    }

    @Test
    fun `periodes`() {
        fun years(text: String) = rules(text).filter { it.field == RuleField.YEAR }.toSet()
        val nineties = setOf(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "1989"), rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2000"))
        assertEquals(nineties, years("90s"))
        assertEquals(nineties, years("les nineties"))
        assertEquals(nineties, years("années 90"))
        assertEquals(setOf(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "1999"), rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2010")), years("années 2000"))
        assertEquals(setOf(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "2014"), rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2016")), years("de 2015"))
        assertEquals(setOf(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "1999"), rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2011")), years("entre 2000 et 2010"))
        assertEquals(setOf(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "2023")), years("du rap récent"))
        assertEquals(setOf(rule(RuleField.YEAR, RuleOperator.LESS_THAN, "1996")), years("des vieux morceaux"))
        assertEquals(setOf(rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2000")), years("avant 2000"))
        assertEquals(setOf(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "2019")), years("depuis 2020"))
        assertEquals("Années 90", parse("années 90").understood.single().label)
    }

    @Test
    fun `energie`() {
        assertEquals(listOf(rule(RuleField.ENERGY, RuleOperator.GREATER_THAN, "59")), rules("un truc énergique"))
        assertEquals(listOf(rule(RuleField.ENERGY, RuleOperator.GREATER_THAN, "59")), rules("dansant"))
        assertEquals(listOf(rule(RuleField.ENERGY, RuleOperator.LESS_THAN, "36")), rules("quelque chose de chill"))
        assertEquals(listOf(rule(RuleField.ENERGY, RuleOperator.LESS_THAN, "36")), rules("pour dormir"))
        assertEquals(
            listOf(rule(RuleField.ENERGY, RuleOperator.GREATER_THAN, "29"), rule(RuleField.ENERGY, RuleOperator.LESS_THAN, "61")),
            rules("pour travailler"),
        )
        assertEquals(
            listOf(
                rule(RuleField.ENERGY, RuleOperator.GREATER_THAN, "59"),
                rule(RuleField.BPM, RuleOperator.GREATER_THAN, "149"),
                rule(RuleField.BPM, RuleOperator.LESS_THAN, "191"),
            ),
            rules("pour courir"),
        )
    }

    @Test
    fun `tempo`() {
        assertEquals(listOf(rule(RuleField.BPM, RuleOperator.GREATER_THAN, "113"), rule(RuleField.BPM, RuleOperator.LESS_THAN, "127")), rules("120 bpm"))
        assertEquals(listOf(rule(RuleField.BPM, RuleOperator.GREATER_THAN, "160"), rule(RuleField.BPM, RuleOperator.LESS_THAN, "180")), rules("autour de 170"))
        assertEquals(listOf(rule(RuleField.BPM, RuleOperator.GREATER_THAN, "140")), rules("plus de 140"))
        assertEquals(listOf(rule(RuleField.BPM, RuleOperator.GREATER_THAN, "129")), rules("du rock rapide").filter { it.field == RuleField.BPM })
        assertEquals(listOf(rule(RuleField.BPM, RuleOperator.LESS_THAN, "90")), rules("un morceau lent"))
    }

    @Test
    fun `tonalite`() {
        assertEquals(listOf(rule(RuleField.KEY, RuleOperator.EQUALS, "la mineur")), rules("en la mineur"))
        assertEquals(listOf(rule(RuleField.KEY, RuleOperator.EQUALS, "ré♯ majeur")), rules("en ré dièse majeur"))
        assertEquals(listOf(rule(RuleField.KEY, RuleOperator.CONTAINS, "mineur")), rules("du jazz en mineur").filter { it.field == RuleField.KEY })
        assertEquals(listOf(rule(RuleField.KEY, RuleOperator.CONTAINS, "majeur")), rules("en majeur"))
        assertEquals(listOf(rule(RuleField.KEY, RuleOperator.EQUALS, "8A")), rules("des titres en 8a"))
    }

    @Test
    fun `nouveaute`() {
        assertEquals(listOf(rule(RuleField.PLAY_COUNT, RuleOperator.EQUALS, "0")), rules("jamais écouté"))
        assertEquals(listOf(rule(RuleField.PLAY_COUNT, RuleOperator.EQUALS, "0")), rules("du rap que je n'ai jamais écouté").filter { it.field == RuleField.PLAY_COUNT })
        assertEquals(listOf(rule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "365")), rules("pas écouté depuis un an"))
        assertEquals(listOf(rule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "180")), rules("pas écouté depuis 6 mois"))
        assertEquals(listOf(rule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "90")), rules("pas écouté depuis longtemps"))
        assertEquals(listOf(rule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "2")), rules("que je connais"))
        assertEquals(listOf(rule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "2")), rules("découverte"))
    }

    @Test
    fun `gout`() {
        assertEquals(listOf(rule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")), rules("mes favoris"))
        assertEquals(listOf(rule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")), rules("que j'adore"))
        assertEquals(listOf(rule(RuleField.RATING, RuleOperator.GREATER_THAN, "3")), rules("bien notés"))
        assertEquals(listOf(rule(RuleField.RATING, RuleOperator.GREATER_THAN, "3")), rules("4 étoiles"))
        assertEquals(listOf(rule(RuleField.RATING, RuleOperator.GREATER_THAN, "4")), rules("plus de 4 étoiles"))
    }

    @Test
    fun `artistes - de, par, sans, sauf, flou, et absent de la bibliotheque`() {
        assertEquals(listOf(rule(RuleField.ARTIST, RuleOperator.EQUALS, "Daft Punk")), rules("de daft punk"))
        assertEquals(listOf(rule(RuleField.ARTIST, RuleOperator.EQUALS, "Nirvana")), rules("par nirvana"))
        assertEquals(listOf(rule(RuleField.ARTIST, RuleOperator.NOT_EQUALS, "Drake")), rules("sans drake"))
        assertEquals(listOf(rule(RuleField.ARTIST, RuleOperator.NOT_EQUALS, "Drake")), rules("sauf drake"))
        assertEquals(listOf(rule(RuleField.ARTIST, RuleOperator.EQUALS, "Daft Punk")), rules("du dafte punk"))
        assertEquals("Daft Punk", parse("de daft punk").understood.single().label)

        val absent = parse("de michel")
        assertTrue(absent.understood.isEmpty())
        assertTrue(absent.rules.rules.none { it.field == RuleField.ARTIST })
        assertEquals(listOf("michel"), absent.ignored)

        // Sans vocabulaire, aucun nom n'est inventé (« punk » reste lu comme un genre).
        val blind = parse("de daft punk", LibraryVocabulary.EMPTY)
        assertTrue(blind.understood.none { it.kind == ClauseKind.ARTIST })
        assertEquals(listOf("daft"), blind.ignored)
    }

    @Test
    fun `artiste cite sans preposition, avec un ordre`() {
        val parsed = parse("stromae en aléatoire")
        assertEquals(listOf(rule(RuleField.ARTIST, RuleOperator.EQUALS, "Stromae")), parsed.rules.rules)
        assertEquals(SmartSort.RANDOM, parsed.rules.sort)
        assertEquals(listOf(ClauseKind.ARTIST, ClauseKind.ORDER), parsed.understood.map { it.kind })
    }

    @Test
    fun `quantites et durees`() {
        assertEquals(30, parse("30 titres").rules.limit)
        assertEquals(30, parse("deux heures").rules.limit)
        assertEquals(10, parse("pour un trajet de 40 min").rules.limit)
        assertEquals(23, parse("une heure et demie").rules.limit)
        assertEquals(8, parse("une demi-heure de pop").rules.limit)
        assertEquals("40 min (10 titres)", parse("pour un trajet de 40 min").understood.single().label)
        assertEquals(emptyList<String>(), parse("pour un trajet de 40 min").ignored)
        assertEquals(SmartSort.RANDOM, parse("30 titres").rules.sort)
    }

    @Test
    fun `ordres`() {
        assertEquals(SmartSort.RANDOM, parse("du rock aléatoire").rules.sort)
        val rise = parse("du plus calme au plus énergique").rules
        assertEquals(SmartSort.ENERGY, rise.sort)
        assertEquals(false, rise.descending)
        assertTrue(rise.rules.isEmpty())
        assertEquals(SmartSort.FLOW, parse("mes favoris en flow").rules.sort)
        val most = parse("les plus écoutés").rules
        assertEquals(SmartSort.PLAY_COUNT, most.sort)
        assertEquals(true, most.descending)
    }

    @Test
    fun `morceaux courts et longs`() {
        assertEquals(listOf(rule(RuleField.DURATION, RuleOperator.LESS_THAN, "180")), rules("morceaux courts"))
        assertEquals(listOf(rule(RuleField.DURATION, RuleOperator.GREATER_THAN, "360")), rules("des titres longs"))
    }

    @Test
    fun `phrase vide et phrase sans clause`() {
        val empty = parse("")
        assertTrue(empty.understood.isEmpty())
        assertTrue(empty.ignored.isEmpty())
        assertEquals(RulePlaylistRules(sort = SmartSort.RANDOM), empty.rules)

        val none = parse("quelque chose de bien")
        assertTrue(none.understood.isEmpty())
        assertEquals(listOf("bien"), none.ignored)
    }

    @Test
    fun `genres - synonymes et correspondance avec la bibliotheque`() {
        // « électro » : la bibliothèque n'a qu'« Électro », pas de techno : une seule cible.
        assertEquals(listOf(rule(RuleField.GENRE, RuleOperator.CONTAINS, "electro")), rules("de l'électro"))
        // « hip-hop » appartient à la famille rap ; le genre présent est « Rap ».
        assertEquals(listOf(rule(RuleField.GENRE, RuleOperator.CONTAINS, "rap")), rules("du hip-hop"))
        // Sans bibliothèque, le mot tapé sert tel quel.
        assertEquals(listOf(rule(RuleField.GENRE, RuleOperator.CONTAINS, "techno")), parse("de la techno", LibraryVocabulary.EMPTY).rules.rules)
        // Genre de la bibliothèque approchant par le flou : la valeur est le nom de la bibliothèque.
        val fuzzy = parse("de l'elektro")
        assertEquals(listOf(rule(RuleField.GENRE, RuleOperator.CONTAINS, "Électro")), fuzzy.rules.rules)
        assertEquals(emptyList<String>(), fuzzy.ignored)
        assertEquals(listOf(rule(RuleField.GENRE, RuleOperator.CONTAINS, "Chanson française")), rules("de la chansson francaise"))
    }

    @Test
    fun `non-regression voix 5_9 - memes phrases, memes periodes`() {
        val rock90 = parse("mets-moi du rock des années 90")
        assertEquals(setOf(ClauseKind.GENRE, ClauseKind.PERIOD), rock90.understood.map { it.kind }.toSet())
        assertTrue(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "1989") in rock90.rules.rules)
        assertTrue(rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2000") in rock90.rules.rules)
        assertTrue(rock90.ignored.isEmpty())

        assertEquals(
            setOf(rule(RuleField.YEAR, RuleOperator.GREATER_THAN, "2014"), rule(RuleField.YEAR, RuleOperator.LESS_THAN, "2016")),
            rules("de 2015").toSet(),
        )

        val favorites = parse("mes favoris en aléatoire")
        assertEquals(listOf(rule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")), favorites.rules.rules)
        assertEquals(SmartSort.RANDOM, favorites.rules.sort)

        // La période partagée répond exactement comme la grammaire 5.9.
        assertEquals(1990..1999, NaturalPlaylistParser.period("rock des annees 90", year)!!.range)
        assertEquals(2024..2026, NaturalPlaylistParser.period("rap recent", year)!!.range)
        assertEquals(1900..1999, NaturalPlaylistParser.period("avant 2000", year)!!.range)
        assertEquals(2015..2018, NaturalPlaylistParser.period("jazz de 2015 a 2018", year)!!.range)
        assertNull(NaturalPlaylistParser.period("stromae", year))
    }

    @Test
    fun `retirer une clause recompose les regles sans elle`() {
        val parsed = parse("une heure de rock calme des années 90")
        val withoutPeriod = NaturalPlaylistParser.assemble(parsed.understood.filter { it.kind != ClauseKind.PERIOD })
        assertTrue(withoutPeriod.rules.none { it.field == RuleField.YEAR })
        assertEquals(15, withoutPeriod.limit)
        assertTrue(rule(RuleField.GENRE, RuleOperator.CONTAINS, "rock") in withoutPeriod.rules)
    }

    @Test
    fun `nom propose - sans verbe, court, capitale`() {
        assertEquals("Rock calme des années 90", NaturalPlaylistParser.suggestName("mets-moi du rock calme des années 90"))
        assertEquals("Une heure de rock calme des années 90", NaturalPlaylistParser.suggestName("une heure de rock calme des années 90"))
        assertEquals("Ma playlist", NaturalPlaylistParser.suggestName("  "))
        assertTrue(NaturalPlaylistParser.suggestName("du rap énergique que je n'ai jamais écouté depuis des lustres et encore").length <= 40)
    }
}
