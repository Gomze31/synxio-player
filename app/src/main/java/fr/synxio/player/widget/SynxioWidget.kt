package fr.synxio.player.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.synxio.player.MainActivity
import fr.synxio.player.R
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.QueueDao
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.ThemeMode
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.SimilarityRepository
import fr.synxio.player.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Widget d'écran d'accueil.
 *
 * Il ne se connecte pas à la session média — un `MediaController` ne survit pas à un
 * widget — mais lit l'état persisté en base et pilote la lecture par intents de boutons
 * média, exactement comme un casque Bluetooth.
 *
 * Le corollaire : le widget ne sait pas de lui-même que la lecture a changé. C'est le
 * service de lecture qui le réveille à chaque persistance d'état. Sans cela, changer de
 * morceau depuis l'application ou le casque laissait le widget figé sur le titre
 * précédent — `updatePeriodMillis` valant zéro, rien ne le rafraîchissait non plus.
 */
class SynxioWidget : GlanceAppWidget() {

    /** On adapte la mise en page à la taille réelle posée par l'utilisateur. */
    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun musicRepository(): MusicRepository
        fun queueDao(): QueueDao
        fun settingsRepository(): SettingsRepository
        fun similarityRepository(): SimilarityRepository
        fun artRenderer(): WidgetArtRenderer
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )

        val state = entryPoint.queueDao().state()
        val queue = entryPoint.queueDao().queue().sortedBy { it.position }
        val song = state?.currentIndex
            ?.let { queue.getOrNull(it) }
            ?.let { entryPoint.musicRepository().resolveSong(it.songId) }
        val isPlaying = state?.isPlaying == true
        val artwork = song?.let { loadArtwork(context, it) }

        // Le widget suit le thème choisi DANS Synxio, pas celui du système.
        //
        // Avec un mode nuit système réglé sur « auto », un fond clair s'affichait en
        // pleine journée sur un fond d'écran sombre et une application en AMOLED : le
        // widget était le seul élément clair de l'écran d'accueil. Suivre le réglage de
        // l'application est ce qu'on attend d'un widget de Synxio.
        val settings = entryPoint.settingsRepository().settings.first()
        val theme = settings.themeMode
        val palette = Palette(
            dark = when (theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
                ThemeMode.SYSTEM -> context.isSystemInDarkMode()
            },
            amoled = theme == ThemeMode.AMOLED,
        )

        val bands = song?.let { entryPoint.similarityRepository().featuresFor(it) }
        val density = context.resources.displayMetrics.density

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val compact = size.width < ARTWORK_MIN_WIDTH

                Box(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(GlanceTheme.colors.widgetBackground)
                            .cornerRadius(24.dp)
                            .padding(16.dp)
                            .clickable(actionStartActivity<MainActivity>()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (artwork != null && !compact) {
                            Image(
                                provider = ImageProvider(artwork),
                                contentDescription = null,
                                modifier = GlanceModifier.fillMaxHeight().width(size.height - 32.dp).cornerRadius(16.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(GlanceModifier.width(16.dp))
                        }

                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (song == null) {
                                Text(
                                    text = "Aucune lecture",
                                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp),
                                )
                            } else {
                                Text(
                                    text = song.title,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Spacer(GlanceModifier.height(2.dp))
                                Text(
                                    text = song.displayArtist,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 14.sp,
                                    ),
                                )
                                Spacer(GlanceModifier.height(12.dp))
                                Controls(isPlaying)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Controls(isPlaying: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.padding(vertical = 4.dp)
        ) {
            WidgetButton(
                R.drawable.ic_widget_previous,
                "Précédent",
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                isPrimary = false,
            )
            Spacer(GlanceModifier.width(16.dp))
            WidgetButton(
                iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                description = if (isPlaying) "Pause" else "Lecture",
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                isPrimary = true,
            )
            Spacer(GlanceModifier.width(16.dp))
            WidgetButton(
                R.drawable.ic_widget_next,
                "Suivant",
                KeyEvent.KEYCODE_MEDIA_NEXT,
                isPrimary = false,
            )
        }
    }

    @Composable
    private fun WidgetButton(
        iconRes: Int,
        description: String,
        keyCode: Int,
        isPrimary: Boolean = false,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(if (isPrimary) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier
                .size(if (isPrimary) 48.dp else 40.dp)
                .clickable(
                    actionRunCallback<MediaKeyAction>(
                        actionParametersOf(MediaKeyAction.KEY_CODE to keyCode)
                    )
                ),
        )
    }

    /**
     * Charge la pochette, réduite avant décodage.
     *
     * Un widget transite par un `RemoteViews`, dont la transaction Binder est plafonnée
     * autour du mégaoctet : une pochette 1000×1000 décodée en ARGB pèse quatre
     * mégaoctets et ferait disparaître le widget sans erreur visible.
     */
    private fun loadArtwork(context: Context, song: Song): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(song.artworkUri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > ARTWORK_PIXELS) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(song.artworkUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()

    /** Couleurs du widget, dérivées du thème choisi dans l'application. */
    private class Palette(val dark: Boolean, amoled: Boolean) {
        val background = ColorProvider(
            when {
                amoled -> Color(0xFF000000)
                dark -> Color(0xFF15131C)
                else -> Color(0xFFF6F3FB)
            }
        )
        val onSurface = ColorProvider(if (dark) Color(0xFFF2EFFA) else Color(0xFF1B1A20))
        val onSurfaceVariant = ColorProvider(if (dark) Color(0xFFB9B3C7) else Color(0xFF5C5768))
        val accent = ColorProvider(if (dark) Color(0xFF9D8DF8) else Color(0xFF6C5CE7))
    }

    private companion object {
        /** En dessous, la pochette prendrait la place des commandes. */
        val ARTWORK_MIN_WIDTH = 220.dp
        const val ARTWORK_PIXELS = 128
    }
}

/** Envoie une touche média au service, comme le ferait un casque. */
class MediaKeyAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val keyCode = parameters[KEY_CODE] ?: return
        
        runCatching {
            val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(context, androidx.media3.session.MediaButtonReceiver::class.java)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            context.sendBroadcast(intentDown)
            
            val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(context, androidx.media3.session.MediaButtonReceiver::class.java)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            context.sendBroadcast(intentUp)
        }
    }

    companion object {
        val KEY_CODE = ActionParameters.Key<Int>("key_code")
    }
}

class SynxioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynxioWidget()
}

/** Vrai si le système est en mode sombre, pour le réglage « Système ». */
private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
