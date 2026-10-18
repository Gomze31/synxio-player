package fr.synxio.player.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
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
import fr.synxio.player.playback.PlaybackService
import fr.synxio.player.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Briques communes aux deux widgets Synxio.
 *
 * Un widget passe par RemoteViews : pas de flou en direct, pas d'animation, des mises
 * à jour comptées. Le « verre » est donc un fond pré-rendu (pochette floutée + voile,
 * voir WidgetArtRenderer), et la progression avance à chaque publication du service.
 */

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
    fun playerConnection(): PlayerConnection
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** Ce que le widget affiche quand son propre état Glance est encore vide. */
internal class WidgetFallback(val snapshot: WidgetSnapshot, val dark: Boolean, val amoled: Boolean)

/**
 * Dernière photo connue et thème de l'application, lus avant la composition.
 *
 * Un widget fraîchement posé n'a pas encore d'état Glance : sans repli, il affichait
 * « Touchez lecture » alors qu'un titre était en pause. Écrire cet état depuis
 * `provideGlance` ne suffisait pas : la session l'avait déjà lu.
 */
internal suspend fun widgetFallback(context: Context): WidgetFallback {
    val updater = context.widgetEntryPoint().widgetUpdater()
    val (dark, amoled) = updater.theme()
    return WidgetFallback(WidgetSnapshotStore.load(context), dark, amoled)
}

/** État affiché : celui publié au widget, sinon le repli ; jamais « en lecture » sans service. */
internal fun resolveSnapshot(prefs: androidx.datastore.preferences.core.Preferences, fallback: WidgetFallback): WidgetSnapshot {
    val own = WidgetSnapshot.from(prefs)
    val snapshot = if (own.isEmpty) fallback.snapshot else own
    // Processus tué pendant la lecture : la photo dit « en lecture » alors que rien ne joue.
    return if (snapshot.isPlaying && !PlaybackService.isRunning) snapshot.copy(isPlaying = false) else snapshot
}

/** Couleurs d'un widget : thème de l'app, teinté par la pochette quand elle en a une. */
internal class WidgetPalette(dark: Boolean, amoled: Boolean, accentArgb: Int, val hasBackdrop: Boolean) {
    private val baseAccent = if (dark) Color(0xFFB59BFF) else Color(0xFF6A3CD1)
    val accentColor = if (accentArgb != 0) Color(accentArgb) else baseAccent
    val background = when {
        amoled -> Color(0xFF000000)
        dark -> Color(0xFF14131B)
        else -> Color(0xFFF6F3FB)
    }
    val onSurface = if (dark) Color(0xFFF5F3FA) else Color(0xFF17161C)
    val onSurfaceVariant = if (dark) Color(0xFFC9C4D6) else Color(0xFF4F4A5A)
    val onAccent = if (accentColor.luminance() > 0.45f) Color(0xFF111015) else Color.White
    val track = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
    /** Surface translucide des pastilles (pochette absente, boutons secondaires). */
    val glass = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)

    val text = ColorProvider(onSurface)
    val textVariant = ColorProvider(onSurfaceVariant)
    val accent = ColorProvider(accentColor)

    companion object {
        fun of(s: WidgetSnapshot, dark: Boolean, amoled: Boolean) =
            WidgetPalette(dark, amoled, s.accent, s.backdropPath.isNotEmpty())
    }
}

/** Fond du widget : pochette floutée sous voile, ou aplat du thème sans pochette. */
@Composable
internal fun WidgetSurface(
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberBitmap(snapshot.backdropPath)
    Box(
        modifier = modifier
            .fillMaxSize()
            .cornerRadius(WidgetRadius)
            .background(palette.background),
    ) {
        if (backdrop != null) {
            Image(
                provider = ImageProvider(backdrop),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        content()
    }
}

/** Pochette du morceau, ou pastille translucide avec une note quand il n'y en a pas. */
@Composable
internal fun WidgetArtwork(snapshot: WidgetSnapshot, palette: WidgetPalette, size: Dp, radius: Dp) {
    val art = rememberBitmap(snapshot.artPath)
    if (art != null) {
        Image(
            provider = ImageProvider(art),
            contentDescription = "Pochette",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.size(size).cornerRadius(radius).clickable(openPlayerAction()),
        )
    } else {
        Box(
            modifier = GlanceModifier.size(size).cornerRadius(radius).background(palette.glass)
                .clickable(openPlayerAction()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.accent),
                modifier = GlanceModifier.size(size * 0.42f),
            )
        }
    }
}

@Composable
internal fun WidgetTitle(text: String, palette: WidgetPalette, fontSize: TextUnit) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = palette.text, fontSize = fontSize, fontWeight = FontWeight.Bold),
    )
}

@Composable
internal fun WidgetSubtitle(text: String, palette: WidgetPalette, fontSize: TextUnit = 13.sp) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = palette.textVariant, fontSize = fontSize),
    )
}

