package fr.synxio.player.watch.data

import android.content.Context
import fr.synxio.player.watch.complication.NowPlayingComplicationService
import fr.synxio.player.watch.tile.SynxioTileService

/** Rafraîchit la tuile et la complication après un nouvel état. */
object WearSurfaces {
    fun refreshAll(context: Context) {
        runCatching { SynxioTileService.requestUpdate(context) }
        runCatching { NowPlayingComplicationService.requestUpdate(context) }
    }
}
