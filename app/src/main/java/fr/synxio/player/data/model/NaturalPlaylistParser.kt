package fr.synxio.player.data.model

import fr.synxio.player.core.util.normalizeForSearch
import java.time.Year
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Ce que la bibliothèque contient, pour reconnaître ses genres et ses artistes dans une phrase. */
data class LibraryVocabulary(
    val genres: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    /** Décennies présentes (1990, 2000…) ; portées pour l'écran, l'analyseur ne filtre pas dessus. */
    val decades: List<Int> = emptyList(),
) {
    companion object {
        val EMPTY = LibraryVocabulary()
    }
}

enum class ClauseKind {
    GENRE, GENRE_EXCLUDED, PERIOD, ENERGY, TEMPO, KEY, NOVELTY, TASTE, ARTIST, ARTIST_EXCLUDED, QUANTITY, ORDER, LENGTH,
}

/**
 * Un morceau de phrase compris : son libellé (chip « Compris ») et ce qu'il apporte aux
 * règles. Une clause porte des règles en ET, ou des alternatives en OU ([anyOf]), ou un
 * tri, ou une limite ; [NaturalPlaylistParser.assemble] les recompose.
 */
data class Clause(
    val kind: ClauseKind,
    val label: String,
    val rules: List<SmartRule> = emptyList(),
    val anyOf: List<SmartRule> = emptyList(),
    val sort: SmartSort? = null,
    val descending: Boolean = false,
    val limit: Int? = null,
)

data class ParsedPlaylist(
    val rules: RulePlaylistRules,
    /** Chips « Compris ». */
    val understood: List<Clause>,
    /** Mots non interprétés, mots vides retirés. */
    val ignored: List<String>,
)

/**
 * Une période trouvée dans un texte normalisé. [openStart] / [openEnd] disent qu'une borne
 * n'a pas été dite (« avant 2000 », « depuis 2020 ») ; [matched] est la plage de caractères
 * à retirer du texte.
 */
data class PeriodMatch(
    val range: IntRange,
    val openStart: Boolean,
    val openEnd: Boolean,
    val matched: IntRange,
    val label: String,
)

/**
 * Lit une playlist décrite en français (« une heure de rock calme des années 90 ») et en
 * fait des règles. Pur : ni Android, ni bibliothèque, seulement le vocabulaire reçu.
 *
 * Principe : la phrase est normalisée (`normalizeForSearch`, ponctuation et apostrophes en
 * espaces), puis des reconnaisseurs passent dans un ordre fixe, du plus précis au plus
 * générique ; chaque clause reconnue est effacée du texte avant la suivante, ce qui évite
 * que « du plus calme au plus énergique » soit lu comme deux clauses d'énergie. Ce qui
 * reste, mots vides retirés, est rendu dans `ignored` pour que l'écran le montre.
 */
object NaturalPlaylistParser {

    fun parse(
        text: String,
        vocabulary: LibraryVocabulary = LibraryVocabulary.EMPTY,
        currentYear: Int = Year.now().value,
    ): ParsedPlaylist {
        val normalized = normalize(text)
        if (normalized.isBlank()) return ParsedPlaylist(assemble(emptyList()), emptyList(), emptyList())
        val cursor = Cursor(normalized)
        val clauses = mutableListOf<Clause>()

        quantity(cursor)?.let(clauses::add)
        novelty(cursor)?.let(clauses::add)
        taste(cursor).let(clauses::addAll)
        order(cursor)?.let(clauses::add)
        energy(cursor).let(clauses::addAll)
        tempo(cursor)?.let(clauses::add)
        key(cursor)?.let(clauses::add)
        period(cursor.text, currentYear)?.let { match ->
            cursor.consume(match.matched)
            clauses += periodClause(match)
        }
        length(cursor)?.let(clauses::add)
        exclusions(cursor, vocabulary).let(clauses::addAll)
        // « de Daft Punk » avant les genres : « punk » appartient aussi au nom de l'artiste.
        artists(cursor, vocabulary, prepositionOnly = true).let(clauses::addAll)
        genres(cursor, vocabulary)?.let(clauses::add)
        artists(cursor, vocabulary, prepositionOnly = false).let(clauses::addAll)

        val ordered = clauses.sortedBy { it.kind.ordinal }
        val ignored = cursor.remainingWords().filter { it.length > 1 && it !in STOP_WORDS }
        return ParsedPlaylist(assemble(ordered), ordered, ignored)
    }

