package fr.synxio.player.wear.protocol

/**
 * Commande envoyée par la montre au téléphone, encodée en une chaîne UTF-8 courte.
 *
 * Les chaînes sont stables : une montre plus ancienne reste comprise par un téléphone
 * plus récent, et inversement, tant que [WearPaths.PROTOCOL_VERSION] ne change pas.
 */
sealed interface WearCommand {
    data object PlayPause : WearCommand
    data object Next : WearCommand
    data object Previous : WearCommand
    data object ToggleFavorite : WearCommand
    data object VolumeUp : WearCommand
    data object VolumeDown : WearCommand

    /** Niveau absolu de 0 à 1. Non utilisé par l'interface V1. */
    data class Volume(val level: Float) : WearCommand

    /** Non utilisé par l'interface V1. */
    data class Seek(val positionMs: Long) : WearCommand

    /** Lance une file à partir d'un identifiant `synxio://…` résolu par `onSetMediaItems`. */
    data class Start(val mediaId: String) : WearCommand

    fun encode(): String = when (this) {
        PlayPause -> "play_pause"
        Next -> "next"
        Previous -> "previous"
        ToggleFavorite -> "toggle_favorite"
        VolumeUp -> "volume_up"
        VolumeDown -> "volume_down"
        is Volume -> "volume:$level"
        is Seek -> "seek:$positionMs"
        is Start -> "start:$mediaId"
    }

    companion object {
        /** `null` pour toute charge inconnue ou hors bornes : la commande est ignorée. */
        fun parse(payload: String): WearCommand? {
            when (payload) {
                "play_pause" -> return PlayPause
                "next" -> return Next
                "previous" -> return Previous
                "toggle_favorite" -> return ToggleFavorite
                "volume_up" -> return VolumeUp
                "volume_down" -> return VolumeDown
            }
            val separator = payload.indexOf(':')
            if (separator <= 0) return null
            val key = payload.substring(0, separator)
            val value = payload.substring(separator + 1)
            return when (key) {
                "volume" -> value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let(::Volume)
                "seek" -> value.toLongOrNull()?.takeIf { it >= 0L }?.let(::Seek)
                "start" -> value.takeIf { it.isNotEmpty() }?.let(::Start)
                else -> null
            }
        }
    }
}

/** Identifiants de lancement rapide, miroir de `BrowseIds` côté téléphone. */
object WearStartIds {
    const val LAST_QUEUE = "synxio://last-queue"
    const val FAVORITES = "synxio://favorites"
    const val SHUFFLE_ALL = "synxio://shuffle/all"
    const val SURPRISE = "synxio://surprise"
}
