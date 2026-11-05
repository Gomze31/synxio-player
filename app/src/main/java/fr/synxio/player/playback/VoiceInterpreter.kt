package fr.synxio.player.playback

/**
 * Ce qu'une phrase dictée demande : une action, un sujet si la phrase le dit, un nom,
 * une période, un mélange. [text] est le reste de la phrase sans verbe ni mélange,
 * période comprise : une playlist nommée « Années 80 » doit pouvoir y correspondre.
 */
data class VoiceIntent(
    val action: Action,
    val subject: Subject?,
    val name: String?,
    val years: IntRange?,
    val shuffle: Boolean,
    val text: String?,
) {
    enum class Action { PLAY, SHUFFLE, RESUME_BOOK, RESUME_LAST }
    enum class Subject { ARTIST, ALBUM, PLAYLIST, GENRE, TITLE, RADIO, MIX, FAVORITES, MEMORY }
}

/**
 * Grammaire par motifs des demandes vocales en français, sur une requête déjà passée
 * par `normalizeForSearch` (minuscules, sans accents). Pur : ni Android, ni bibliothèque.
 *
 * Ordre : verbes, mélange, reprise, favoris, souvenirs, mixs, radio, période, déterminants.
 * Le focus de l'Assistant, s'il est là, fixe le sujet et le nom ; la grammaire ne lui
 * ajoute que la période et le mélange.
 */
object VoiceInterpreter {

    fun interpret(query: String, request: VoiceRequest, currentYear: Int): VoiceIntent {
        var text = query.replace('’', '\'').trim().replace(SPACES, " ")
        text = text.replace(VERBS, "")

        val shuffle = SHUFFLE.containsMatchIn(text)
        text = text.replace(SHUFFLE, " ").trim().replace(SPACES, " ")
        if (text in FILLERS) text = ""

        val focused = focusedSubject(request)
        if (focused == null) {
            if (RESUME_LAST.matches(text)) return VoiceIntent(VoiceIntent.Action.RESUME_LAST, null, null, null, shuffle, null)
            if (RESUME_BOOK.matches(text)) return VoiceIntent(VoiceIntent.Action.RESUME_BOOK, null, null, null, shuffle, null)
            if (FAVORITES.matches(text)) return VoiceIntent(VoiceIntent.Action.PLAY, VoiceIntent.Subject.FAVORITES, null, null, shuffle, null)
            if (MEMORY.matches(text)) return VoiceIntent(VoiceIntent.Action.PLAY, VoiceIntent.Subject.MEMORY, null, null, shuffle, null)
            if (MIX_DAILY.matches(text)) return VoiceIntent(VoiceIntent.Action.PLAY, VoiceIntent.Subject.MIX, "daily", null, shuffle, null)
            if (MIX_DISCOVERY.matches(text)) return VoiceIntent(VoiceIntent.Action.PLAY, VoiceIntent.Subject.MIX, "discovery", null, shuffle, null)
            MIX_OF.matchEntire(text)?.let { return VoiceIntent(VoiceIntent.Action.PLAY, VoiceIntent.Subject.MIX, it.groupValues[1].trim(), null, shuffle, null) }
            (RADIO_BEFORE.matchEntire(text) ?: RADIO_AFTER.matchEntire(text))?.let {
                return VoiceIntent(VoiceIntent.Action.PLAY, VoiceIntent.Subject.RADIO, it.groupValues[1].trim(), null, shuffle, null)
            }
        }

        // Le reste, déterminant de tête retiré : c'est le texte que l'appelant compare aux playlists.
        val remainder = stripLeadingDeterminer(text)
        val (years, afterPeriod) = period(remainder, currentYear)
        var name: String? = stripLeadingDeterminer(afterPeriod.replace(TRAILING_DETERMINER, "").trim()).trim()
        if (name.isNullOrEmpty() || name in FILLERS) name = null

        if (focused != null) {
            val (subject, focusName) = focused
            return VoiceIntent(VoiceIntent.Action.PLAY, subject, focusName ?: name, years, shuffle, remainder.ifEmpty { null })
        }
        val action = if (shuffle && name == null && years == null) VoiceIntent.Action.SHUFFLE else VoiceIntent.Action.PLAY
        return VoiceIntent(action, null, name, years, shuffle, remainder.ifEmpty { null })
    }

    private fun focusedSubject(request: VoiceRequest): Pair<VoiceIntent.Subject, String?>? = when (request.focus) {
        VoiceRequest.FOCUS_ARTIST -> VoiceIntent.Subject.ARTIST to request.artist
        VoiceRequest.FOCUS_ALBUM -> VoiceIntent.Subject.ALBUM to request.album
        VoiceRequest.FOCUS_GENRE -> VoiceIntent.Subject.GENRE to request.genre
        VoiceRequest.FOCUS_PLAYLIST -> VoiceIntent.Subject.PLAYLIST to request.playlist
        VoiceRequest.FOCUS_TITLE -> VoiceIntent.Subject.TITLE to request.title
        else -> null
    }

