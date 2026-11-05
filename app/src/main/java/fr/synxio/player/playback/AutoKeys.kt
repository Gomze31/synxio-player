package fr.synxio.player.playback

/**
 * Clés des actions de navigation Android Auto (menu contextuel d'un élément parcouru).
 *
 * Elles ne vivent que comme chaînes dans `androidx.media.utils.MediaConstants`
 * (androidx.media:media, dépendance transitive de media3-session, jamais importée
 * ici). Media3 1.11.1 relaie l'appel : `MediaLibraryServiceLegacyStub.onCustomAction`
 * remonte dans `MediaLibrarySession.Callback.onCustomCommand`, avec l'identifiant de
 * l'action en commande et les extras du navigateur en arguments.
 *
 * Valeurs relevées dans androidx.media:media:1.7.0 ; `AutoKeysTest` les fige.
 */
object AutoKeys {
    /** Indice de racine : nombre d'actions par élément que l'hôte sait afficher. */
    const val ROOT_HINT_ACTION_LIMIT = "androidx.media.utils.MediaBrowserCompat.extras.CUSTOM_BROWSER_ACTION_LIMIT"
    /** Extras de racine : liste (`ArrayList<Bundle>`) des actions déclarées. */
    const val ROOT_EXTRAS_ACTION_LIST = "androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ROOT_LIST"
    /** Extras d'un élément : identifiants (`ArrayList<String>`) des actions qui s'y appliquent. */
    const val ITEM_ACTION_ID_LIST = "androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ID_LIST"

    const val ACTION_ID = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ID"
    const val ACTION_LABEL = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_LABEL"
    const val ACTION_ICON_URI = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ICON_URI"
    const val ACTION_EXTRAS = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_EXTRAS"
    /** Argument reçu avec l'action : l'identifiant de l'élément visé. */
    const val ACTION_MEDIA_ITEM_ID = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID"
    /** Résultat : message bref affiché par l'hôte. */
    const val RESULT_MESSAGE = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE"
    /** Résultat : identifiant d'un élément à recharger (le cœur change d'état). */
    const val RESULT_REFRESH_ITEM = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM"

    const val ACTION_FAVORITE = "favorite"
    const val ACTION_UNFAVORITE = "unfavorite"
    const val ACTION_PLAY_NEXT = "play_next"
    const val ACTION_ADD_TO_QUEUE = "add_to_queue"
    const val ACTION_SHUFFLE = "shuffle"

    /** Ordre de déclaration à la racine, et ordre d'affichage par l'hôte. */
    val ALL_ACTIONS: List<String> = listOf(ACTION_FAVORITE, ACTION_UNFAVORITE, ACTION_PLAY_NEXT, ACTION_ADD_TO_QUEUE, ACTION_SHUFFLE)
}
