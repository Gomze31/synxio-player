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
import androidx.glance.text.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class SynxioLargeWidget : GlanceAppWidget() {

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
                
                val backdrop = entryPoint.artRenderer().render(
                    widthPx = (size.width.value * density).toInt(),
                    heightPx = (size.height.value * density).toInt(),
                    artwork = artwork,
                    bands = bands,
                    dark = palette.dark,
                    showWave = settings.widgetShowWave,
                    glassOpacity = settings.widgetGlassOpacity,
                    waveTint = settings.widgetWaveTint,
                )

                Box(modifier = GlanceModifier.fillMaxSize()) {
                    if (backdrop != null) {
                        Image(
                            provider = ImageProvider(backdrop),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp),
                        )
                    }

                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .then(
                                if (backdrop == null) {
                                    GlanceModifier.background(palette.background)
                                } else {
                                    GlanceModifier
                                }
                            )
                            .cornerRadius(24.dp)
                            .padding(16.dp)
                            .clickable(actionStartActivity<MainActivity>()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (artwork != null) {
                            Image(
                                provider = ImageProvider(artwork),
                                contentDescription = null,
                                modifier = GlanceModifier
                                    .size(140.dp)
                                    .cornerRadius(18.dp),
                            )
                            Spacer(GlanceModifier.height(16.dp))
                        }

                        if (song == null) {
                            Text(
                                text = "Aucune lecture",
                                style = TextStyle(color = palette.onSurface, fontSize = 16.sp),
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = GlanceModifier
                                    .background(ColorProvider(if (palette.dark) Color(0x33FFFFFF) else Color(0x22000000)))
                                    .cornerRadius(20.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = palette.onSurface,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                )
                                Spacer(GlanceModifier.height(4.dp))
                                Text(
                                    text = song.displayArtist,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = palette.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                )
                                Spacer(GlanceModifier.height(12.dp))
                                Controls(isPlaying, palette)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Controls(isPlaying: Boolean, palette: Palette) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WidgetButton(
                R.drawable.ic_widget_previous,
                "Précédent",
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                palette,
                isPrimary = false,
            )
            Spacer(GlanceModifier.width(16.dp))
            WidgetButton(
                iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                description = if (isPlaying) "Pause" else "Lecture",
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                palette = palette,
                isPrimary = true,
            )
            Spacer(GlanceModifier.width(16.dp))
            WidgetButton(
                R.drawable.ic_widget_next,
                "Suivant",
                KeyEvent.KEYCODE_MEDIA_NEXT,
                palette,
                isPrimary = false,
            )
        }
    }

    @Composable
    private fun WidgetButton(
        iconRes: Int,
        description: String,
        keyCode: Int,
        palette: Palette,
        isPrimary: Boolean = false,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(if (isPrimary) palette.accent else palette.onSurface),
            modifier = GlanceModifier
                .size(if (isPrimary) 56.dp else 44.dp)
                .padding(if (isPrimary) 8.dp else 10.dp)
                .clickable(
                    actionRunCallback<MediaKeyAction>(
                        actionParametersOf(MediaKeyAction.KEY_CODE to keyCode)
                    )
                ),
        )
    }

    private fun loadArtwork(context: Context, song: Song): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(song.artworkUri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > 256) sample *= 2 // Plus grand pour le grand widget

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(song.artworkUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()

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
}

class SynxioLargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynxioLargeWidget()
}

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
