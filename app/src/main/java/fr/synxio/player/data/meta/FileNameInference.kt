package fr.synxio.player.data.meta

import fr.synxio.player.data.media.tidyTag

/** Ce que l'on devine d'un morceau sans tags, et l'autre lecture possible quand l'ordre est douteux. */
data class Inference(
    val metadata: TrackMetadata,
    /**
     * « Deuxième option - Coralie Fischer » : rien ne dit à coup sûr lequel des deux est
     * l'artiste. On garde l'ordre inverse pour que la recherche en ligne tranche.
     */
    val alternative: TrackMetadata? = null,
)

/**
 * Déduit titre, artiste, album, piste et disque du nom de fichier et des dossiers.
 *
 * Dernier recours local, pensé pour les fichiers sans tags : vidéos YouTube converties
 * (« Laura - Plus de problèmes (Clip officiel).mp3 »), rips numérotés
 * (« 03 - Titre.flac » dans « Artiste/Album/CD2 »), fichiers de sites de conversion.
 *
 * Fonction pure : aucune E/S, pour être testée sur la JVM.
 */
object FileNameInference {

    /** Séparateurs « Artiste - Titre », du plus au moins courant. « _ » remplace « : » chez yt-dlp. */
    val SEPARATORS = listOf(" - ", " – ", " — ", " _ ", " : ", " | ")

    /**
     * @param path chemin absolu du fichier.
     * @param tagTitle titre lu dans les tags, souvent plus fidèle que le nom de fichier
     *   (accents, deux-points) ; analysé à la place du nom quand il contient « Artiste - Titre ».
     * @param isKnownArtist vrai si la bibliothèque connaît déjà cet artiste : c'est l'indice
     *   le plus sûr pour savoir de quel côté du tiret il se trouve.
     */
    fun infer(
        path: String,
        tagTitle: String? = null,
        isKnownArtist: (String) -> Boolean = { false },
    ): Inference {
        val fileName = path.substringAfterLast('/')
        val baseName = fileName.substringBeforeLast('.', fileName)

        val numbered = parseLeadingNumber(baseName)
        val fromFolders = inferFromFolders(path)

        // Le titre des tags est préféré dès qu'il porte lui-même « Artiste - Titre ».
        val tagCandidate = tagTitle?.tidyTag()?.takeIf { TagSanity.isValidTitle(it) }
        val source = when {
            tagCandidate != null && (hasSeparator(tagCandidate) || QUOTED.containsMatchIn(tagCandidate)) -> tagCandidate
            else -> numbered.rest
        }
        val parsed = parseArtistTitle(source, isKnownArtist)
        val title = parsed.title ?: tagCandidate ?: cleanTitle(numbered.rest).takeIf { it.isNotBlank() }

        val base = TrackMetadata(
            title = title,
            artist = parsed.artist ?: fromFolders.artist,
            album = fromFolders.album,
            albumArtist = fromFolders.artist,
            year = fromFolders.year,
            track = numbered.track,
            disc = numbered.disc ?: fromFolders.disc,
        )
        val alternative = if (parsed.ambiguous && parsed.artist != null && parsed.title != null) {
            base.copy(title = parsed.artist, artist = parsed.title)
        } else null
        return Inference(base, alternative)
    }

    // --- Nom de fichier ---------------------------------------------------------------

    internal data class Numbered(val rest: String, val track: Int?, val disc: Int?)

