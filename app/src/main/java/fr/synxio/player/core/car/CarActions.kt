package fr.synxio.player.core.car

enum class CarEvent { CONNECTED, DISCONNECTED }

/** Ce que « Lancer la lecture » démarre quand la voiture se connecte. */
enum class CarPlaySource(val label: String) {
    RESUME("Reprendre la file"),
    DAILY_MIX("Mix du jour"),
    FAVORITES("Favoris en aléatoire"),
    PLAYLIST("Une playlist"),
}

/** Les réglages Voiture, tels que [carActions] les lit. */
data class CarSettings(
    val notify: Boolean,
    val autoPlay: Boolean,
    val playSource: CarPlaySource,
    val playlistId: Long,
    val openDriveMode: Boolean,
    val exitWithCar: Boolean,
    val pauseOnDisconnect: Boolean,
)

sealed interface CarAction {
    /** Notification « Voiture détectée », avec ses deux boutons. */
    data object Notify : CarAction
    data class StartPlayback(val source: CarPlaySource, val playlistId: Long) : CarAction
    /** Ouvrir l'écran du mode voiture : seulement quand l'app est déjà visible. */
    data object OpenDriveMode : CarAction
    data object DismissNotification : CarAction
    data object ExitDriveMode : CarAction
    data object Pause : CarAction
}

/**
 * Réglages + événement → actions, dans l'ordre d'exécution.
 *
 * Android interdit d'ouvrir une activité depuis l'arrière-plan : « Ouvrir automatiquement »
 * se replie alors sur la notification, même si « Proposer » est coupé.
 */
fun carActions(event: CarEvent, settings: CarSettings, foreground: Boolean): List<CarAction> = when (event) {
    CarEvent.CONNECTED -> buildList {
        if (settings.autoPlay) add(CarAction.StartPlayback(settings.playSource, settings.playlistId))
        when {
            settings.openDriveMode && foreground -> add(CarAction.OpenDriveMode)
            settings.notify || settings.openDriveMode -> add(CarAction.Notify)
        }
    }
    CarEvent.DISCONNECTED -> buildList {
        add(CarAction.DismissNotification)
        if (settings.exitWithCar) add(CarAction.ExitDriveMode)
        if (settings.pauseOnDisconnect) add(CarAction.Pause)
    }
}
