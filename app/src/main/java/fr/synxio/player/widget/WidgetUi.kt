package fr.synxio.player.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
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
import androidx.glance.color.ColorProvider as dayNight

/*
 * Briques communes aux deux widgets Synxio, version « Lumen ».
 *
 * Un widget passe par RemoteViews : pas de flou en direct, pas de dégradé, pas
 * d'animation, des mises à jour comptées. L'identité Lumen y est donc traduite avec ce
 * que le lanceur sait afficher : une scène unie (encre ou papier) éclairée par un halo
 * pré-rendu de la couleur de la pochette (voir WidgetArtRenderer), et la signature
 * braise → magenta portée par le disque du bouton lecture, un drawable dégradé.
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
internal class WidgetFallback(val snapshot: WidgetSnapshot, val theme: WidgetTheme)

/**
 * Dernière photo connue et thème de l'application, lus avant la composition.
 *
 * Un widget fraîchement posé n'a pas encore d'état Glance : sans repli, il affichait
 * « Touchez lecture » alors qu'un titre était en pause. Écrire cet état depuis
 * `provideGlance` ne suffisait pas : la session l'avait déjà lu. Les images manquantes
 * (aucun widget n'existait quand le morceau a commencé) sont préparées ici.
 */
internal suspend fun widgetFallback(context: Context): WidgetFallback {
    val updater = context.widgetEntryPoint().widgetUpdater()
    val snapshot = runCatching { updater.ensurePrepared(WidgetSnapshotStore.load(context)) }
        .getOrElse { WidgetSnapshotStore.load(context) }
    return WidgetFallback(snapshot, updater.themeMode())
}

/** État affiché : celui publié au widget, sinon le repli ; jamais « en lecture » sans service. */
internal fun resolveSnapshot(prefs: androidx.datastore.preferences.core.Preferences, fallback: WidgetFallback): WidgetSnapshot {
    val own = WidgetSnapshot.from(prefs)
    val snapshot = if (own.isEmpty) fallback.snapshot else own
    // Processus tué pendant la lecture : la photo dit « en lecture » alors que rien ne joue.
    return if (snapshot.isPlaying && !PlaybackService.isRunning) snapshot.copy(isPlaying = false) else snapshot
}

/** Thème publié au widget, sinon celui lu au moment de la composition. */
internal fun resolveTheme(prefs: androidx.datastore.preferences.core.Preferences, fallback: WidgetFallback): WidgetTheme =
    prefs[WidgetSnapshot.Keys.THEME]?.let { WidgetTheme.of(it) } ?: fallback.theme

/**
 * Couleurs d'un widget Lumen : encre #0C0A10 la nuit, papier #FAF6F1 le jour.
 *
 * En thème « système », chaque couleur est une paire jour/nuit : c'est le lanceur qui
 * choisit, au moment d'afficher, selon le mode sombre du téléphone. L'accent vient de la
 * pochette quand elle en donne un (deux variantes, chacune contrastée ≥ 4,5:1 sur sa
 * scène), sinon c'est la braise Synxio.
 */
internal class WidgetPalette(private val theme: WidgetTheme, accentDark: Int, accentLight: Int) {

    private fun pick(day: Color, night: Color): ColorProvider = when (theme) {
        WidgetTheme.SYSTEM -> dayNight(day = day, night = night)
        WidgetTheme.LIGHT -> ColorProvider(day)
        WidgetTheme.DARK, WidgetTheme.AMOLED -> ColorProvider(night)
    }

    private val accentNight = if (accentDark != 0) Color(accentDark) else EMBER
    private val accentDay = if (accentLight != 0) Color(accentLight) else EMBER_ON_PAPER

    val background = pick(PAPER, if (theme == WidgetTheme.AMOLED) Color.Black else INK)
    val text = pick(PAPER_TEXT, INK_TEXT)
    val textVariant = pick(PAPER_TEXT_VARIANT, INK_TEXT_VARIANT)
    val accent = pick(accentDay, accentNight)
    /** Pastille derrière une commande active (aléatoire, répétition). */
    val activeFill = pick(accentDay.copy(alpha = 0.14f), accentNight.copy(alpha = 0.18f))
    val track = pick(Color.Black.copy(alpha = 0.10f), Color.White.copy(alpha = 0.16f))
    /** Surface translucide (pochette absente). */
    val glass = pick(Color.Black.copy(alpha = 0.05f), Color.White.copy(alpha = 0.08f))
    /** Icône posée sur le disque braise → magenta : l'encre y contraste à plus de 7:1. */
    val onBrand = ColorProvider(INK)

    companion object {
        fun of(s: WidgetSnapshot, theme: WidgetTheme) = WidgetPalette(theme, s.accent, s.accentLight)

        private val INK = Color(0xFF0C0A10)
        private val PAPER = Color(0xFFFAF6F1)
        /** Blanc chaud et gris tiède : ≥ 4,5:1 sur l'encre même sous le halo. */
        private val INK_TEXT = Color(0xFFF6F1EC)
        private val INK_TEXT_VARIANT = Color(0xFFBDB4AF)
        private val PAPER_TEXT = Color(0xFF1A1418)
        private val PAPER_TEXT_VARIANT = Color(0xFF4F464A)
        private val EMBER = Color(0xFFFF8A4C)
        /** Braise assombrie pour le papier : la braise pure n'y atteint pas 3:1. */
        private val EMBER_ON_PAPER = Color(0xFFB4461A)
    }
}

