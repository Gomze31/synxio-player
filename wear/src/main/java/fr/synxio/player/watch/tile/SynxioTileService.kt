package fr.synxio.player.watch.tile

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.watch.R
import fr.synxio.player.watch.data.PhoneLink
import fr.synxio.player.watch.data.WearStateStore
import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearStartIds
import fr.synxio.player.wear.protocol.WearState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tuile « en cours » : titre, artiste, lecture/pause · suivant · cœur.
 * Sans lecture : « Synxio », Reprendre · Favoris.
 *
 * Rafraîchie par [fr.synxio.player.watch.data.WearSurfaces] à chaque état reçu, jamais
 * par horloge. Un appui sur un bouton est une `LoadAction` : la tuile est redemandée
 * avec `lastClickableId`, la commande part, et la tuile renvoyée est optimiste.
 */
@AndroidEntryPoint
class SynxioTileService : TileService() {

    @Inject lateinit var store: WearStateStore
    @Inject lateinit var link: PhoneLink

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val clicked = requestParams.currentState.lastClickableId
        val state = store.current().state ?: WearState()
        val shown = when (clicked) {
            ID_PLAY_PAUSE -> { send(WearCommand.PlayPause); state.copy(isPlaying = !state.isPlaying) }
            ID_NEXT -> { send(WearCommand.Next); state }
            ID_HEART -> { send(WearCommand.ToggleFavorite); state.copy(favorite = !state.favorite) }
            ID_RESUME -> { send(WearCommand.Start(WearStartIds.LAST_QUEUE)); state }
            ID_FAVORITES -> { send(WearCommand.Start(WearStartIds.FAVORITES)); state }
            else -> state
        }
        val layout = materialScope(this, requestParams.deviceConfiguration) {
            if (shown.isEmpty) idleLayout() else playingLayout(shown)
        }
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(0)
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .build()
        return CallbackToFutureAdapter.getFuture { completer -> completer.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .addIdToImageMapping(RES_PLAY, drawable(R.drawable.ic_tile_play))
            .addIdToImageMapping(RES_PAUSE, drawable(R.drawable.ic_tile_pause))
            .addIdToImageMapping(RES_NEXT, drawable(R.drawable.ic_tile_next))
            .addIdToImageMapping(RES_HEART, drawable(R.drawable.ic_tile_heart))
            .addIdToImageMapping(RES_HEART_OUTLINE, drawable(R.drawable.ic_tile_heart_outline))
            .addIdToImageMapping(RES_NOTE, drawable(R.drawable.ic_tile_note))
            .build()
        return CallbackToFutureAdapter.getFuture { completer -> completer.set(resources); "resources" }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun MaterialScope.playingLayout(state: WearState): LayoutElement = primaryLayout(
        titleSlot = { text(state.title.layoutString, typography = Typography.TITLE_SMALL, maxLines = 1) },
        mainSlot = {
            buttonGroup {
                buttonGroupItem {
                    iconButton(
                        onClick = clickable(id = ID_PLAY_PAUSE, action = loadAction()),
                        iconContent = { icon(if (state.isPlaying) RES_PAUSE else RES_PLAY) },
                    )
                }
                buttonGroupItem {
                    iconButton(
                        onClick = clickable(id = ID_NEXT, action = loadAction()),
                        iconContent = { icon(RES_NEXT) },
                    )
                }
                buttonGroupItem {
                    iconButton(
                        onClick = clickable(id = ID_HEART, action = loadAction()),
                        iconContent = { icon(if (state.favorite) RES_HEART else RES_HEART_OUTLINE) },
                    )
                }
            }
        },
        bottomSlot = {
            text(state.artist.layoutString, typography = Typography.BODY_SMALL, maxLines = 1)
        },
    )

    private fun MaterialScope.idleLayout(): LayoutElement = primaryLayout(
        titleSlot = { text(getString(R.string.app_name).layoutString, typography = Typography.TITLE_SMALL) },
        mainSlot = {
            buttonGroup {
                buttonGroupItem {
                    textButton(
                        onClick = clickable(id = ID_RESUME, action = loadAction()),
                        labelContent = { text(getString(R.string.resume).layoutString) },
                    )
                }
                buttonGroupItem {
                    textButton(
                        onClick = clickable(id = ID_FAVORITES, action = loadAction()),
                        labelContent = { text(getString(R.string.favorites).layoutString) },
                    )
                }
            }
        },
    )

    private fun send(command: WearCommand) {
        scope.launch { link.send(command) }
    }

    private fun drawable(resId: Int) = ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(resId).build())
        .build()

    companion object {
        private const val ID_PLAY_PAUSE = "play_pause"
        private const val ID_NEXT = "next"
        private const val ID_HEART = "heart"
        private const val ID_RESUME = "resume"
        private const val ID_FAVORITES = "favorites"
        private const val RES_PLAY = "ic_play"
        private const val RES_PAUSE = "ic_pause"
        private const val RES_NEXT = "ic_next"
        private const val RES_HEART = "ic_heart"
        private const val RES_HEART_OUTLINE = "ic_heart_outline"
        private const val RES_NOTE = "ic_note"

        /** Icônes seules, fixes : une version suffit. */
        private const val RESOURCES_VERSION = "1"

        /** Pour `WearSurfaces` : redemande la tuile au système. */
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(SynxioTileService::class.java)
        }
    }
}