    /**
     * Recompose les règles à partir des clauses : règles en ET, alternatives en OU, premier
     * tri explicite (ordre, sinon celui d'une quantité, sinon aléatoire : une playlist
     * dictée se mélange), première limite.
     */
    fun assemble(clauses: List<Clause>): RulePlaylistRules {
        val order = clauses.firstOrNull { it.kind == ClauseKind.ORDER && it.sort != null }
            ?: clauses.firstOrNull { it.sort != null }
        return RulePlaylistRules(
            rules = clauses.flatMap { it.rules },
            matchAll = true,
            sort = order?.sort ?: SmartSort.RANDOM,
            descending = order?.descending ?: false,
            limit = clauses.firstNotNullOfOrNull { it.limit },
            anyOf = clauses.flatMap { it.anyOf },
        )
    }

    /** Nom proposé pour l'enregistrement : la phrase sans verbe de tête, courte, capitale initiale. */
    fun suggestName(text: String): String {
        var s = text.trim().replace(SPACES, " ").trimEnd('.', '!', '?', ',', ' ')
        repeat(3) { s = s.replace(LEADING_VERBS, "").trim() }
        if (s.isBlank()) return "Ma playlist"
        s = s.replaceFirstChar { it.uppercaseChar() }
        if (s.length > MAX_NAME) {
            val cut = s.lastIndexOf(' ', MAX_NAME)
            s = (if (cut > MAX_NAME / 2) s.substring(0, cut) else s.substring(0, MAX_NAME)).trimEnd(',', ' ')
        }
        return s
    }

    // --- Période (partagée avec la voix Android Auto) --------------------------------------

    /**
     * Première période trouvée dans [text] (déjà normalisé), dans l'ordre de priorité de la
     * 5.9 : plage, décennie en mots, décennie en suffixe, décennie en anglais, avant, après,
     * depuis, année seule, récent ; puis « vieux ».
     */
    fun period(text: String, currentYear: Int): PeriodMatch? {
        fun decade(raw: String): IntRange {
            val n = raw.toInt()
            val start = when {
                n >= 1000 -> n / 10 * 10
                n >= 30 -> 1900 + n / 10 * 10
                else -> 2000 + n / 10 * 10
            }
            return start..start + 9
        }
        fun closed(range: IntRange, m: MatchResult, label: String) = PeriodMatch(range, false, false, m.range, label)

        BETWEEN.find(text)?.let { return closed(it.groupValues[1].toInt()..it.groupValues[2].toInt(), it, "${it.groupValues[1]}–${it.groupValues[2]}") }
        RANGE.find(text)?.let { return closed(it.groupValues[1].toInt()..it.groupValues[2].toInt(), it, "${it.groupValues[1]}–${it.groupValues[2]}") }
        DECADE_WORD.find(text)?.let { val d = decade(it.groupValues[1]); return closed(d, it, "Années ${decadeLabel(d)}") }
        DECADE_SUFFIX.find(text)?.let { val d = decade(it.groupValues[1]); return closed(d, it, "Années ${decadeLabel(d)}") }
        DECADE_NAME.find(text)?.let { val d = DECADES.getValue(it.groupValues[1]); return closed(d, it, "Années ${decadeLabel(d)}") }
        BEFORE.find(text)?.let { return PeriodMatch(1900..it.groupValues[1].toInt() - 1, true, false, it.range, "Avant ${it.groupValues[1]}") }
        AFTER.find(text)?.let { return PeriodMatch(it.groupValues[1].toInt() + 1..currentYear, false, true, it.range, "Après ${it.groupValues[1]}") }
        SINCE.find(text)?.let { return PeriodMatch(it.groupValues[1].toInt()..currentYear, false, true, it.range, "Depuis ${it.groupValues[1]}") }
        SINGLE_YEAR.find(text)?.let { val y = it.groupValues[1].toInt(); return closed(y..y, it, "$y") }
        RECENT.find(text)?.let { return PeriodMatch(currentYear - 2..currentYear, false, true, it.range, "Récent") }
        OLD.find(text)?.let { return PeriodMatch(1900..OLD_UNTIL, true, false, it.range, "Vieux (jusqu'à $OLD_UNTIL)") }
        return null
    }

    private fun decadeLabel(d: IntRange): String = if (d.first < 2000) "${d.first % 100}" else "${d.first}"

    private fun periodClause(m: PeriodMatch): Clause {
        val rules = buildList {
            if (!m.openStart) add(SmartRule(RuleField.YEAR, RuleOperator.GREATER_THAN, "${m.range.first - 1}"))
            if (!m.openEnd) add(SmartRule(RuleField.YEAR, RuleOperator.LESS_THAN, "${m.range.last + 1}"))
        }
        return Clause(ClauseKind.PERIOD, m.label, rules)
    }

    // --- Reconnaisseurs -------------------------------------------------------------------

