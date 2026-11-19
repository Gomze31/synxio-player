package fr.synxio.player.wear

import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearStartIds

/**
 * Table commande montre → action sur le téléphone.
 *
 * Sans file chargée (service tout juste démarré), lecture/pause reprend la dernière file ;
 * les autres commandes de transport n'ont rien sur quoi agir et sont ignorées.
 */
class WearCommandExecutor(
    private val target: PlaybackTarget,
    private val volume: VolumeControl,
) {
    /** @return vrai si le volume a changé : l'appelant republie alors l'état. */
    fun execute(command: WearCommand): Boolean {
        when (command) {
            WearCommand.PlayPause -> if (target.hasQueue) target.playPause() else target.start(WearStartIds.LAST_QUEUE)
            WearCommand.Next -> if (target.hasQueue) target.next()
            WearCommand.Previous -> if (target.hasQueue) target.previous()
            WearCommand.ToggleFavorite -> if (target.hasQueue) target.toggleFavorite()
            is WearCommand.Seek -> if (target.hasQueue) target.seekTo(command.positionMs)
            is WearCommand.Start -> target.start(command.mediaId)
            WearCommand.VolumeUp -> { volume.raise(); return true }
            WearCommand.VolumeDown -> { volume.lower(); return true }
            is WearCommand.Volume -> { volume.set(command.level); return true }
        }
        return false
    }
}
