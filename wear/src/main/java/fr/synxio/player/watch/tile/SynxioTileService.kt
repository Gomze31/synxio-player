package fr.synxio.player.watch.tile

import android.content.ComponentName
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.avatarImage
import androidx.wear.protolayout.material3.backgroundImage
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.iconEdgeButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.border
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.clip
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.watch.MainActivity
import fr.synxio.player.watch.R
import fr.synxio.player.watch.data.PhoneLink
import fr.synxio.player.watch.data.WearStateStore
import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearPaths
import fr.synxio.player.wear.protocol.WearStartIds
import fr.synxio.player.wear.protocol.WearState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Carte « en cours » (tuile Wear OS), dans l'esprit Lumen.
 *
 * Fond : pochette floutée sous un voile encre. Haut : pochette ronde, titre, artiste.
 * Bas : un seul bouton de bord lecture/pause à la couleur d'accent de la pochette ;
 * suivant et cœur en petites icônes discrètes juste au-dessus. Sans lecture : note,
 * « Synxio », bouton de bord « Reprendre », « Favoris » dessous.
 *
 * Rafraîchie par [fr.synxio.player.watch.data.WearSurfaces] à chaque état reçu, jamais
 * par horloge. Un appui sur un bouton est une `LoadAction` : la tuile est redemandée avec
 * `lastClickableId`, la commande part, et la tuile renvoyée est optimiste.
 */
@AndroidEntryPoint
class SynxioTileService : TileService() {