    /** « 1-03 Titre », « 03 - Titre », « 03. Titre », « 03 Titre » ; jamais « 50 Cent - … ». */
    internal fun parseLeadingNumber(name: String): Numbered {
        DISC_TRACK.find(name)?.let { m ->
            return Numbered(name.substring(m.range.last + 1), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        TRACK_WITH_SEPARATOR.find(name)?.let { m ->
            return Numbered(name.substring(m.range.last + 1), m.groupValues[1].toInt(), null)
        }
        ZERO_PADDED_TRACK.find(name)?.let { m ->
            return Numbered(name.substring(m.range.last + 1), m.groupValues[1].toInt(), null)
        }
        return Numbered(name, null, null)
    }

    internal data class Parsed(val artist: String?, val title: String?, val ambiguous: Boolean)

    internal fun parseArtistTitle(raw: String, isKnownArtist: (String) -> Boolean = { false }): Parsed {
        var text = stripSitePrefix(raw.tidyTag())
        if (text.isBlank() || GARBAGE.matches(text)) return Parsed(null, null, false)

        // Liane Foly "On a tous le droit"
        QUOTED.find(text)?.let { m ->
            val artist = m.groupValues[1].trim()
            val title = cleanTitle(m.groupValues[2])
            if (artist.isNotBlank() && title.isNotBlank()) return Parsed(artist, title, false)
        }

        text = YOUTUBE_ID.replace(text, "")
        // Un double espace marque un caractère retiré (« | ») : la suite est un sous-titre
        // de vidéo (« Chanson Française », « Archive INA »), pas une partie du titre.
        text = text.split(DOUBLE_SPACE).firstOrNull { it.isNotBlank() }?.trim() ?: text

        // « Take Me Home, Country Roads by John Denver »
        BY.find(text)?.let { m ->
            val title = cleanTitle(m.groupValues[1])
            val artist = cleanSegment(m.groupValues[2])
            if (title.isNotBlank() && artist.isNotBlank()) return Parsed(artist, title, false)
        }

        val separator = SEPARATORS.firstOrNull { text.contains(it) }
            ?: return Parsed(null, cleanTitle(text).takeIf { it.isNotBlank() }, false)

        val segments = text.split(separator)
            .map { cleanSegment(it) }
            .filter { it.isNotBlank() && !NOISE_ONLY.matches(it) }
        if (segments.size < 2) return Parsed(null, segments.firstOrNull()?.let(::cleanTitle), false)

        var left = segments.first()
        var right = segments.drop(1).joinToString(separator)

        // Une précision entre parenthèses suit le titre, quel que soit le côté où elle tombe.
        val leftExtra = TRAILING_PAREN.find(left)?.value.orEmpty()
        val rightExtra = TRAILING_PAREN.find(right)?.value.orEmpty()
        left = left.removeSuffix(leftExtra).trim()
        right = right.removeSuffix(rightExtra).trim()

        val leftScore = artistScore(left, isKnownArtist)
        val rightScore = artistScore(right, isKnownArtist)
        val rightIsArtist = rightScore > leftScore
        val (artist, titleCore) = if (rightIsArtist) right to left else left to right
        val title = cleanTitle(listOf(titleCore, leftExtra.trim(), rightExtra.trim()).filter { it.isNotBlank() }.joinToString(" "))

        val strongSignal = maxOf(leftScore, rightScore) >= STRONG_SCORE
        val ambiguous = !strongSignal && kotlin.math.abs(leftScore - rightScore) < 2
        return Parsed(artist.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() }, ambiguous)
    }

    /**
     * Plus le score est haut, plus le segment ressemble à un nom d'artiste.
     *
     * Un artiste connu de la bibliothèque l'emporte toujours ; une collaboration
     * (« feat. », « & ») est presque toujours du côté de l'artiste ; un segment en casse de
     * phrase (« Plus de problèmes ») est presque toujours le titre.
     */
    internal fun artistScore(segment: String, isKnownArtist: (String) -> Boolean): Int {
        var score = 0
        val names = segment.split(COLLAB_SPLIT).map { it.trim() }.filter { it.isNotBlank() }
        if (isKnownArtist(segment) || names.any(isKnownArtist)) score += 5
        if (COLLAB_MARKER.containsMatchIn(segment)) score += 2
        val words = segment.split(' ').filter { it.isNotBlank() }
        val meaningful = words.filter { it.lowercase().trim(',', '.') !in CONNECTORS }
        if (meaningful.isNotEmpty()) {
            score += if (meaningful.all { w -> w.first().let { it.isUpperCase() || it.isDigit() } }) 1 else -1
        }
        return score
    }

    private fun hasSeparator(text: String) = SEPARATORS.any { text.contains(it) }

    private fun stripSitePrefix(text: String): String = SITE_PREFIX.replace(text, "").trim()

    /** Retire le bruit d'un segment : « (Clip officiel) », « Parole FRPower ». */
    private fun cleanSegment(segment: String): String = segment
        .replace(NOISE_GROUP, " ")
        .replace(TRAILING_NOISE, "")
        .replace(MULTISPACE, " ")
        .trim(' ', '-', '_', '|', ':')

    internal fun cleanTitle(text: String): String = cleanSegment(
        text.replace(YOUTUBE_ID, "")
            .let { if (!it.contains(' ') && it.contains('_')) it.replace('_', ' ') else it }
    )

    // --- Dossiers ---------------------------------------------------------------------

    internal data class FolderGuess(val album: String?, val artist: String?, val year: Int?, val disc: Int?)

    /**
     * « …/Artiste/Album/CD2/03 - Titre.mp3 ». Les dossiers fourre-tout (« Download »,
     * « Son v2 », tout dossier à la racine du stockage) ne disent rien de l'album.
     */
    internal fun inferFromFolders(path: String): FolderGuess {
        val root = STORAGE_ROOT.find(path)?.value.orEmpty()
        val dirs = path.removePrefix(root).trim('/').split('/').dropLast(1).filter { it.isNotBlank() }
        if (dirs.isEmpty()) return FolderGuess(null, null, null, null)

        var albumIndex = dirs.lastIndex
        var disc: Int? = null
        DISC_FOLDER.matchEntire(dirs[albumIndex].trim())?.let { m ->
            disc = m.groupValues[1].toInt()
            albumIndex--
        }
        // Un dossier d'album à la racine du stockage est en fait un fourre-tout.
        if (albumIndex < 1 || isGeneric(dirs[albumIndex])) return FolderGuess(null, null, null, disc)

        val rawAlbum = dirs[albumIndex]
        val year = BRACKETED_YEAR.find(rawAlbum)?.groupValues?.get(1)?.toInt()
        var album = cleanFolder(rawAlbum)
        var artist: String? = null

        // Convention « Artiste - Album » des dossiers de rips.
        val separator = SEPARATORS.firstOrNull { album.contains(it) }
        if (separator != null) {
            artist = album.substringBefore(separator).trim().takeIf { it.isNotBlank() }
            album = album.substringAfter(separator).trim()
        }
        if (artist == null && albumIndex >= 2 && !isGeneric(dirs[albumIndex - 1])) {
            artist = cleanFolder(dirs[albumIndex - 1]).takeIf { it.isNotBlank() }
        }
        return FolderGuess(album.takeIf { it.isNotBlank() }, artist, year, disc)
    }

    internal fun isGeneric(folder: String): Boolean {
        val name = folder.trim().lowercase()
        return name in GENERIC_FOLDERS || name.startsWith(".") || GENERIC_PATTERN.matches(name)
    }

    private fun cleanFolder(name: String): String = name
        .tidyTag()
        .replace(BRACKETED, " ")
        .replace(RELEASE_NOISE, " ")
        .replace(RELEASE_GROUP, "")
        .replace(MULTISPACE, " ")
        .trim(' ', '-', '_')

    // --- Motifs -----------------------------------------------------------------------

    private const val STRONG_SCORE = 3

    private val MULTISPACE = Regex("\\s{2,}")
    private val DOUBLE_SPACE = Regex("\\s{2,}")
    private val DISC_TRACK = Regex("^\\s*(\\d)-(\\d{2})\\s+")
    private val TRACK_WITH_SEPARATOR = Regex("^\\s*(\\d{1,3})\\s*[.\\-_)]\\s+")
    private val ZERO_PADDED_TRACK = Regex("^\\s*(0\\d)\\s+")
    private val QUOTED = Regex("^(.+?)\\s+[\"«“](.+?)[\"»”]\\s*$")
    private val BY = Regex("^(.+?)\\s+by\\s+(.+)$")
    private val YOUTUBE_ID = Regex("\\s*\\[[A-Za-z0-9_-]{11}]")
    private val TRAILING_PAREN = Regex("\\s*\\([^()]*\\)\\s*$")
    private val COLLAB_MARKER = Regex("(?i)\\b(?:feat\\.?|ft\\.?|featuring)\\s|\\s&\\s|\\s[xX]\\s|,\\s")
    private val COLLAB_SPLIT = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|&|x)\\s+|,\\s*")
    private val SITE_PREFIX = Regex(
        "^(?:y2mate\\.(?:com|is)|yt1s\\.com|ytmp3\\.cc|ssstik\\.io|snaptik\\.app|savefrom\\.net|mp3juices\\.cc|" +
            "notube\\.\\w+|onlymp3\\.\\w+)\\s*[-_ ]\\s*",
        RegexOption.IGNORE_CASE,
    )
    /** Restes sans information : horodatage d'un site de conversion, nom généré. */
    private val GARBAGE = Regex("^(?:\\d+|[0-9a-f]{8,}|gemini_generated_media_[0-9a-f]+|(?:audio|aud|rec|vid)[-_ ]?\\d+)$", RegexOption.IGNORE_CASE)

