package fr.synxio.player.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.synxio.player.MainActivity
import fr.synxio.player.R
import fr.synxio.player.data.db.QueueDao
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.playback.PlaybackService

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

    /** Sous une certaine largeur, la pochette mangerait la place des commandes. */
    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun musicRepository(): MusicRepository
        fun queueDao(): QueueDao
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
            ?.let { entryPoint.musicRepository().songById(it.songId) }
        val isPlaying = state?.isPlaying == true
        val artwork = song?.let { loadArtwork(context, it) }

        provideContent {
            GlanceTheme {
                val compact = LocalSize.current.width < ARTWORK_MIN_WIDTH

                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(20.dp)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (artwork != null && !compact) {
                        Image(
                            provider = ImageProvider(artwork),
                            contentDescription = null,
                            modifier = GlanceModifier.size(64.dp).cornerRadius(12.dp),
                        )
                        Spacer(GlanceModifier.width(12.dp))
                    }

                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (song == null) {
                            Text(
                                text = "Synxio — aucune lecture",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 14.sp,
                                ),
                            )
                        } else {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text = song.displayArtist,
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                ),
                            )
                            Spacer(GlanceModifier.size(8.dp))
                            Controls(isPlaying)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Controls(isPlaying: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetButton(
                R.drawable.ic_widget_previous,
                "Précédent",
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            )
            Spacer(GlanceModifier.width(10.dp))
            WidgetButton(
                // L'icône reflète l'état réel : un bouton « lecture » affiché pendant la
                // lecture laisse croire que le widget n'a pas pris en compte l'appui.
                iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                description = if (isPlaying) "Pause" else "Lecture",
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            )
            Spacer(GlanceModifier.width(10.dp))
            WidgetButton(R.drawable.ic_widget_next, "Suivant", KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    @Composable
    private fun WidgetButton(iconRes: Int, description: String, keyCode: Int) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier
                .size(34.dp)
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

    private companion object {
        /** En dessous, la pochette prendrait la place des commandes. */
        val ARTWORK_MIN_WIDTH = 220.dp
        const val ARTWORK_PIXELS = 256
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
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(context, PlaybackService::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        runCatching { context.startService(intent) }

        // Le service persiste son état de façon asynchrone : sans ce délai, on redessine
        // à partir de l'état d'avant l'appui et le widget paraît ne pas répondre.
        kotlinx.coroutines.delay(SETTLE_MS)
        SynxioWidget().updateAll(context)
    }

    companion object {
        val KEY_CODE = ActionParameters.Key<Int>("key_code")
        private const val SETTLE_MS = 350L
    }
}

class SynxioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynxioWidget()
}
