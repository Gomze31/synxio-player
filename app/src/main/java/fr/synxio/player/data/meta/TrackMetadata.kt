package fr.synxio.player.data.meta

import fr.synxio.player.data.media.tidyTag
import java.util.Calendar

/** D'où vient une valeur affichée. L'ordre est celui de la cascade. */
enum class MetaSource(val label: String) {
    FILE_TAGS("tags du fichier"),
    MEDIASTORE("index Android"),
    INFERRED("nom du fichier"),
    ONLINE("MusicBrainz"),
}

/** D'où vient la pochette affichée. L'ordre est celui de la cascade. */
enum class ArtSource(val label: String) {
    EMBEDDED("intégrée au fichier"),
    FOLDER("image du dossier"),
    MEDIASTORE("index Android"),
    ONLINE("Cover Art Archive"),
    NONE("aucune"),
}

/**
 * Métadonnées d'un morceau telles qu'une source les connaît. `null` = la source ne sait pas.
 *
 * Les valeurs ne sont pas encore validées : c'est [MetadataCascade] qui écarte les
 * « <unknown> », « Piste 12 » et autres remplissages avant de les retenir.
 */
data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val track: Int? = null,
    val disc: Int? = null,
    val durationMs: Long? = null,
) {
    companion object {
        val EMPTY = TrackMetadata()
    }
}

/** Champs de [TrackMetadata], pour savoir quelle source a fourni quoi. */
enum class MetaField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, YEAR, GENRE, TRACK, DISC, DURATION }

/** Une couche de la cascade : ce qu'une source sait du morceau. */
data class MetaLayer(val source: MetaSource, val data: TrackMetadata)

/** Résultat de la cascade : une valeur par champ, et la source de chacune. */
data class ResolvedMetadata(
    val data: TrackMetadata,
    val sources: Map<MetaField, MetaSource>,
) {
    fun sourceOf(field: MetaField): MetaSource? = sources[field]

    /** Vrai si ce champ n'est connu que par déduction : une source en ligne peut mieux faire. */
    fun isGuess(field: MetaField): Boolean = sources[field] == null || sources[field] == MetaSource.INFERRED
}

/**
 * Fusionne les sources de métadonnées, champ par champ, dans l'ordre de priorité.
 *
 * Pour chaque champ, la première valeur *valable* l'emporte : les remplissages que posent
 * les outils de rip ou Android lui-même (« <unknown> », « Piste 12 », année 0, genre
 * « (13) ») sont ignorés, et la source suivante a sa chance. C'est ce qui manquait : une
 * valeur vide ou bidon en tête suffisait à afficher « Artiste inconnu ».
 *
 * Une exception à l'ordre : la recherche en ligne passe après la déduction depuis le nom
 * de fichier, mais une correspondance en ligne jugée sûre remplace une simple déduction,
 * sauf pour le titre
 * (l'ordre « Titre - Artiste » ou « Artiste - Titre » d'un nom de fichier n'est qu'une
 * supposition). Elle ne remplace jamais une valeur lue dans le fichier ou dans MediaStore.
 */
object MetadataCascade {

    fun resolve(layers: List<MetaLayer>): ResolvedMetadata {
        val ordered = layers.sortedBy { it.source.ordinal }
        val sources = HashMap<MetaField, MetaSource>()

        fun <T : Any> pick(
            field: MetaField,
            read: (TrackMetadata) -> T?,
            valid: (T) -> Boolean,
            onlineBeatsGuess: Boolean = true,
        ): T? {
            var guess: Pair<T, MetaSource>? = null
            for (layer in ordered) {
                val value = read(layer.data)?.takeIf(valid) ?: continue
                // Une déduction est retenue en réserve : une fiche en ligne peut la battre.
                if (layer.source == MetaSource.INFERRED) {
                    if (guess == null) guess = value to layer.source
                } else if (layer.source == MetaSource.ONLINE && !onlineBeatsGuess && guess != null) {
                    continue
                } else {
                    sources[field] = layer.source
                    return value
                }
            }
            return guess?.let { (value, source) -> sources[field] = source; value }
        }

        val data = TrackMetadata(
            // Le titre en ligne ne remplace pas celui du nom de fichier : la fiche trouvée
            // peut être un remix ou une autre version (« … (R3hab remix) » pour une reprise
            // au piano), et le nom de fichier dit bien ce que l'on écoute.
            title = pick(MetaField.TITLE, { it.title?.cleanText() }, TagSanity::isValidTitle, onlineBeatsGuess = false),
            artist = pick(MetaField.ARTIST, { it.artist?.cleanArtist() }, TagSanity::isValidArtist),
            album = pick(MetaField.ALBUM, { it.album?.cleanText() }, TagSanity::isValidAlbum),
            albumArtist = pick(MetaField.ALBUM_ARTIST, { it.albumArtist?.cleanArtist() }, TagSanity::isValidArtist),
            year = pick(MetaField.YEAR, { it.year }, TagSanity::isValidYear),
            genre = pick(MetaField.GENRE, { it.genre?.cleanText() }, TagSanity::isValidGenre),
            track = pick(MetaField.TRACK, { it.track }, { it in 1..999 }),
            disc = pick(MetaField.DISC, { it.disc }, { it in 1..99 }),
            durationMs = pick(MetaField.DURATION, { it.durationMs }, { it > 0 }),
        )
        return ResolvedMetadata(data.withInferredTitleWhenArtistGuessed(ordered, sources).withoutArtistPrefix(), sources)
    }

