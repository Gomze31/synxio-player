package fr.synxio.player.ui.screens.settings

import fr.synxio.player.core.util.normalizeForSearch
import fr.synxio.player.ui.screens.SettingsSubMenu

/** Un groupe de verre d'une sous-page : sa clé d'élément de liste et son surtitre. */
data class SettingGroupDef(val key: String, val title: String)

/**
 * Un réglage cherchable.
 *
 * @param inSource vrai si [title] apparaît tel quel dans `SettingsScreen.kt` (le test de
 *   synchronisation le vérifie) ; faux pour les titres construits à l'exécution.
 */
data class SettingEntry(
    val title: String,
    val menu: SettingsSubMenu,
    val group: String,
    val keywords: List<String> = emptyList(),
    val inSource: Boolean = true,
)

/**
 * Catalogue des Réglages : groupes par sous-menu dans l'ordre de l'écran, et réglages.
 *
 * Tenu à la main, vérifié par `SettingsCatalogTest` contre le source de l'écran : un
 * titre ajouté à l'écran sans entrée ici fait échouer le test.
 */
object SettingsCatalog {

    val groups: Map<SettingsSubMenu, List<SettingGroupDef>> = mapOf(
        SettingsSubMenu.APPEARANCE to listOf(
            SettingGroupDef("theme", "Thème"),
            SettingGroupDef("player", "Lecteur"),
            SettingGroupDef("display", "Affichage"),
            SettingGroupDef("motion", "Mouvement et lumière"),
            SettingGroupDef("widgets", "Widgets"),
        ),
        SettingsSubMenu.PLAYBACK to listOf(
            SettingGroupDef("transitions", "Enchaînement"),
            SettingGroupDef("queue", "File d'attente"),
            SettingGroupDef("resume", "Reprise et casque"),
            SettingGroupDef("sleep", "Minuterie"),
            SettingGroupDef("radio", "Radio"),
            SettingGroupDef("buds", "Galaxy Buds"),
            SettingGroupDef("alarms", "Réveil"),
        ),
        SettingsSubMenu.CAR to listOf(
            SettingGroupDef("cars", "Mes voitures"),
            SettingGroupDef("car-connect", "À la connexion"),
            SettingGroupDef("car-disconnect", "À la déconnexion"),
        ),
        SettingsSubMenu.VOLUME to listOf(
            SettingGroupDef("normalize", "Normalisation"),
            SettingGroupDef("limit", "Protection auditive"),
        ),
        SettingsSubMenu.LIBRARY to listOf(
            SettingGroupDef("index", "Indexation"),
            SettingGroupDef("tools", "Outils"),
            SettingGroupDef("metadata", "Métadonnées et pochettes"),
        ),
        SettingsSubMenu.EQUALIZER to listOf(SettingGroupDef("eq", "Égaliseur")),
        SettingsSubMenu.LYRICS_AND_SOCIAL to listOf(
            SettingGroupDef("lyrics", "Paroles"),
            SettingGroupDef("lastfm", "Last.fm"),
            SettingGroupDef("discord", "Discord"),
        ),
        SettingsSubMenu.STATS_AND_HISTORY to listOf(SettingGroupDef("stats", "Ton écoute")),
        SettingsSubMenu.BACKUP_AND_RESTORE to listOf(SettingGroupDef("backup", "Données")),
        SettingsSubMenu.SYSTEM_AND_ABOUT to listOf(
            SettingGroupDef("updates", "Mises à jour"),
            SettingGroupDef("maintenance", "Maintenance"),
            SettingGroupDef("about", "À propos"),
        ),
    )

    fun groupTitle(menu: SettingsSubMenu, key: String): String =
        groups[menu]?.firstOrNull { it.key == key }?.title ?: key

    /** Indice de l'élément de liste du groupe : l'en-tête de sous-page est l'élément 0. */
    fun indexOfGroup(menu: SettingsSubMenu, key: String): Int =
        1 + (groups[menu]?.indexOfFirst { it.key == key } ?: 0).coerceAtLeast(0)

    private fun e(menu: SettingsSubMenu, group: String, title: String, vararg keywords: String, inSource: Boolean = true) =
        SettingEntry(title, menu, group, keywords.toList(), inSource)

