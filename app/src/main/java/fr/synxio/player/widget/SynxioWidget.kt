package fr.synxio.player.widget

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import fr.synxio.player.R

/**
 * Widget compact : pochette, titre, artiste et commandes de base, sur une scène Lumen.
 *
 * Il ne se connecte pas à la session — un `MediaController` ne survit pas à un widget —
 * mais affiche l'état que le service lui publie (voir [WidgetUpdater]) et pilote la
 * lecture par le contrôleur de l'app, ou par touche média quand le service est arrêté.
 */
class SynxioWidget : GlanceAppWidget() {

    /** La mise en page suit la taille réelle posée par l'utilisateur. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fallback = widgetFallback(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val snapshot = resolveSnapshot(prefs, fallback)
            val palette = WidgetPalette.of(snapshot, resolveTheme(prefs, fallback))
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
        // Budget vertical : titre + artiste ≈ 38 dp, barre 12 dp, commandes 44 à 52 dp.
        // Sur deux rangées basses (≈ 110 dp), la barre cède sa place et la marge se resserre.
        val cramped = size.height < CRAMPED_HEIGHT
        val roomy = size.height >= ROOMY_HEIGHT
        val pad = if (cramped) 12.dp else 16.dp
        val artSide = if (size.width >= ARTWORK_MIN_WIDTH) {
            minOf(size.height - pad * 2, size.width * 0.38f, 140.dp).coerceAtLeast(56.dp)
        } else 0.dp
        val columnWidth = size.width - pad * 2 - if (artSide > 0.dp) artSide + 14.dp else 0.dp
        // 48 dp de cible tactile dès que la hauteur le permet.
        val playSize = if (roomy) 52.dp else 44.dp
        val buttonSize = if (roomy) 48.dp else 40.dp
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (artSide > 0.dp) {
                WidgetArtwork(s, palette, artSide)
                Spacer(GlanceModifier.width(14.dp))
            }
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (size.height >= EYEBROW_HEIGHT) {
                    WidgetEyebrow(eyebrowFor(s), palette)
                    Spacer(GlanceModifier.height(2.dp))
                }
                WidgetTitle(s.title, palette, if (roomy) 17.sp else 16.sp)
                WidgetSubtitle(s.artist, palette)
                if (!cramped) WidgetProgressSlot(s, palette, top = 8.dp)
                Spacer(GlanceModifier.height(if (roomy) 8.dp else if (cramped) 2.dp else 4.dp))
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
                                size = buttonSize,
                                iconSize = 22.dp,
                            )
                        }
                    }
                    Slot {
                        WidgetIconButton(R.drawable.ic_widget_previous, "Titre précédent",
                            mediaKeyAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS), palette.text, size = buttonSize, iconSize = 26.dp)
                    }
                    Slot { PlayPauseButton(s.isPlaying, palette, playSize) }
                    Slot {
                        WidgetIconButton(R.drawable.ic_widget_next, "Titre suivant",
                            mediaKeyAction(KeyEvent.KEYCODE_MEDIA_NEXT), palette.text, size = buttonSize, iconSize = 26.dp)
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
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (size.width >= 200.dp) {
                WidgetArtwork(s, palette, (size.height - 16.dp).coerceIn(32.dp, 52.dp))
                Spacer(GlanceModifier.width(10.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle(s.title, palette, 14.sp)
                WidgetSubtitle(s.artist, palette, 12.sp)
            }
            Spacer(GlanceModifier.width(4.dp))
            PlayPauseButton(s.isPlaying, palette, 42.dp)
            WidgetIconButton(R.drawable.ic_widget_next, "Titre suivant",
                mediaKeyAction(KeyEvent.KEYCODE_MEDIA_NEXT), palette.text, size = 44.dp, iconSize = 24.dp)
        }
    }

    @Composable
    private fun EmptyContent(palette: WidgetPalette) {
        val context = LocalContext.current
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetEyebrow(context.getString(R.string.widget_eyebrow_brand), palette)
                Spacer(GlanceModifier.height(2.dp))
                WidgetTitle(context.getString(R.string.widget_empty_title), palette, 16.sp)
                WidgetSubtitle(context.getString(R.string.widget_empty_subtitle), palette)
            }
            Spacer(GlanceModifier.width(8.dp))
            PlayPauseButton(false, palette, 52.dp)
        }
    }

    @Composable
    private fun RowScope.Slot(content: @Composable () -> Unit) {
        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { content() }
    }

    private companion object {
        val SLIM_HEIGHT = 96.dp
        /** En dessous, pas de barre de progression : les commandes passent avant. */
        val CRAMPED_HEIGHT = 130.dp
        /** À partir de là, cibles tactiles de 48 dp et titre plus grand. */
        val ROOMY_HEIGHT = 150.dp
        /** Hauteur qui loge aussi le surtitre d'état au-dessus du titre. */
        val EYEBROW_HEIGHT = 168.dp
        /** En dessous, la pochette prendrait la place des commandes. */
        val ARTWORK_MIN_WIDTH = 220.dp
        /** Largeur de colonne qui loge le cœur en plus des trois commandes. */
        val FAVORITE_MIN_COLUMN = 196.dp
    }
}

class SynxioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynxioWidget()
}
