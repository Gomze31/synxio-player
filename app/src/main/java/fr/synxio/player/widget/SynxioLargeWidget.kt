package fr.synxio.player.widget

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.media3.common.Player
import fr.synxio.player.R

/**
 * Widget étendu : grande pochette, métadonnées, progression et commandes complètes
 * (aléatoire, précédent, lecture, suivant, répétition, favori, ouverture du lecteur).
 *
 * Deux dispositions selon la forme posée : pochette au-dessus quand le widget est haut,
 * pochette à gauche quand il est large. Aucune n'écrase les commandes.
 */
class SynxioLargeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fallback = widgetFallback(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val snapshot = resolveSnapshot(prefs, fallback)
            val palette = WidgetPalette.of(
                snapshot,
                dark = prefs[WidgetSnapshot.Keys.DARK] ?: fallback.dark,
                amoled = prefs[WidgetSnapshot.Keys.AMOLED] ?: fallback.amoled,
            )
            WidgetSurface(snapshot, palette, GlanceModifier.clickable(openPlayerAction())) {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val size = LocalSize.current
                    val inner = size.width - 32.dp
                    // Hauteur prise par le titre, la progression et les commandes.
                    val stackedArt = size.height - 32.dp - CONTROLS_BLOCK
                    when {
                        snapshot.isEmpty -> EmptyContent(palette)
                        stackedArt >= inner * 0.55f -> StackedHeader(snapshot, palette, minOf(stackedArt, inner, 240.dp))
                        else -> SideHeader(snapshot, palette, (size.height - 32.dp - 60.dp - 16.dp).coerceIn(56.dp, 150.dp))
                    }
                    if (!snapshot.isEmpty) {
                        if (!snapshot.live && snapshot.durationMs > 0) {
                            Spacer(GlanceModifier.height(12.dp))
                            WidgetProgress(snapshot, palette)
                        }
                        Spacer(GlanceModifier.height(8.dp))
                        FullControls(snapshot, palette)
                    }
                }
            }
        }
    }

    /** Pochette centrée au-dessus, titre et favori en dessous. */
    @Composable
    private fun StackedHeader(s: WidgetSnapshot, palette: WidgetPalette, artSide: Dp) {
        Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WidgetArtwork(s, palette, artSide, radius = 20.dp)
        }
        Spacer(GlanceModifier.height(12.dp))
        TitleRow(s, palette)
    }

    /** Pochette à gauche, métadonnées à droite : pour les widgets plus larges que hauts. */
    @Composable
    private fun SideHeader(s: WidgetSnapshot, palette: WidgetPalette, artSide: Dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetArtwork(s, palette, artSide, radius = 20.dp)
            Spacer(GlanceModifier.width(14.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle(s.title, palette, 18.sp)
                WidgetSubtitle(s.artist, palette, 14.sp)
                if (s.album.isNotBlank()) WidgetSubtitle(s.album, palette, 12.sp)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FavoriteButton(s, palette)
                    OpenButton(palette)
                }
            }
        }
    }

    @Composable
    private fun TitleRow(s: WidgetSnapshot, palette: WidgetPalette) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle(s.title, palette, 17.sp)
                WidgetSubtitle(s.artist, palette, 13.sp)
            }
            FavoriteButton(s, palette)
            OpenButton(palette)
        }
    }

    @Composable
    private fun FavoriteButton(s: WidgetSnapshot, palette: WidgetPalette) {
        WidgetIconButton(
            iconRes = if (s.favorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border,
            description = if (s.favorite) "Retirer des favoris" else "Ajouter aux favoris",
            action = commandAction(PlayerCommandAction.FAVORITE),
            tint = if (s.favorite) palette.accent else palette.textVariant,
            size = 40.dp,
            iconSize = 22.dp,
        )
    }

    @Composable
    private fun OpenButton(palette: WidgetPalette) {
        WidgetIconButton(
            iconRes = R.drawable.ic_widget_open,
            description = "Ouvrir le lecteur",
            action = openPlayerAction(),
            tint = palette.textVariant,
            size = 40.dp,
            iconSize = 24.dp,
        )
    }

    @Composable
    private fun FullControls(s: WidgetSnapshot, palette: WidgetPalette) {
        val repeatOn = s.repeatMode != Player.REPEAT_MODE_OFF
        // Cinq commandes sur la largeur : elles rétrécissent plutôt que d'être rognées.
        val slot = (LocalSize.current.width - 32.dp) / 5
        val playSize = minOf(56.dp, slot)
        val buttonSize = minOf(44.dp, slot)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Slot {
                WidgetIconButton(
                    R.drawable.ic_widget_shuffle,
                    if (s.shuffle) "Lecture aléatoire activée" else "Lecture aléatoire désactivée",
                    commandAction(PlayerCommandAction.SHUFFLE),
                    if (s.shuffle) palette.accent else palette.textVariant,
                    size = buttonSize,
                    iconSize = 22.dp,
                )
            }
            Slot {
                WidgetIconButton(R.drawable.ic_widget_previous, "Titre précédent",
                    mediaKeyAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS), palette.text, size = buttonSize, iconSize = 28.dp)
            }
            Slot { PlayPauseButton(s.isPlaying, palette, playSize) }
            Slot {
                WidgetIconButton(R.drawable.ic_widget_next, "Titre suivant",
                    mediaKeyAction(KeyEvent.KEYCODE_MEDIA_NEXT), palette.text, size = buttonSize, iconSize = 28.dp)
            }
            Slot {
                WidgetIconButton(
                    if (s.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_widget_repeat_one else R.drawable.ic_widget_repeat,
                    when (s.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Répétition du titre"
                        Player.REPEAT_MODE_ALL -> "Répétition de la file"
                        else -> "Répétition désactivée"
                    },
                    commandAction(PlayerCommandAction.REPEAT),
                    if (repeatOn) palette.accent else palette.textVariant,
                    size = buttonSize,
                    iconSize = 22.dp,
                )
            }
        }
    }

    @Composable
    private fun EmptyContent(palette: WidgetPalette) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetTitle("Synxio Player", palette, 18.sp)
            WidgetSubtitle("Touchez lecture pour reprendre votre écoute", palette)
            Spacer(GlanceModifier.height(16.dp))
            PlayPauseButton(false, palette, 60.dp)
        }
    }

    @Composable
    private fun RowScope.Slot(content: @Composable () -> Unit) {
        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { content() }
    }

    private companion object {
        /** Titre (≈ 44 dp) + progression (≈ 16 dp) + commandes (≈ 64 dp) + marges. */
        val CONTROLS_BLOCK = 140.dp
    }
}

class SynxioLargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynxioLargeWidget()
}