    private fun quantity(c: Cursor): Clause? {
        COUNT.find(c.text)?.let { m ->
            val n = m.groupValues[1].toInt()
            if (n in 1..999) {
                c.consume(m.range)
                return Clause(ClauseKind.QUANTITY, "$n titres", sort = SmartSort.RANDOM, limit = n)
            }
        }
        DURATION.find(c.text)?.let { m ->
            val minutes = durationMinutes(m) ?: return@let
            if (minutes <= 0) return@let
            c.consume(m.range)
            val limit = max(1, (minutes / MINUTES_PER_SONG).roundToInt())
            val label = when {
                minutes % 60 == 0 -> "${minutes / 60} h"
                minutes > 60 -> "${minutes / 60} h ${minutes % 60}"
                else -> "$minutes min"
            }
            return Clause(ClauseKind.QUANTITY, "$label ($limit titres)", sort = SmartSort.RANDOM, limit = limit)
        }
        return null
    }

    private fun durationMinutes(m: MatchResult): Int? {
        m.groupValues[1].takeIf { it.isNotEmpty() }?.let { return it.toInt() }
        m.groupValues[2].takeIf { it.isNotEmpty() }?.let { return it.toInt() * 60 + m.groupValues[3].toInt() }
        m.groupValues[4].takeIf { it.isNotEmpty() }?.let { return it.toInt() * 60 }
        m.groupValues[5].takeIf { it.isNotEmpty() }?.let { word ->
            val hours = HOUR_WORDS[word] ?: return null
            return hours * 60 + if (m.groupValues[6].isNotEmpty()) 30 else 0
        }
        if (m.groupValues[7].isNotEmpty()) return 30
        return null
    }