/**
 * Scène du widget : fond uni aux coins du système, halo de la pochette par-dessus.
 *
 * Android 12+ : coins `system_app_widget_background_radius` et vue marquée comme fond
 * (`appWidgetBackground`) pour les transitions du lanceur. Avant, `cornerRadius` est sans
 * effet : un fond arrondi en drawable, teinté, le remplace, et le halo (rectangulaire,
 * il déborderait des coins) est omis.
 */
@Composable
internal fun WidgetSurface(
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glow = if (modern) rememberBitmap(snapshot.backdropPath) else null
    val base = modifier.fillMaxSize().appWidgetBackground()
    val scene = if (modern) {
        base.background(palette.background).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        base.background(
            imageProvider = ImageProvider(R.drawable.widget_bg_round),
            colorFilter = ColorFilter.tint(palette.background),
        )
    }
    Box(modifier = scene) {
        if (glow != null) {
            Image(
                provider = ImageProvider(glow),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        content()
    }
}

/**
 * Pochette du morceau (coins déjà arrondis dans l'image, voir WidgetArtRenderer), ou
 * pastille translucide avec une note. Un toucher ouvre le lecteur complet.
 */
@Composable
internal fun WidgetArtwork(snapshot: WidgetSnapshot, palette: WidgetPalette, size: Dp) {
    val art = rememberBitmap(snapshot.artPath)
    val open = openPlayerAction()
    if (art != null) {
        Image(
            provider = ImageProvider(art),
            contentDescription = "Pochette — ouvrir le lecteur",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.size(size).clickable(open),
        )
    } else {
        Box(
            modifier = GlanceModifier.size(size).cornerRadius(size * 0.16f).background(palette.glass)
                .clickable(open),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_note),
                contentDescription = "Ouvrir le lecteur",
                colorFilter = ColorFilter.tint(palette.accent),
                modifier = GlanceModifier.size(size * 0.42f),
            )
        }
    }
}

/** Surtitre Lumen : état de la lecture, petites capitales dans l'accent. */
@Composable
internal fun WidgetEyebrow(text: String, palette: WidgetPalette) {
    Text(
        text = text.uppercase(),
        maxLines = 1,
        style = TextStyle(
            color = palette.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily("sans-serif-medium"),
        ),
    )
}

/** Libellé du surtitre selon l'état. */
@Composable
internal fun eyebrowFor(s: WidgetSnapshot): String {
    val context = LocalContext.current
    return when {
        s.live -> context.getString(R.string.widget_eyebrow_live)
        s.isPlaying -> context.getString(R.string.widget_eyebrow_playing)
        else -> context.getString(R.string.widget_eyebrow_paused)
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

/**
 * Emplacement de la barre de progression : la barre en pause (position exacte), un
 * vide de même hauteur pendant la lecture, rien pour un direct. Voir
 * [WidgetSnapshot.showsProgress] : la barre ne s'actualise qu'aux changements d'état.
 */
@Composable
internal fun WidgetProgressSlot(snapshot: WidgetSnapshot, palette: WidgetPalette, top: Dp) {
    if (!snapshot.reservesProgress) return
    Spacer(GlanceModifier.height(top))
    if (snapshot.showsProgress) {
        LinearProgressIndicator(
            progress = snapshot.progressAt(System.currentTimeMillis()),
            color = palette.accent,
            backgroundColor = palette.track,
            modifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT).cornerRadius(PROGRESS_HEIGHT / 2),
        )
    } else {
        Spacer(GlanceModifier.height(PROGRESS_HEIGHT))
    }
}

/**
 * Bouton lecture/pause : disque au dégradé braise → magenta, icône à l'encre.
 * Le libellé dit l'action ; il change avec l'état, ce qui l'annonce aussi.
 */
@Composable
internal fun PlayPauseButton(isPlaying: Boolean, palette: WidgetPalette, size: Dp) {
    val label = if (isPlaying) "Pause" else "Lecture"
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .clickable(mediaKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_play_disc),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Image(
            provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            contentDescription = label,
            colorFilter = ColorFilter.tint(palette.onBrand),
            modifier = GlanceModifier.size(size * 0.46f),
        )
    }
}

/**
 * Bouton icône secondaire, zone tactile de [size].
 * [active] pose une pastille d'accent derrière l'icône (Android 12+, où elle s'arrondit).
 */
@Composable
internal fun WidgetIconButton(
    iconRes: Int,
    description: String,
    action: Action,
    tint: ColorProvider,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    active: Boolean = false,
    activeFill: ColorProvider? = null,
) {
    var modifier = GlanceModifier.size(size).cornerRadius(size / 2)
    if (active && activeFill != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        modifier = modifier.background(activeFill)
    }
    Box(
        modifier = modifier.clickable(action).semantics { contentDescription = description },
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

/** Charge une image préparée ; mémorisée par chemin (une nouvelle pochette = un nouveau fichier). */
@Composable
private fun rememberBitmap(path: String): Bitmap? = remember(path) {
    if (path.isEmpty()) null
    else runCatching { File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }.getOrNull()
}

private val PROGRESS_HEIGHT = 4.dp

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