@Composable
internal fun WidgetProgress(snapshot: WidgetSnapshot, palette: WidgetPalette) {
    LinearProgressIndicator(
        progress = snapshot.progressAt(System.currentTimeMillis()),
        color = palette.accent,
        backgroundColor = ColorProvider(palette.track),
        modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
    )
}

/** Bouton lecture/pause : disque d'accent, icône contrastée. */
@Composable
internal fun PlayPauseButton(isPlaying: Boolean, palette: WidgetPalette, size: Dp) {
    val label = if (isPlaying) "Pause" else "Lecture"
    Box(
        modifier = GlanceModifier
            .size(size)
            .clickable(mediaKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_circle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.accent),
            modifier = GlanceModifier.fillMaxSize(),
        )
        Image(
            provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            contentDescription = label,
            colorFilter = ColorFilter.tint(ColorProvider(palette.onAccent)),
            modifier = GlanceModifier.size(size * 0.5f),
        )
    }
}

/** Bouton icône secondaire, zone tactile d'au moins [size]. */
@Composable
internal fun WidgetIconButton(
    iconRes: Int,
    description: String,
    action: Action,
    tint: ColorProvider,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,
) {
    Box(
        modifier = GlanceModifier.size(size).cornerRadius(size / 2).clickable(action)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

/** Charge une image préparée ; mémorisée par chemin (un nouveau morceau = un nouveau fichier). */
@Composable
private fun rememberBitmap(path: String): Bitmap? = remember(path) {
    if (path.isEmpty()) null
    else runCatching { File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }.getOrNull()
}

internal val WidgetRadius = 24.dp

/** Ouvre directement le lecteur plein écran (et non l'accueil de l'application). */
@Composable
internal fun openPlayerAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_PLAYER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )
}

internal fun mediaKeyAction(keyCode: Int): Action =
    actionRunCallback<MediaKeyAction>(actionParametersOf(MediaKeyAction.KEY_CODE to keyCode))

internal fun commandAction(command: String): Action =
    actionRunCallback<PlayerCommandAction>(actionParametersOf(PlayerCommandAction.COMMAND to command))

/**
 * Lecture/pause, précédent, suivant.
 *
 * Service vivant : la commande passe par le contrôleur de l'application, comme un appui
 * dans l'app. Le récepteur de touches média de Media3 ignore en effet toute touche autre
 * que « lecture » (pour ne pas démarrer un service au premier plan à tort) : l'ancien
 * widget envoyait précédent/suivant par ce chemin, et ces boutons ne faisaient rien.
 *
 * Service arrêté : lecture/pause passe par ce récepteur, qui démarre le service et
 * reprend la file sauvegardée (`onPlaybackResumption`).
 */
class MediaKeyAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val keyCode = parameters[KEY_CODE] ?: return
        if (PlaybackService.isRunning || keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            val connection = context.widgetEntryPoint().playerConnection()
            withContext(Dispatchers.Main) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> connection.togglePlayPause()
                    KeyEvent.KEYCODE_MEDIA_NEXT -> connection.next()
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> connection.previous()
                }
            }
            return
        }
        runCatching {
            for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        component = ComponentName(context, androidx.media3.session.MediaButtonReceiver::class.java)
                        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, keyCode))
                    }
                )
            }
        }.onFailure { android.util.Log.w("SynxioWidget", "Touche média non envoyée", it) }
    }

    companion object {
        val KEY_CODE = ActionParameters.Key<Int>("key_code")
    }
}

/**
 * Aléatoire, répétition et favori n'ont pas de touche média : ils passent par la session
 * via le contrôleur de l'application, qui met la commande en attente s'il se connecte.
 */
class PlayerCommandAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[COMMAND] ?: return
        val connection = context.widgetEntryPoint().playerConnection()
        // MediaController exige le thread sur lequel il a été créé : le principal.
        withContext(Dispatchers.Main) {
            when (command) {
                SHUFFLE -> connection.toggleShuffle()
                REPEAT -> connection.cycleRepeat()
                FAVORITE -> connection.toggleFavorite()
            }
        }
    }

    companion object {
        val COMMAND = ActionParameters.Key<String>("command")
        const val SHUFFLE = "shuffle"
        const val REPEAT = "repeat"
        const val FAVORITE = "favorite"
    }
}

/**
 * Demande au lanceur d'épingler un widget Synxio (Android 8+).
 *
 * Le système affiche sa propre confirmation : rien n'est ajouté sans l'accord de
 * l'utilisateur. Faux si le lanceur ne sait pas épingler.
 */
fun requestPinWidget(context: Context, large: Boolean): Boolean {
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    val provider = ComponentName(
        context,
        if (large) SynxioLargeWidgetReceiver::class.java else SynxioWidgetReceiver::class.java,
    )
    return runCatching { manager.requestPinAppWidget(provider, null, null) }.getOrDefault(false)
}