    private fun novelty(c: Cursor): Clause? {
        NEVER_PLAYED.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.NOVELTY, "Jamais écouté", listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.EQUALS, "0"))) }
        NOT_PLAYED_SINCE.find(c.text)?.let { m ->
            val days = when (m.groupValues[1]) {
                "un an", "1 an", "12 mois", "une annee" -> 365
                "six mois", "6 mois" -> 180
                else -> 90
            }
            c.consume(m.range)
            return Clause(ClauseKind.NOVELTY, "Pas écouté depuis ${m.groupValues[1]}", listOf(SmartRule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "$days")))
        }
        FORGOTTEN.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.NOVELTY, "Oubliés", listOf(SmartRule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "180"))) }
        KNOWN.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.NOVELTY, "Que tu connais", listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "2"))) }
        DISCOVERY.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.NOVELTY, "À découvrir", listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "2"))) }
        return null
    }

    private fun taste(c: Cursor): List<Clause> = buildList {
        FAVORITES.find(c.text)?.let { c.consume(it.range); add(Clause(ClauseKind.TASTE, "Favoris", listOf(SmartRule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")))) }
        STARS.find(c.text)?.let { m ->
            val n = m.groupValues[2].toIntOrNull() ?: STAR_WORDS[m.groupValues[2]] ?: return@let
            if (n !in 1..5) return@let
            c.consume(m.range)
            val strict = m.groupValues[1].isNotEmpty()
            val threshold = if (strict) n else n - 1
            add(Clause(ClauseKind.TASTE, if (strict) "Plus de $n étoiles" else "$n étoiles et plus", listOf(SmartRule(RuleField.RATING, RuleOperator.GREATER_THAN, "$threshold"))))
        }
        WELL_RATED.find(c.text)?.let { c.consume(it.range); add(Clause(ClauseKind.TASTE, "Bien notés", listOf(SmartRule(RuleField.RATING, RuleOperator.GREATER_THAN, "3")))) }
    }

    private fun order(c: Cursor): Clause? {
        fun clause(m: MatchResult, label: String, sort: SmartSort, descending: Boolean = false): Clause {
            c.consume(m.range)
            return Clause(ClauseKind.ORDER, label, sort = sort, descending = descending)
        }
        ORDER_RISE.find(c.text)?.let { return clause(it, "Du plus calme au plus énergique", SmartSort.ENERGY) }
        ORDER_FALL.find(c.text)?.let { return clause(it, "Du plus énergique au plus calme", SmartSort.ENERGY, descending = true) }
        ORDER_FLOW.find(c.text)?.let { return clause(it, "En Flow", SmartSort.FLOW) }
        ORDER_MOST_PLAYED.find(c.text)?.let { return clause(it, "Les plus écoutés", SmartSort.PLAY_COUNT, descending = true) }
        ORDER_LATEST.find(c.text)?.let { return clause(it, "Derniers ajoutés", SmartSort.DATE_ADDED, descending = true) }
        ORDER_RANDOM.find(c.text)?.let { return clause(it, "Aléatoire", SmartSort.RANDOM) }
        return null
    }

    private fun energy(c: Cursor): List<Clause> = buildList {
        RUN.find(c.text)?.let {
            c.consume(it.range)
            add(Clause(ClauseKind.ENERGY, "Pour courir", listOf(
                SmartRule(RuleField.ENERGY, RuleOperator.GREATER_THAN, "59"),
                SmartRule(RuleField.BPM, RuleOperator.GREATER_THAN, "149"),
                SmartRule(RuleField.BPM, RuleOperator.LESS_THAN, "191"),
            )))
        }
        WORK.find(c.text)?.let {
            c.consume(it.range)
            add(Clause(ClauseKind.ENERGY, "Pour travailler", listOf(
                SmartRule(RuleField.ENERGY, RuleOperator.GREATER_THAN, "29"),
                SmartRule(RuleField.ENERGY, RuleOperator.LESS_THAN, "61"),
            )))
        }
        HIGH_ENERGY.find(c.text)?.let { c.consume(it.range); add(Clause(ClauseKind.ENERGY, "Énergique", listOf(SmartRule(RuleField.ENERGY, RuleOperator.GREATER_THAN, "59")))) }
        LOW_ENERGY.find(c.text)?.let { c.consume(it.range); add(Clause(ClauseKind.ENERGY, "Calme", listOf(SmartRule(RuleField.ENERGY, RuleOperator.LESS_THAN, "36")))) }
    }

    private fun tempo(c: Cursor): Clause? {
        BPM_AROUND.find(c.text)?.let { m ->
            val bpm = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            if (bpm !in BPM_RANGE) return@let
            c.consume(m.range)
            val low = floor(bpm * 0.95).toInt() - 1
            val high = ceil(bpm * 1.05).toInt() + 1
            return Clause(ClauseKind.TEMPO, "$bpm BPM (±5 %)", listOf(
                SmartRule(RuleField.BPM, RuleOperator.GREATER_THAN, "$low"),
                SmartRule(RuleField.BPM, RuleOperator.LESS_THAN, "$high"),
            ))
        }
        BPM_MORE.find(c.text)?.let { m ->
            val bpm = m.groupValues[1].toInt()
            if (bpm !in BPM_RANGE) return@let
            c.consume(m.range)
            return Clause(ClauseKind.TEMPO, "Plus de $bpm BPM", listOf(SmartRule(RuleField.BPM, RuleOperator.GREATER_THAN, "$bpm")))
        }
        BPM_LESS.find(c.text)?.let { m ->
            val bpm = m.groupValues[1].toInt()
            if (bpm !in BPM_RANGE) return@let
            c.consume(m.range)
            return Clause(ClauseKind.TEMPO, "Moins de $bpm BPM", listOf(SmartRule(RuleField.BPM, RuleOperator.LESS_THAN, "$bpm")))
        }
        FAST.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.TEMPO, "Rapide", listOf(SmartRule(RuleField.BPM, RuleOperator.GREATER_THAN, "129"))) }
        SLOW.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.TEMPO, "Lent", listOf(SmartRule(RuleField.BPM, RuleOperator.LESS_THAN, "90"))) }
        return null
    }

    private fun key(c: Cursor): Clause? {
        KEY_FULL.find(c.text)?.let { m ->
            var pc = PITCH.getValue(m.groupValues[1])
            when (m.groupValues[2]) {
                "diese" -> pc = (pc + 1) % 12
                "bemol" -> pc = (pc + 11) % 12
            }
            val minor = m.groupValues[3] == "mineur"
            val name = KeyNames.french(pc + if (minor) 12 else 0)
            c.consume(m.range)
            return Clause(ClauseKind.KEY, name.replaceFirstChar { it.uppercaseChar() }, listOf(SmartRule(RuleField.KEY, RuleOperator.EQUALS, name)))
        }
        KEY_MODE.find(c.text)?.let { m ->
            c.consume(m.range)
            val mode = m.groupValues[1]
            return Clause(ClauseKind.KEY, mode.replaceFirstChar { it.uppercaseChar() }, listOf(SmartRule(RuleField.KEY, RuleOperator.CONTAINS, mode)))
        }
        CAMELOT.find(c.text)?.let { m ->
            val code = m.groupValues[1].uppercase()
            if (KeyNames.parse(code) == null) return@let
            c.consume(m.range)
            return Clause(ClauseKind.KEY, code, listOf(SmartRule(RuleField.KEY, RuleOperator.EQUALS, code)))
        }
        return null
    }

    private fun length(c: Cursor): Clause? {
        SHORT.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.LENGTH, "Morceaux courts", listOf(SmartRule(RuleField.DURATION, RuleOperator.LESS_THAN, "180"))) }
        LONG.find(c.text)?.let { c.consume(it.range); return Clause(ClauseKind.LENGTH, "Morceaux longs", listOf(SmartRule(RuleField.DURATION, RuleOperator.GREATER_THAN, "360"))) }
        return null
    }

    /** « sans rap », « sauf Drake » : un genre connu, sinon un artiste de la bibliothèque, sinon rien. */
    private fun exclusions(c: Cursor, vocabulary: LibraryVocabulary): List<Clause> = buildList {
        for (m in EXCLUSION.findAll(c.text).toList()) {
            val group = m.groups[2] ?: continue
            val words = group.value.trim().split(' ').filter { it.isNotEmpty() }
            // Deux mots puis un seul : « sans chanson francaise », « sans rap ».
            for (n in minOf(words.size, 3) downTo 1) {
                val candidate = words.take(n).joinToString(" ")
                val end = group.range.first + candidate.length - 1
                val family = GENRES.firstOrNull { candidate in it.variants }
                if (family != null) {
                    c.consume(m.range.first..end)
                    add(Clause(ClauseKind.GENRE_EXCLUDED, "Sans ${family.label.lowercase()}", listOf(SmartRule(RuleField.GENRE, RuleOperator.NOT_CONTAINS, candidate))))
                    break
                }
                val artist = bestArtist(candidate, vocabulary)
                if (artist != null) {
                    c.consume(m.range.first..end)
                    add(Clause(ClauseKind.ARTIST_EXCLUDED, "Sans $artist", listOf(SmartRule(RuleField.ARTIST, RuleOperator.NOT_EQUALS, artist))))
                    break
                }
            }
        }
    }

    /** Toutes les familles de genres citées, réunies en une clause : une cible → ET, plusieurs → « l'un de ». */
    private fun genres(c: Cursor, vocabulary: LibraryVocabulary): Clause? {
        val libraryGenres = vocabulary.genres.map { it to it.normalizeForSearch() }
        val labels = mutableListOf<String>()
        val targets = linkedSetOf<String>()
        for (m in GENRE_WORDS.findAll(c.text).toList()) {
            val typed = m.groupValues[1]
            val family = GENRES.first { typed in it.variants }
            c.consume(m.range)
            if (family.label !in labels) labels += family.label
            targets += genreTargets(family, typed, libraryGenres)
        }
        // Un genre de la bibliothèque écrit de travers (« elektro », « chansson francaise »).
        if (libraryGenres.isNotEmpty()) {
            for (run in c.runs()) {
                val words = run.words
                var i = 0
                while (i < words.size) {
                    var matched = false
                    for (n in minOf(words.size - i, 2) downTo 1) {
                        if (n == 1 && words[i].text in STOP_WORDS) break
                        val candidate = words.subList(i, i + n).joinToString(" ") { it.text }
                        val genre = libraryGenres.firstOrNull { candidate.length >= 4 && similarity(it.second, candidate) >= FUZZY_MIN } ?: continue
                        c.consume(words[i].start..words[i + n - 1].end)
                        if (genre.first !in labels) labels += genre.first
                        targets += genre.first
                        i += n
                        matched = true
                        break
                    }
                    if (!matched) i++
                }
            }
        }
        if (targets.isEmpty()) return null
        val rules = targets.map { SmartRule(RuleField.GENRE, RuleOperator.CONTAINS, it) }
        val label = labels.joinToString(" ou ")
        return if (rules.size == 1) Clause(ClauseKind.GENRE, label, rules) else Clause(ClauseKind.GENRE, label, anyOf = rules)
    }

    /**
     * Ce que « rock » doit attraper : les variantes de la famille présentes dans un genre de
     * la bibliothèque (« rock » et « punk » si les deux existent), sinon le genre de la
     * bibliothèque qui ressemble au mot tapé, sinon le mot tapé lui-même.
     */
    private fun genreTargets(family: GenreFamily, typed: String, library: List<Pair<String, String>>): List<String> {
        val present = (listOf(typed) + family.variants).distinct()
            .filter { v -> library.any { it.second.contains(v) } }
        // « hard rock » est déjà couvert par « rock » : on garde les plus courts.
        val minimal = present.filter { v -> present.none { other -> other != v && v.contains(other) } }
        if (minimal.isNotEmpty()) return minimal
        library.firstOrNull { similarity(it.second, typed) >= FUZZY_MIN }?.let { return listOf(it.first) }
        return listOf(typed)
    }

    /**
     * « de Daft Punk », « par Nirvana » ([prepositionOnly]), puis un nom cité tel quel
     * (« stromae en aléatoire ») une fois les genres retirés.
     */
    private fun artists(c: Cursor, vocabulary: LibraryVocabulary, prepositionOnly: Boolean): List<Clause> = buildList {
        if (vocabulary.artists.isEmpty()) return@buildList
        for (run in c.runs()) {
            val words = run.words
            if (words.isEmpty()) continue
            val afterPreposition = words.first().text in ARTIST_PREPOSITIONS
            if (prepositionOnly && !afterPreposition) continue
            val start = if (afterPreposition) 1 else 0
            var i = start
            while (i < words.size) {
                var matched = false
                for (n in minOf(words.size - i, 3) downTo 1) {
                    if (n == 1 && words[i].text in STOP_WORDS) break
                    val candidate = words.subList(i, i + n).joinToString(" ") { it.text }
                    val artist = bestArtist(candidate, vocabulary) ?: continue
                    val from = if (afterPreposition && i == start) run.start else words[i].start
                    c.consume(from..words[i + n - 1].end)
                    add(Clause(ClauseKind.ARTIST, artist, listOf(SmartRule(RuleField.ARTIST, RuleOperator.EQUALS, artist))))
                    i += n
                    matched = true
                    break
                }
                if (!matched) i++
            }
        }
    }

    private fun bestArtist(candidate: String, vocabulary: LibraryVocabulary): String? {
        if (candidate.length < 3) return null
        var best: String? = null
        var bestScore = 0.0
        for (artist in vocabulary.artists) {
            val score = similarity(artist.normalizeForSearch(), candidate)
            if (score > bestScore) { bestScore = score; best = artist }
        }
        return if (bestScore >= FUZZY_MIN) best else null
    }

    /** Similarité 0–1 : 1 − distance de Levenshtein / longueur du plus long. */
    internal fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous; previous = current; current = swap
        }
        return 1.0 - previous[b.length].toDouble() / max(a.length, b.length)
    }

    // --- Texte -----------------------------------------------------------------------------

    private fun normalize(text: String): String = text
        .replace('’', '\'')
        .replace(Regex("(?i)r&b|r'n'b|rnb"), " rnb ")
        .replace("#", " diese ")
        .replace(Regex("(?i)(\\d)\\s*h\\s*(\\d{2})\\b"), "$1h$2")
        .normalizeForSearch()
        .replace(PUNCTUATION, " ")
        .replace(SPACES, " ")
        .trim()

    private class Word(val text: String, val start: Int) {
        val end: Int get() = start + text.length - 1
    }

    private class Run(val start: Int, val words: List<Word>)

    /** Texte en cours d'analyse ; une clause reconnue est remplacée par [GAP] pour ne plus être relue. */
    private class Cursor(initial: String) {
        private val chars = initial.toCharArray()
        val text: String get() = String(chars)

        fun consume(range: IntRange) {
            for (i in range) if (i in chars.indices) chars[i] = GAP
        }

        /** Segments encore libres, découpés en mots avec leur position. */
        fun runs(): List<Run> {
            val runs = mutableListOf<Run>()
            var i = 0
            while (i < chars.size) {
                if (chars[i] == GAP) { i++; continue }
                val start = i
                while (i < chars.size && chars[i] != GAP) i++
                val segment = String(chars, start, i - start)
                val words = mutableListOf<Word>()
                for (m in WORD.findAll(segment)) words += Word(m.value, start + m.range.first)
                if (words.isNotEmpty()) runs += Run(words.first().start, words)
            }
            return runs
        }

        fun remainingWords(): List<String> = runs().flatMap { run -> run.words.map { it.text } }
    }

    private const val GAP = '\u0001'
    private const val MAX_NAME = 40
    private const val MINUTES_PER_SONG = 4.0
    private const val FUZZY_MIN = 0.8
    private const val OLD_UNTIL = 1995
    private val BPM_RANGE = 40..300

    private val SPACES = Regex("\\s+")
    private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s]")
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val LEADING_VERBS = Regex(
        "^(?i)(?:mets[- ]moi|mets|joue[- ]moi|joue|fais[- ]moi|lance|passe[- ]moi|passe|je veux|je voudrais|j'aimerais|une playlist(?: de| avec)?|de la|de l'|du|des|de|d')\\s+"
    )

    // Période : mêmes motifs que la voix 5.9, dans le même ordre (plus « entre … et … » et « vieux »).
    private val BETWEEN = Regex("\\bentre (\\d{4}) et (\\d{4})\\b")
    private val RANGE = Regex("\\bde (\\d{4}) a (\\d{4})\\b")
    private val DECADE_WORD = Regex("(?:\\b(?:les|des|du|de la|de l'|d')\\s+)?\\bannees? (\\d{4}|\\d{2})\\b")
    private val DECADE_SUFFIX = Regex("\\b(\\d{4}|\\d{2})s\\b")
    private val DECADE_NAME = Regex("\\b(sixties|seventies|eighties|nineties)\\b")
    private val DECADES = mapOf("sixties" to 1960..1969, "seventies" to 1970..1979, "eighties" to 1980..1989, "nineties" to 1990..1999)
    private val BEFORE = Regex("\\bavant (\\d{4})\\b")
    private val AFTER = Regex("\\bapres (\\d{4})\\b")
    private val SINCE = Regex("\\bdepuis (\\d{4})\\b")
    private val SINGLE_YEAR = Regex("\\b(?:de|en) (\\d{4})\\b")
    private val RECENT = Regex("\\b(?:recents?|recentes?|nouveautes|nouveaux?|nouvelles?)\\b")
    private val OLD = Regex("\\b(?:vieux|vieilles?|vieilleries|anciens?|anciennes?)\\b")

    private val COUNT = Regex("\\b(\\d{1,3}) (?:titres?|morceaux|chansons|sons|musiques|pistes)\\b")
    private val DURATION = Regex(
        "(?:\\bpour (?:un |le |mon |une |la )?(?:trajet|voyage|route|session|seance|footing|sortie) (?:de |d )?)?" +
            "(?:\\b(\\d{1,3}) ?(?:min|minutes?|mn)\\b|\\b(\\d)h(\\d{2})\\b|\\b(\\d) ?h(?:eures?)?\\b|\\b(une|deux|trois|quatre) heures?( et demie?)?\\b|\\b(?:une )?(demi[- ]heure)\\b)"
    )
    private val HOUR_WORDS = mapOf("une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4)

    private val NEVER_PLAYED = Regex("\\b(?:que je n ai )?jamais (?:ecoutes?|ecoutees?|entendus?|joues?)\\b")
    private val NOT_PLAYED_SINCE = Regex("\\b(?:pas|plus) (?:ecoutes?|ecoutees?|entendus?|joues?) depuis (un an|1 an|12 mois|une annee|six mois|6 mois|longtemps|des mois|un moment|des lustres|trois mois|3 mois)\\b")
    private val FORGOTTEN = Regex("\\boublies?\\b")
    private val KNOWN = Regex("\\bque je connais(?: bien| par coeur)?\\b|\\bconnus?\\b|\\bque j ai deja ecoutes?\\b")
    private val DISCOVERY = Regex("\\b(?:decouvertes?|a decouvrir|decouvrir)\\b")

    private val FAVORITES = Regex("\\b(?:(?:tous |toutes )?(?:mes |les )?(?:titres |chansons |morceaux )?(?:favoris|favorites|preferes|preferees|coups de coeur)|que j ?adore|que j ?aime(?: bien)?)\\b")
    private val STARS = Regex("\\b(plus de |au moins |minimum )?(\\d|une|deux|trois|quatre|cinq) etoiles?\\b")
    private val STAR_WORDS = mapOf("une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5)
    private val WELL_RATED = Regex("\\b(?:les )?(?:bien|mieux|les mieux) notes?\\b")

    private val ORDER_RISE = Regex("\\bdu plus (?:calme|doux|lent) au plus (?:energique|nerveux|rapide|fort)\\b|\\ben montee\\b|\\bcrescendo\\b|\\bqui monte\\b")
    private val ORDER_FALL = Regex("\\bdu plus (?:energique|nerveux|rapide|fort) au plus (?:calme|doux|lent)\\b|\\ben descente\\b|\\bdecrescendo\\b|\\bqui redescend\\b")
    private val ORDER_FLOW = Regex("\\ben (?:mode )?flow\\b|\\bqui s ?enchainent\\b|\\ben enchainement\\b")
    private val ORDER_MOST_PLAYED = Regex("\\b(?:les |mes )?plus (?:ecoutes|joues)\\b")
    private val ORDER_LATEST = Regex("\\b(?:les |mes )?derniers ajoutes\\b|\\brecemment ajoutes\\b|\\bajoutes recemment\\b|\\bderniers ajouts\\b")
    private val ORDER_RANDOM = Regex("\\b(?:en aleatoire|au hasard|en melange|melangee?s?|aleatoire|shuffle)\\b")

    private val RUN = Regex("\\bpour (?:courir|le sport|le running|le footing|la course|m entrainer|s entrainer|le jogging|un jogging)\\b")
    private val WORK = Regex("\\bpour (?:travailler|bosser|me concentrer|se concentrer|la concentration|reviser|etudier|lire|coder)\\b")
    private val HIGH_ENERGY = Regex("\\b(?:energiques?|nerveux|nerveuses?|dansants?|dansantes?|pechus?|pechues?|punchy|qui bougent?|toniques?|peps|pour faire la fete|pour la fete|festifs?|festives?)\\b")
    private val LOW_ENERGY = Regex("\\b(?:calmes?|doux|douces?|tranquilles?|chill|poses?|posees?|relax|zen|apaisants?|apaisantes?|pour dormir|pour s ?endormir|pour me detendre|pour se detendre)\\b")

    private val BPM_AROUND = Regex("\\b(?:(?:autour de|vers|environ|a peu pres|a) (\\d{2,3})(?: bpm)?|(\\d{2,3}) bpm)\\b")
    private val BPM_MORE = Regex("\\b(?:plus de|au dessus de|superieur a) (\\d{2,3})(?: bpm)?\\b")
    private val BPM_LESS = Regex("\\b(?:moins de|en dessous de|inferieur a) (\\d{2,3})(?: bpm)?\\b")
    private val FAST = Regex("\\brapides?\\b|\\bqui va vite\\b|\\bspeed\\b")
    private val SLOW = Regex("\\blents?\\b|\\blentes?\\b")

    private val PITCH = mapOf("do" to 0, "re" to 2, "mi" to 4, "fa" to 5, "sol" to 7, "la" to 9, "si" to 11)
    private val KEY_FULL = Regex("\\ben (do|re|mi|fa|sol|la|si)(?: (diese|bemol))? (mineur|majeur)\\b")
    private val KEY_MODE = Regex("\\ben (mineur|majeur)\\b")
    private val CAMELOT = Regex("\\b((?:1[0-2]|[1-9])[ab])\\b")

    private val SHORT = Regex("\\b(?:morceaux |titres |chansons |sons )?courts?\\b|\\bcourtes?\\b")
    private val LONG = Regex("\\b(?:morceaux |titres |chansons |sons )?longs?\\b|\\blongues?\\b")

    private val EXCLUSION = Regex("\\b(sans|sauf|hors|excepte|pas de|pas d) ((?:[\\p{L}\\p{N}]+ ?){1,3})")
    private val ARTIST_PREPOSITIONS = setOf("de", "par", "du", "d")

    private class GenreFamily(val label: String, val variants: List<String>)

    private val GENRES = listOf(
        GenreFamily("Rock", listOf("rock", "hard rock", "rock n roll", "punk", "grunge", "indie", "garage")),
        GenreFamily("Pop", listOf("pop", "synthpop", "k pop", "kpop", "britpop")),
        GenreFamily("Rap", listOf("rap", "hip hop", "hiphop", "trap", "drill")),
        GenreFamily("Électro", listOf("electro", "electronique", "electronic", "electronica", "techno", "house", "edm", "dance", "trance", "dubstep", "drum and bass")),
        GenreFamily("Jazz", listOf("jazz", "swing", "bebop")),
        GenreFamily("Classique", listOf("classique", "classical", "baroque", "opera", "symphonique")),
        GenreFamily("Métal", listOf("metal", "heavy metal", "hardcore", "death metal", "black metal")),
        GenreFamily("Chanson française", listOf("chanson francaise", "chanson", "variete francaise", "variete")),
        GenreFamily("Reggae", listOf("reggae", "dub", "ska", "dancehall")),
        GenreFamily("Soul / funk", listOf("soul", "funk", "motown", "disco")),
        GenreFamily("R&B", listOf("rnb", "rhythm and blues")),
        GenreFamily("Folk", listOf("folk", "acoustique", "acoustic")),
        GenreFamily("Country", listOf("country", "bluegrass")),
        GenreFamily("Blues", listOf("blues")),
        GenreFamily("Latino", listOf("latino", "latin", "latina", "salsa", "reggaeton", "bachata", "cumbia")),
        GenreFamily("Lo-fi", listOf("lo fi", "lofi")),
        GenreFamily("Ambient", listOf("ambient", "ambiant", "new age")),
        GenreFamily("World", listOf("world", "musique du monde", "afro", "afrobeat")),
    )
    private val GENRE_WORDS = Regex(
        "\\b(" + GENRES.flatMap { it.variants }.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")\\b"
    )

    private val STOP_WORDS = setOf(
        "de", "du", "des", "la", "le", "les", "un", "une", "et", "en", "pour", "avec", "que", "qui", "je", "me", "moi",
        "mets", "joue", "fais", "d", "l", "s", "j", "n", "ai", "a", "au", "aux", "ou", "ma", "mes", "mon", "ta", "tes", "ton",
        "lance", "ecoute", "passe", "veux", "voudrais", "aimerais", "playlist", "musique", "musiques", "titres", "titre",
        "morceaux", "morceau", "chansons", "chanson", "sons", "son", "trucs", "truc", "quelque", "chose", "plutot", "genre",
        "style", "ambiance", "tout", "toute", "tous", "il", "y", "sans", "sauf", "hors", "pas", "ne", "nous", "on", "ce", "ca", "sa",
    )
}