    /** Période trouvée dans [text], et le texte sans elle. */
    private fun period(text: String, currentYear: Int): Pair<IntRange?, String> {
        fun decade(raw: String): IntRange {
            val n = raw.toInt()
            val start = when {
                n >= 1000 -> n / 10 * 10
                n >= 30 -> 1900 + n / 10 * 10
                else -> 2000 + n / 10 * 10
            }
            return start..start + 9
        }
        fun cut(m: MatchResult) = (text.substring(0, m.range.first) + " " + text.substring(m.range.last + 1)).trim().replace(SPACES, " ")

        RANGE.find(text)?.let { return it.groupValues[1].toInt()..it.groupValues[2].toInt() to cut(it) }
        DECADE_WORD.find(text)?.let { return decade(it.groupValues[1]) to cut(it) }
        DECADE_SUFFIX.find(text)?.let { return decade(it.groupValues[1]) to cut(it) }
        DECADE_NAME.find(text)?.let { return DECADES.getValue(it.groupValues[1]) to cut(it) }
        BEFORE.find(text)?.let { return 1900..it.groupValues[1].toInt() - 1 to cut(it) }
        AFTER.find(text)?.let { return it.groupValues[1].toInt() + 1..currentYear to cut(it) }
        SINCE.find(text)?.let { return it.groupValues[1].toInt()..currentYear to cut(it) }
        SINGLE_YEAR.find(text)?.let { return it.groupValues[1].toInt()..it.groupValues[1].toInt() to cut(it) }
        RECENT.find(text)?.let { return currentYear - 2..currentYear to cut(it) }
        return null to text
    }

    /** Retire « du », « de la », « un peu de »… devant un mot ; jamais devant un nombre (« de 2015 »). */
    private fun stripLeadingDeterminer(text: String): String = text.replace(LEADING_DETERMINER, "").trim()

    private val SPACES = Regex("\\s+")
    private val VERBS = Regex(
        "^(?:mets|met|joue|jouer|lance|lancer|ecoute|ecouter|passe|fais(?:[\\s-]+moi)?[\\s-]+ecouter|je veux ecouter|je voudrais ecouter|tu peux (?:me )?(?:mettre|jouer|lancer))(?:[\\s-]+(?:moi|nous))?\\s+"
    )
    private val SHUFFLE = Regex("\\b(?:en aleatoire|au hasard|en melange|melangee?s?|aleatoire|shuffle)\\b")
    private val FILLERS = setOf("musique", "de la musique", "lecture", "quelque chose", "un truc", "des titres", "des chansons", "un morceau", "des morceaux")

    private val RESUME_LAST = Regex(
        "(?:(?:reprends?|reprendre|continue|remets?)\\s+)?(?:ce que j'?ecoutais|ce que j'?etais en train d'?ecouter|ma derniere file|la derniere file|ma file(?: d'?attente)?|la file(?: d'?attente)?)"
    )
    private val RESUME_BOOK = Regex(
        "(?:continue|reprends?|reprendre|remets?)(?:\\s+(?:mon livre(?: audio)?|mon podcast|mon bouquin|la lecture|la ou j'?en etais|ou j'?en etais))?" +
            "|mon livre(?: audio)?|mon podcast|la ou j'?en etais|ou j'?en etais"
    )
    private val FAVORITES = Regex("(?:tous mes |mes |les )?(?:titres |chansons |morceaux )?(?:favoris|favorites|preferes|preferees|coups de coeur)")
    private val MEMORY = Regex("(?:mes )?souvenirs(?: musicaux)?|ce jour[- ]la|il y a (?:un|deux) ans?")
    private val MIX_DAILY = Regex("(?:mon |le |ton )?mix du jour|mon mix")
    private val MIX_DISCOVERY = Regex("(?:ma |la |ta )?decouverte du jour")
    private val MIX_OF = Regex("(?:le |un |mon )mix (?:de |d'|du |des )?(.+)")
    private val RADIO_BEFORE = Regex("(?:la |une )?(?:web)?radio (.+)")
    private val RADIO_AFTER = Regex("(.+) en (?:web)?radio")

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

    private val LEADING_DETERMINER = Regex("^(?:un peu de |un peu d'|du |de la |de l'|des |de |d'|le |la |les |l'|un |une )(?=[a-z])")
    private val TRAILING_DETERMINER = Regex("\\s+(?:du|de la|de l'|des|de|d')$")
}