    /**
     * Tag titre « Liane Foly "On a tous le droit" », sans tag artiste : l'artiste a été déduit
     * de ce titre même. Le titre affiché doit alors être la partie titre de la déduction, et
     * non le tag entier qui répète l'artiste.
     */
    private fun TrackMetadata.withInferredTitleWhenArtistGuessed(
        layers: List<MetaLayer>,
        sources: MutableMap<MetaField, MetaSource>,
    ): TrackMetadata {
        if (sources[MetaField.ARTIST] != MetaSource.INFERRED) return this
        val t = title ?: return this
        val a = artist ?: return this
        val inferredTitle = layers.firstOrNull { it.source == MetaSource.INFERRED }?.data?.title
            ?.cleanText()?.takeIf(TagSanity::isValidTitle) ?: return this
        if (!t.contains(a, ignoreCase = true) || inferredTitle.equals(t, ignoreCase = true)) return this
        sources[MetaField.TITLE] = MetaSource.INFERRED
        return copy(title = inferredTitle)
    }

    /**
     * « Yu_Gen - Elle danse » d'artiste « Yu_Gen » : le titre répète l'artiste, héritage
     * d'un nom de vidéo. On n'affiche que « Elle danse ».
     */
    private fun TrackMetadata.withoutArtistPrefix(): TrackMetadata {
        val t = title ?: return this
        val a = artist ?: return this
        for (separator in FileNameInference.SEPARATORS) {
            val prefix = a + separator
            if (t.length > prefix.length && t.startsWith(prefix, ignoreCase = true)) {
                return copy(title = t.substring(prefix.length).trim())
            }
        }
        return this
    }

    private fun String.cleanText(): String = tidyTag().replace(MULTISPACE, " ")

    /**
     * « Angèle - Topic », « Zaho Officiel », « AngeleVEVO » : noms de chaînes YouTube que
     * yt-dlp recopie dans le tag artiste. Seul le nom de l'artiste est gardé.
     */
    private fun String.cleanArtist(): String {
        val cleaned = cleanText()
            .replace(TOPIC_SUFFIX, "")
            .replace(OFFICIAL_SUFFIX, "")
            .replace(VEVO_SUFFIX, "$1")
            .trim()
        return cleaned.ifEmpty { cleanText() }
    }

    private val MULTISPACE = Regex("\\s{2,}")
    private val TOPIC_SUFFIX = Regex("\\s+-\\s+Topic$", RegexOption.IGNORE_CASE)
    private val OFFICIAL_SUFFIX = Regex("\\s+(?:officiel(?:le)?|official)$", RegexOption.IGNORE_CASE)
    private val VEVO_SUFFIX = Regex("(\\p{Ll})VEVO$")
}

/** Reconnaît les valeurs de remplissage qui ne disent rien du morceau. */
object TagSanity {

    private val PLACEHOLDERS = setOf(
        "<unknown>", "unknown", "unknown artist", "unknown album", "unknown title",
        "artiste inconnu", "album inconnu", "titre inconnu", "inconnu", "sans titre",
        "untitled", "no artist", "no album", "n/a", "null", "-", "?", "??",
    )

    /** « Piste 12 », « Track 03 », « AudioTrack 1 » : titre posé par un logiciel de rip. */
    private val PLACEHOLDER_TITLE = Regex(
        "^(?:piste|track|titre|pista|audiotrack|audio track|plage)\\s*\\d+$",
        RegexOption.IGNORE_CASE,
    )

    /** Album posé par certains logiciels quand le CD n'est pas reconnu. */
    private val PLACEHOLDER_ALBUM = Regex(
        "^(?:album inconnu|unknown album)\\s*\\(.*\\)$",
        RegexOption.IGNORE_CASE,
    )

    /** Genre ID3v1 resté sous sa forme numérique : « 13 », « (13) ». */
    private val NUMERIC_GENRE = Regex("^\\(?\\d+\\)?$")

    private fun isPlaceholder(value: String): Boolean = value.isBlank() || value.lowercase() in PLACEHOLDERS

    fun isValidTitle(value: String): Boolean = !isPlaceholder(value) && !PLACEHOLDER_TITLE.matches(value)

    fun isValidArtist(value: String): Boolean = !isPlaceholder(value)

    fun isValidAlbum(value: String): Boolean = !isPlaceholder(value) && !PLACEHOLDER_ALBUM.matches(value)

    fun isValidGenre(value: String): Boolean = !isPlaceholder(value) && !NUMERIC_GENRE.matches(value)

    fun isValidYear(value: Int): Boolean = value in 1000..(Calendar.getInstance().get(Calendar.YEAR) + 1)
}