    val entries: List<SettingEntry> = buildList {
        val a = SettingsSubMenu.APPEARANCE
        add(e(a, "theme", "Mode de thème", "sombre", "clair", "amoled", "noir", "système"))
        add(e(a, "theme", "Couleurs de l'application", "accent", "pochette", "material you", "personnalisé"))
        add(e(a, "theme", "Préréglage de thème", "preset", "lumen", "braise", "magenta", inSource = false))
        add(e(a, "player", "Style du lecteur", "immersif", "vinyle", "carte", "minimaliste", "skin"))
        add(e(a, "player", "Fond flouté", "blur", "arrière-plan"))
        add(e(a, "player", "Visualiseur audio", "spectre", "fft", "barres"))
        add(e(a, "player", "Maintenir l'écran allumé", "veille écran", "wake"))
        add(e(a, "display", "Colonnes de la grille d'albums", "grille", "colonnes", "albums"))
        add(e(a, "display", "Taille du texte", "police", "lisibilité", "grand texte"))
        add(e(a, "motion", "Réduire les animations", "mouvement", "motion", "transitions"))
        add(e(a, "motion", "Effets lumineux", "glow", "halo", "aurore"))
        add(e(a, "motion", "Scène réactive", "pouls", "aurore", "musique"))
        add(e(a, "motion", "Canvas animé", "pochette animée"))
        add(e(a, "motion", "Réessayer les shaders", "agsl", "verre", "shader"))
        add(e(a, "motion", "Retour haptique", "vibration", "vibreur", "haptique"))
        add(e(a, "widgets", "Ajouter le widget compact", "widget", "écran d'accueil"))
        add(e(a, "widgets", "Ajouter le grand widget", "widget", "écran d'accueil"))

        val p = SettingsSubMenu.PLAYBACK
        add(e(p, "transitions", "Fondu entre les morceaux", "crossfade", "fondu enchaîné", "transition", "gapless", "sans blanc"))
        add(e(p, "transitions", "Courbe du fondu", "crossfade", "equal-power", "linéaire", "douce", "courbe"))
        add(e(p, "transitions", "Caler sur l'énergie", "crossfade", "intro", "outro", "énergie", "silence"))
        add(e(p, "transitions", "Ignorer les silences", "silence", "skip silence"))
        add(e(p, "queue", "Auto-DJ", "file", "suivants", "automatique"))
        add(e(p, "queue", "Aléatoire sans les titres zappés", "shuffle", "aléatoire", "zappés"))
        add(e(p, "queue", "Reprendre la file au démarrage", "file", "reprise", "démarrage"))
        add(e(p, "resume", "Rembobinage automatique à la reprise", "reprise", "retour", "rewind"))
        add(e(p, "resume", "Reprendre au branchement du casque", "casque", "écouteurs", "bluetooth", "jack"))
        add(e(p, "resume", "Gestes du bouton de casque", "casque", "bouton", "appui", "écouteurs"))
        add(e(p, "sleep", "Arrêter après inactivité", "veille", "sleep timer", "minuterie", "inactivité"))
        add(e(p, "sleep", "Durée d'inactivité", "veille", "minuterie"))
        add(e(p, "sleep", "Immobilité avant pause", "veille adaptative", "immobile", "pause"))
        add(e(p, "radio", "Analyser le son des titres", "empreinte", "similarité", "ambiance", "radio", "analyse", inSource = false))
        add(e(p, "radio", "Analyser seulement en charge", "batterie", "chargeur", "analyse"))
        add(e(p, "buds", "Correction auto de l'égaliseur", "galaxy buds", "écouteurs", "auto eq"))
        add(e(p, "buds", "Double toucher", "galaxy buds", "gestes"))
        add(e(p, "buds", "Triple toucher", "galaxy buds", "gestes"))
        add(e(p, "buds", "À la connexion, si rien ne joue", "galaxy buds", "connexion"))
        add(e(p, "buds", "Alerte batterie faible", "galaxy buds", "batterie"))
        add(e(p, "buds", "Audio 360", "galaxy buds", "spatial"))
        add(e(p, "buds", "Réduction de bruit et son ambiant", "galaxy buds", "anc", "bruit"))
        add(e(p, "alarms", "Réveils en musique", "alarme", "réveil", "matin"))

        val c = SettingsSubMenu.CAR
        add(e(c, "cars", "Mes voitures", "voiture", "bluetooth", "autoradio", inSource = false))
        add(e(c, "car-connect", "Proposer le mode voiture", "voiture", "notification"))
        add(e(c, "car-connect", "Lancer la lecture", "voiture", "automatique"))
        add(e(c, "car-connect", "Quoi lancer", "voiture", "mix", "favoris"))
        add(e(c, "car-connect", "Playlist", "voiture"))
        add(e(c, "car-connect", "Ouvrir le mode voiture automatiquement", "voiture"))
        add(e(c, "car-disconnect", "Quitter avec la voiture", "voiture", "déconnexion"))
        add(e(c, "car-disconnect", "Mettre en pause à la déconnexion", "voiture", "pause", "bluetooth"))

        val v = SettingsSubMenu.VOLUME
        add(e(v, "normalize", "Normaliser le volume", "loudness", "replaygain", "niveau", "normalisation"))
        add(e(v, "normalize", "Niveau cible", "dbfs", "loudness", "normalisation"))
        add(e(v, "limit", "Limiter le volume", "protection", "oreilles", "limiteur"))
        add(e(v, "limit", "Volume maximum", "protection", "limiteur"))

        val l = SettingsSubMenu.LIBRARY
        add(e(l, "index", "Durée minimale d'un titre", "courts", "bips", "secondes", "filtre"))
        add(e(l, "index", "Onglet par défaut", "bibliothèque", "onglet", "ouverture"))
        add(e(l, "tools", "Relancer le scan", "scan", "bibliothèque", "rescanner", "synchronisation", "dossiers"))
        add(e(l, "tools", "Atelier des tags", "tags", "id3", "réparer", "pochettes"))
        add(e(l, "tools", "Supprimer les fichiers en double", "doublons", "duplicates"))
        add(e(l, "tools", "Recherche avancée", "filtre", "recherche"))
        add(e(l, "metadata", "Compléter en ligne", "musicbrainz", "pochettes", "métadonnées", "wi-fi"))
        add(e(l, "metadata", "Lire les images des dossiers", "cover", "folder", "pochettes", "images"))
        add(e(l, "metadata", "Tout réanalyser", "métadonnées", "cascade", "pochettes"))

        add(e(SettingsSubMenu.EQUALIZER, "eq", "Ouvrir l'égaliseur complet", "égaliseur", "eq", "basses", "aigus", "bandes"))

        val s = SettingsSubMenu.LYRICS_AND_SOCIAL
        add(e(s, "lyrics", "Chercher les paroles en ligne", "paroles", "lyrics", "lrclib", "lrc"))
        add(e(s, "lyrics", "Traduire les paroles automatiquement", "traduction", "paroles"))
        add(e(s, "lastfm", "Scrobbling Last.fm", "last.fm", "scrobble", "compte"))
        add(e(s, "discord", "Statut d'activité Discord", "discord", "rich presence"))
        add(e(s, "discord", "Annoncer sur Discord", "discord", "webhook"))

        val st = SettingsSubMenu.STATS_AND_HISTORY
        add(e(st, "stats", "Mes statistiques", "stats", "écoute", "récap"))
        add(e(st, "stats", "Historique de lecture", "historique", "récents"))

        add(e(SettingsSubMenu.BACKUP_AND_RESTORE, "backup", "Sauvegarde et restauration", "backup", "export", "import", "json", "restaurer"))

        val sys = SettingsSubMenu.SYSTEM_AND_ABOUT
        add(e(sys, "updates", "Rechercher une mise à jour", "mise à jour", "update", "version", "github"))
        add(e(sys, "maintenance", "Nettoyer les caches", "cache", "espace", "stockage"))
        add(e(sys, "about", "À propos de Synxio", "version", "licence", "diagnostic", "journal"))
    }
}

/** Recherche floue dans le catalogue : accents et casse ignorés, tous les mots doivent correspondre. */
object SettingsSearch {

    fun search(query: String, entries: List<SettingEntry>): List<SettingEntry> {
        val tokens = query.normalizeForSearch().split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val title = entry.title.normalizeForSearch()
            val keywords = entry.keywords.map { it.normalizeForSearch() }
            val group = SettingsCatalog.groupTitle(entry.menu, entry.group).normalizeForSearch()
            val menu = entry.menu.title.normalizeForSearch()
            var score = 0
            for (token in tokens) {
                score += when {
                    title.startsWith(token) -> 4
                    title.contains(token) -> 3
                    keywords.any { it.contains(token) } -> 2
                    group.contains(token) || menu.contains(token) -> 1
                    else -> return@mapNotNull null
                }
            }
            entry to score
        }.sortedByDescending { it.second }.map { it.first }
    }
}
