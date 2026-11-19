package fr.synxio.player.wear.protocol

/**
 * Chemins et clés du Wearable Data Layer, partagés entre le téléphone et la montre.
 *
 * Le téléphone publie un `DataItem` sur [STATE] ; la montre envoie des messages sur
 * [COMMAND] au nœud qui annonce la capacité [CAPABILITY_PHONE].
 */
object WearPaths {
    const val STATE = "/synxio/state"
    const val COMMAND = "/synxio/cmd"
    const val CAPABILITY_PHONE = "synxio_phone"

    /** Incrémenté à chaque rupture du format de [WearState] ou des commandes. */
    const val PROTOCOL_VERSION = 1

    /** Clés de la `DataMap` du `DataItem` d'état. */
    const val KEY_STATE = "state"
    const val KEY_ART = "art"
    const val KEY_ART_BLURRED = "artBlurred"

    /** Côté de la pochette publiée, en pixels. */
    const val ART_PIXELS = 160
}
