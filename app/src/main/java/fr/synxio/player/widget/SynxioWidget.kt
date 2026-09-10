package fr.synxio.player.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.playback.PlaybackService
import androidx.glance.action.actionStartActivity

/**
 * Widget d'écran d'accueil.
 *
 * Il ne se connecte pas à la session média (un `MediaController` ne survit pas à un
 * widget) : il lit l'état persisté en base et pilote la lecture par intents de
 * boutons média, exactement comme un casque Bluetooth.
 */
class SynxioWidget : GlanceAppWidget() {

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

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(20.dp)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = song.displayArtist,
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp,
                            ),
                        )
                        Spacer(GlanceModifier.size(10.dp))
                        Controls()
                    }
                }
            }
        }
    }

    @Composable
    private fun Controls() {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetButton(R.drawable.ic_widget_previous, "Précédent", KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Spacer(GlanceModifier.width(12.dp))
            WidgetButton(R.drawable.ic_widget_play_pause, "Lecture/Pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Spacer(GlanceModifier.width(12.dp))
            WidgetButton(R.drawable.ic_widget_next, "Suivant", KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    @Composable
    private fun WidgetButton(iconRes: Int, description: String, keyCode: Int) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier
                .size(36.dp)
                .clickable(
                    actionRunCallback<MediaKeyAction>(
                        actionParametersOf(MediaKeyAction.KEY_CODE to keyCode)
                    )
                ),
        )
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
        SynxioWidget().updateAll(context)
    }

    companion object {
        val KEY_CODE = ActionParameters.Key<Int>("key_code")
    }
}

class SynxioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SynxioWidget()
}
