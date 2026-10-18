package fr.synxio.player.widget

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import fr.synxio.player.R

/**
 * Widget compact : pochette, titre, artiste, progression et commandes de base.
 *
 * Il ne se connecte pas à la session — un `MediaController` ne survit pas à un widget —
 * mais affiche l'état que le service lui publie (voir [WidgetUpdater]) et pilote la
 * lecture par touches média, exactement comme un casque Bluetooth.
 */
class SynxioWidget : GlanceAppWidget() {

    /** La mise en page suit la taille réelle posée par l'utilisateur. */
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
                val size = LocalSize.current
                when {
                    snapshot.isEmpty -> EmptyContent(palette)
                    size.height < SLIM_HEIGHT -> SlimContent(snapshot, palette)
                    else -> RegularContent(snapshot, palette)
                }
            }
        }
    }

    @Composable
    private fun RegularContent(s: WidgetSnapshot, palette: WidgetPalette) {
        val size = LocalSize.current
        // La pochette ne prend jamais la place des commandes : au plus 38 % de la largeur,
        // et la colonne de droite garde de quoi aligner précédent, lecture et suivant.
        val artSide = if (size.width >= ARTWORK_MIN_WIDTH) {
            minOf(size.height - 28.dp, size.width * 0.38f, 136.dp).coerceAtLeast(56.dp)
        } else 0.dp
        val columnWidth = size.width - 28.dp - if (artSide > 0.dp) artSide + 14.dp else 0.dp
        val playSize = if (size.height < 150.dp) 42.dp else 48.dp
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (artSide > 0.dp) {
                WidgetArtwork(s, palette, artSide, radius = 18.dp)
                Spacer(GlanceModifier.width(14.dp))
            }
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTitle(s.title, palette, 16.sp)
                WidgetSubtitle(s.artist, palette)
                if (!s.live && s.durationMs > 0) {
                    Spacer(GlanceModifier.height(8.dp))
                    WidgetProgress(s, palette)
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (columnWidth >= FAVORITE_MIN_COLUMN) {
                        Slot {
                            WidgetIconButton(
                                iconRes = if (s.favorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border,
                                description = if (s.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                                action = commandAction(PlayerCommandAction.FAVORITE),
                                tint = if (s.favorite) palette.accent else palette.textVariant,
                                size = 40.dp,
                                iconSize = 22.dp,
                            )
                        }
                    }
                    Slot {
                        WidgetIconButton(R.drawable.ic_widget_previous, "Titre précédent",
                            mediaKeyAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS), palette.text, size = 40.dp)
                    }
                    Slot { PlayPauseButton(s.isPlaying, palette, playSize) }
                    Slot {
                        WidgetIconButton(R.drawable.ic_widget_next, "Titre suivant",
                            mediaKeyAction(KeyEvent.KEYCODE_MEDIA_NEXT), palette.text, size = 40.dp)
                    }
                }
            }
        }
    }

    /** Très peu de hauteur : une seule ligne, l'essentiel. */
    @Composable
    private fun SlimContent(s: WidgetSnapshot, palette: WidgetPalette) {
        val size = LocalSize.current
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (size.width >= 200.dp) {
                WidgetArtwork(s, palette, (size.height - 16.dp).coerceIn(32.dp, 52.dp), radius = 12.dp)
                Spacer(GlanceModifier.width(10.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle(s.title, palette, 14.sp)
                WidgetSubtitle(s.artist, palette, 12.sp)
            }
            PlayPauseButton(s.isPlaying, palette, 40.dp)
            WidgetIconButton(R.drawable.ic_widget_next, "Titre suivant",
                mediaKeyAction(KeyEvent.KEYCODE_MEDIA_NEXT), palette.text, size = 40.dp, iconSize = 24.dp)
        }
    }

    @Composable
    private fun EmptyContent(palette: WidgetPalette) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle("Synxio Player", palette, 16.sp)
                WidgetSubtitle("Touchez lecture pour reprendre", palette)
            }
            PlayPauseButton(false, palette, 48.dp)
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Slot(content: @Composable () -> Unit) {
        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { content() }
    }

    private companion object {
        val SLIM_HEIGHT = 96.dp
        /** En dessous, la pochette prendrait la place des commandes. */
        val ARTWORK_MIN_WIDTH = 220.dp
        /** Largeur de colonne qui loge le cœur en plus des trois commandes. */
        val FAVORITE_MIN_COLUMN = 196.dp
    }
}

class SynxioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynxioWidget()
}