    @Inject lateinit var store: WearStateStore
    @Inject lateinit var link: PhoneLink

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val clicked = requestParams.currentState.lastClickableId
        val stored = store.current()
        val state = stored.state ?: WearState()
        val shown = when (clicked) {
            ID_PLAY_PAUSE -> { send(WearCommand.PlayPause); state.copy(isPlaying = !state.isPlaying) }
            ID_NEXT -> { send(WearCommand.Next); state }
            ID_HEART -> { send(WearCommand.ToggleFavorite); state.copy(favorite = !state.favorite) }
            ID_RESUME -> { send(WearCommand.Start(WearStartIds.LAST_QUEUE)); state }
            ID_FAVORITES -> { send(WearCommand.Start(WearStartIds.FAVORITES)); state }
            else -> state
        }
        val hasArt = !shown.isEmpty && stored.art != null && stored.artBlurred != null
        val layout = materialScope(
            context = this,
            deviceConfiguration = requestParams.deviceConfiguration,
            allowDynamicTheme = false,
            defaultColorScheme = lumenScheme(shown.accent),
        ) {
            if (shown.isEmpty) idleLayout() else playingLayout(shown, hasArt)
        }
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersion(stored))
            .setFreshnessIntervalMillis(0)
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .build()
        return CallbackToFutureAdapter.getFuture { completer -> completer.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val stored = store.current()
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .addIdToImageMapping(RES_PLAY, drawable(R.drawable.ic_tile_play))
            .addIdToImageMapping(RES_PAUSE, drawable(R.drawable.ic_tile_pause))
            .addIdToImageMapping(RES_NEXT, drawable(R.drawable.ic_tile_next))
            .addIdToImageMapping(RES_HEART, drawable(R.drawable.ic_tile_heart))
            .addIdToImageMapping(RES_HEART_OUTLINE, drawable(R.drawable.ic_tile_heart_outline))
            .addIdToImageMapping(RES_NOTE, drawable(R.drawable.ic_tile_note))
        stored.art?.let { builder.addIdToImageMapping(RES_ART, inline(it)) }
        stored.artBlurred?.let { builder.addIdToImageMapping(RES_BACKDROP, inline(it)) }
        val resources = builder.build()
        return CallbackToFutureAdapter.getFuture { completer -> completer.set(resources); "resources" }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- Mise en page ---------------------------------------------------------------------

    private fun MaterialScope.playingLayout(state: WearState, hasArt: Boolean): LayoutElement {
        val openApp = clickable(id = ID_OPEN, action = launchApp())
        val tonal = tonalColors()
        val header = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(Modifiers.Builder().setClickable(openApp).build())
            .apply {
                if (hasArt) {
                    addContent(
                        avatarImage(
                            protoLayoutResourceId = RES_ART,
                            width = dp(ART_DP),
                            height = dp(ART_DP),
                            // Ronde, cerclée de l'accent : même signature que le lecteur.
                            modifier = LayoutModifier.clip(ART_DP / 2).border(1.5f, colorScheme.primary),
                        ),
                    )
                } else {
                    addContent(icon(protoLayoutResourceId = RES_NOTE, width = dp(28f), height = dp(28f), tintColor = colorScheme.primary))
                }
                addContent(Spacer.Builder().setHeight(dp(4f)).build())
                addContent(text(state.title.layoutString, typography = Typography.TITLE_MEDIUM, color = colorScheme.onBackground, maxLines = 1))
                addContent(text(state.artist.layoutString, typography = Typography.BODY_SMALL, color = colorScheme.onSurfaceVariant, maxLines = 1))
            }
            .build()

        val sideButtons = Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(
                iconButton(
                    onClick = clickable(id = ID_NEXT, action = loadAction()),
                    iconContent = { icon(RES_NEXT, width = dp(18f), height = dp(18f)) },
                    width = dp(SMALL_BUTTON_DP),
                    height = dp(SMALL_BUTTON_DP),
                    colors = tonal,
                ),
            )
            .addContent(Spacer.Builder().setWidth(dp(16f)).build())
            .addContent(
                iconButton(
                    onClick = clickable(id = ID_HEART, action = loadAction()),
                    iconContent = {
                        icon(
                            if (state.favorite) RES_HEART else RES_HEART_OUTLINE,
                            width = dp(18f),
                            height = dp(18f),
                            tintColor = if (state.favorite) colorScheme.primary else colorScheme.onSurface,
                        )
                    },
                    width = dp(SMALL_BUTTON_DP),
                    height = dp(SMALL_BUTTON_DP),
                    colors = tonal,
                ),
            )
            .build()

        val foreground = primaryLayout(
            mainSlot = {
                Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(header)
                    .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                    .addContent(sideButtons)
                    .build()
            },
            bottomSlot = {
                iconEdgeButton(
                    onClick = clickable(id = ID_PLAY_PAUSE, action = loadAction()),
                    modifier = LayoutModifier.contentDescription(getString(if (state.isPlaying) R.string.pause else R.string.play)),
                ) { icon(if (state.isPlaying) RES_PAUSE else RES_PLAY) }
            },
        )
        return if (hasArt) withBackdrop(foreground) else foreground
    }

    private fun MaterialScope.idleLayout(): LayoutElement = primaryLayout(
        mainSlot = {
            Column.Builder()
                .setWidth(expand())
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .addContent(icon(protoLayoutResourceId = RES_NOTE, width = dp(28f), height = dp(28f), tintColor = colorScheme.primary))
                .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                .addContent(text(getString(R.string.app_name).layoutString, typography = Typography.TITLE_MEDIUM, color = colorScheme.onBackground))
                .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                .addContent(
                    textButton(
                        onClick = clickable(id = ID_FAVORITES, action = loadAction()),
                        labelContent = { text(getString(R.string.favorites).layoutString, typography = Typography.LABEL_SMALL) },
                        height = dp(SMALL_BUTTON_DP),
                        colors = tonalColors(),
                    ),
                )
                .build()
        },
        bottomSlot = {
            textEdgeButton(
                onClick = clickable(id = ID_RESUME, action = loadAction()),
            ) { text(getString(R.string.resume).layoutString) }
        },
    )

    /** Pochette floutée plein cadran sous un voile encre, puis le contenu. */
    private fun MaterialScope.withBackdrop(foreground: LayoutElement): LayoutElement = Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .addContent(
            backgroundImage(
                protoLayoutResourceId = RES_BACKDROP,
                overlayColor = INK_VEIL.argb,
            ),
        )
        .addContent(foreground)
        .build()

    /** Lumen : encre, accent de la pochette (braise sans pochette), texte blanc. */
    private fun lumenScheme(accent: Int): ColorScheme {
        val primary = if (accent == 0) EMBER else accent
        val onPrimary = if (luminance(primary) > 0.5f) INK else WHITE
        return ColorScheme(
            primary = primary.argb,
            onPrimary = onPrimary.argb,
            primaryContainer = withAlpha(primary, 0.28f).argb,
            onPrimaryContainer = WHITE.argb,
            secondary = primary.argb,
            onSecondary = onPrimary.argb,
            secondaryContainer = withAlpha(WHITE, 0.14f).argb,
            onSecondaryContainer = WHITE.argb,
            background = INK.argb,
            onBackground = WHITE.argb,
            surfaceContainer = withAlpha(WHITE, 0.10f).argb,
            onSurface = WHITE.argb,
            onSurfaceVariant = withAlpha(WHITE, 0.70f).argb,
        )
    }

    private fun MaterialScope.tonalColors(): ButtonColors = with(ButtonDefaults) { filledTonalButtonColors() }

    private fun launchApp(): ActionBuilders.LaunchAction = ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(MainActivity::class.java.name)
                .build(),
        )
        .build()

    private fun send(command: WearCommand) {
        scope.launch { link.send(command) }
    }

    private fun drawable(resId: Int) = ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(resId).build())
        .build()

    private fun inline(jpeg: ByteArray) = ResourceBuilders.ImageResource.Builder()
        .setInlineResource(
            ResourceBuilders.InlineImageResource.Builder()
                .setData(jpeg)
                .setWidthPx(WearPaths.ART_PIXELS)
                .setHeightPx(WearPaths.ART_PIXELS)
                .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                .build(),
        )
        .build()

    /** Change avec la pochette : le système redemande alors les images. */
    private fun resourcesVersion(stored: fr.synxio.player.watch.data.StoredState): String =
        "2-${stored.state?.mediaId.orEmpty().hashCode()}-${stored.art?.size ?: 0}"

    companion object {
        private const val ID_OPEN = "open"
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
        private const val RES_ART = "art"
        private const val RES_BACKDROP = "backdrop"

        private const val SMALL_BUTTON_DP = 36f
        private const val ART_DP = 44f
        private const val INK = 0xFF0C0A10.toInt()
        private const val EMBER = 0xFFFF6A3D.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        /** Voile encre à 70 % sur la pochette floutée : lisible, mais la teinte reste. */
        private const val INK_VEIL = 0xB30C0A10.toInt()

        private fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

        private fun luminance(color: Int): Float {
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        /** Pour `WearSurfaces` : redemande la tuile au système. */
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(SynxioTileService::class.java)
        }
    }
}
