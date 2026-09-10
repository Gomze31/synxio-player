package fr.synxio.player.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ce sur quoi une règle porte. [operators] borne ce que l'éditeur a le droit de proposer :
 * « favori contient » ne doit jamais pouvoir être composé.
 *
 * `operators` est une propriété **calculée** et non un paramètre du constructeur : des
 * constantes top-level passées aux entrées d'enum seraient encore nulles si `RuleField`
 * est chargée avant les propriétés du fichier.
 */
enum class RuleField(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    ALBUM("Album"),
    ALBUM_ARTIST("Artiste de l'album"),
    GENRE("Genre"),
    FOLDER("Dossier"),
    FORMAT("Format"),
    YEAR("Année"),
    DURATION("Durée (s)"),
    PLAY_COUNT("Nombre de lectures"),
    SKIP_COUNT("Nombre de passages"),
    BITRATE("Débit (kbps)"),
    LAST_PLAYED("Dernière écoute"),
    DATE_ADDED("Date d'ajout"),
    FAVORITE("Favori");

    val operators: List<RuleOperator>
        get() = when (this) {
            TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, FOLDER, FORMAT -> TEXT_OPS
            YEAR, DURATION, PLAY_COUNT, SKIP_COUNT, BITRATE -> NUMBER_OPS
            LAST_PLAYED, DATE_ADDED -> DATE_OPS
            FAVORITE -> BOOL_OPS
        }
}

enum class RuleOperator(val label: String) {
    CONTAINS("contient"),
    NOT_CONTAINS("ne contient pas"),
    EQUALS("est"),
    NOT_EQUALS("n'est pas"),
    STARTS_WITH("commence par"),
    GREATER_THAN("est supérieur à"),
    LESS_THAN("est inférieur à"),
    IN_LAST_DAYS("dans les N derniers jours"),
    NOT_IN_LAST_DAYS("pas depuis N jours"),
    IS_TRUE("oui"),
    IS_FALSE("non"),
}

private val TEXT_OPS = listOf(
    RuleOperator.CONTAINS, RuleOperator.NOT_CONTAINS,
    RuleOperator.EQUALS, RuleOperator.NOT_EQUALS, RuleOperator.STARTS_WITH,
)
private val NUMBER_OPS = listOf(
    RuleOperator.EQUALS, RuleOperator.NOT_EQUALS,
    RuleOperator.GREATER_THAN, RuleOperator.LESS_THAN,
)
private val DATE_OPS = listOf(RuleOperator.IN_LAST_DAYS, RuleOperator.NOT_IN_LAST_DAYS)
private val BOOL_OPS = listOf(RuleOperator.IS_TRUE, RuleOperator.IS_FALSE)

@Serializable
data class SmartRule(
    val field: RuleField,
    val operator: RuleOperator,
    val value: String,
)

enum class SmartSort(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    ALBUM("Album"),
    YEAR("Année"),
    DATE_ADDED("Date d'ajout"),
    PLAY_COUNT("Nombre de lectures"),
    LAST_PLAYED("Dernière écoute"),
    RANDOM("Aléatoire"),
}

@Serializable
data class SmartPlaylistRules(
    val rules: List<SmartRule> = emptyList(),
    /** true = toutes les règles (ET), false = au moins une (OU). */
    val matchAll: Boolean = true,
    val sort: SmartSort = SmartSort.TITLE,
    val descending: Boolean = false,
    val limit: Int? = null,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * Un JSON illisible (base éditée à la main, format changé entre deux versions)
         * ne doit pas faire planter l'écran des playlists : on retombe sur des règles
         * vides, l'utilisateur voit une playlist sans critère et peut la corriger.
         */
        fun fromJson(json: String): SmartPlaylistRules =
            runCatching { JSON.decodeFromString(serializer(), json) }
                .getOrDefault(SmartPlaylistRules())
    }
}

/**
 * Les presets ne vivent pas en base mais doivent être navigables comme les playlists
 * stockées : cet identifiant unifie les deux dans les routes.
 */
sealed interface SmartPlaylistId {
    data class Preset(val key: String) : SmartPlaylistId
    data class Stored(val id: Long) : SmartPlaylistId

    fun encode(): String = when (this) {
        is Preset -> "preset:$key"
        is Stored -> "stored:$id"
    }

    companion object {
        fun decode(value: String): SmartPlaylistId = when {
            value.startsWith("stored:") ->
                Stored(value.removePrefix("stored:").toLongOrNull() ?: -1L)
            else -> Preset(value.removePrefix("preset:"))
        }
    }
}

/** Une playlist intelligente prête à afficher : ses règles et son résultat courant. */
data class SmartPlaylist(
    val id: SmartPlaylistId,
    val name: String,
    val rules: SmartPlaylistRules,
    val songs: List<Song>,
) {
    val isPreset: Boolean get() = id is SmartPlaylistId.Preset
    val songCount: Int get() = songs.size
}
