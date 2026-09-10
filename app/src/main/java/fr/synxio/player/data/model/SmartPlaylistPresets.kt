package fr.synxio.player.data.model

/**
 * Playlists intelligentes livrées d'office. Volontairement **non stockées en base** :
 * corriger un seuil se fait par mise à jour de l'appli, pas par une migration, et
 * l'utilisateur ne peut pas se retrouver avec des presets à moitié supprimés.
 *
 * Un preset n'est pas modifiable ; « Dupliquer en playlist modifiable » en fait une
 * copie stockée que l'utilisateur peut ajuster.
 */
object SmartPlaylistPresets {

    data class Preset(val key: String, val name: String, val rules: SmartPlaylistRules)

    val all: List<Preset> = listOf(
        Preset(
            key = "recent",
            name = "Ajoutés récemment",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.DATE_ADDED, RuleOperator.IN_LAST_DAYS, "30")),
                sort = SmartSort.DATE_ADDED, descending = true, limit = 100,
            ),
        ),
        Preset(
            key = "most_played",
            name = "Les plus écoutés",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "2")),
                sort = SmartSort.PLAY_COUNT, descending = true, limit = 100,
            ),
        ),
        Preset(
            key = "never_played",
            name = "Jamais écoutés",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "1")),
                sort = SmartSort.RANDOM, limit = 100,
            ),
        ),
        Preset(
            key = "favorites",
            name = "Coups de cœur",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")),
                sort = SmartSort.DATE_ADDED, descending = true,
            ),
        ),
        Preset(
            key = "forgotten",
            name = "Oubliés",
            rules = SmartPlaylistRules(
                rules = listOf(
                    SmartRule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "180"),
                    SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "0"),
                ),
                sort = SmartSort.LAST_PLAYED, limit = 100,
            ),
        ),
        Preset(
            key = "discoveries",
            name = "Découvertes",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "2")),
                sort = SmartSort.RANDOM, limit = 50,
            ),
        ),
    )

    fun byKey(key: String): Preset? = all.firstOrNull { it.key == key }
}