    private val NOISE_WORDS =
        "officiel(?:le)?|official|lyrics?|paroles?|clip|vid[ée]o|audio|visuali[sz]e[rd]?|" +
            "visualiseur|hd|hq|4k|mv|m/v|music\\s+video|lyric\\s+video|karaok[ée]"
    private val NOISE_GROUP = Regex("[(\\[][^()\\[\\]]*\\b(?:$NOISE_WORDS)\\b[^()\\[\\]]*[)\\]]", RegexOption.IGNORE_CASE)
    private val TRAILING_NOISE = Regex(
        "(?<=[\\p{L}\\d)])\\s+(?:(?:paroles?|lyrics?)(?:\\s+\\S+)?|clip(?:\\s+officiel)?|" +
            "official\\s+(?:music\\s+)?video|audio\\s+officiel|lyrics?\\s+video)$",
        RegexOption.IGNORE_CASE,
    )

    /** Segment qui n'est que du bruit : « … - Parole », « … - Clip officiel ». */
    private val NOISE_ONLY = Regex(
        "^(?:paroles?|lyrics?|clip(?:\\s+officiel)?|audio|vid[ée]o|hd|hq|official(?:\\s+(?:music\\s+)?video)?)$",
        RegexOption.IGNORE_CASE,
    )

    private val CONNECTORS = setOf(
        "et", "and", "&", "de", "du", "des", "la", "le", "les", "l'", "d'", "the", "of", "x", "y", "e",
        "à", "a", "au", "aux", "en", "un", "une", "feat", "feat.", "ft", "ft.", "vs", "vs.", "-",
    )

    private val STORAGE_ROOT = Regex("^/storage/(?:emulated/\\d+|[^/]+)")
    private val DISC_FOLDER = Regex("^(?:cd|disc|disque|disk|dvd)\\s*(\\d{1,2})$", RegexOption.IGNORE_CASE)
    private val BRACKETED_YEAR = Regex("[(\\[]((?:19|20)\\d{2})[)\\]]")
    private val BRACKETED = Regex("[(\\[][^()\\[\\]]*[)\\]]")
    private val RELEASE_NOISE = Regex(
        "(?i)\\b(?:flac|mp3|aac|alac|wav|320\\s*kbps|\\d{3}\\s*kbps|v0|hi[- ]?res|24\\s*bit|16\\s*bit|web(?:-dl)?)\\b",
    )
    /** Signature de groupe de release en fin de nom : « -G11DF3 ». */
    private val RELEASE_GROUP = Regex("-(?=[A-Z0-9]*\\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{2,10}$")
    private val GENERIC_FOLDERS = setOf(
        "music", "musique", "musiques", "musics", "download", "downloads", "téléchargements",
        "telechargements", "son", "sons", "audio", "audios", "mp3", "songs", "chansons", "synxio",
        "bluetooth", "media", "ringtones", "notifications", "alarms", "misc", "divers", "various",
        "playlist", "playlists", "tracks", "samsung", "recordings", "podcasts", "documents", "pre-loaded",
        "sdcard", "0", "emulated", "telegram", "whatsapp", "snaptube", "vidmate", "youtube",
    )
    /** « Son v2 », « Musique 2024 », « Music (old) ». */
    private val GENERIC_PATTERN = Regex("^(?:sons?|musiques?|music|mp3|audio|chansons?)\\s*(?:v?\\d+|\\(.*\\))?$")
}
