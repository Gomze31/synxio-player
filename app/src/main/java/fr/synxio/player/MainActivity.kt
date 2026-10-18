package fr.synxio.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.data.model.TextSize
import fr.synxio.player.playback.PlayerConnection
import fr.synxio.player.ui.SynxioRoot
import fr.synxio.player.ui.screens.OnboardingScreen
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.ThemePreset
import fr.synxio.player.ui.theme.rememberArtworkColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

fun hasSeenOnboarding(context: Context): Boolean {
    val prefs = context.getSharedPreferences("synxio_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("onboarding_seen", false)
}

fun markOnboardingSeen(context: Context) {
    val prefs = context.getSharedPreferences("synxio_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("onboarding_seen", true).apply()
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var playerConnection: PlayerConnection

    /**
     * Intent à traiter. L'activité est en `singleTask` : quand elle tourne déjà, un
     * raccourci, la notification ou « Ouvrir avec » arrivent par [onNewIntent]. Ils
     * étaient ignorés, seul l'intent de lancement était lu.
     */
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Le splash statique du thème laisse place à l'UI Compose dès le premier frame.
        setTheme(R.style.Theme_Synxio)
        enableEdgeToEdge()
        // Navigation à trois boutons : Android pose sinon un voile gris opaque sous la barre
        // système, qui coupait le fond ambiant. Les boutons restent lisibles : leur couleur
        // suit le thème clair/sombre de la fenêtre.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingIntent.value = intent

        // Erreurs de lecture visibles où que l'on soit dans l'app.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerConnection.errors.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val artworkUri by viewModel.currentArtworkUri.collectAsStateWithLifecycle()
            val artworkColors by rememberArtworkColors(artworkUri)
            val library by viewModel.library.collectAsStateWithLifecycle()

            var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            var showOnboarding by rememberSaveable { mutableStateOf(!hasSeenOnboarding(this@MainActivity)) }
            var openPlayerRequest by remember { mutableLongStateOf(0L) }

            // Le splash dure le temps utile, plus le geste de marque : il se lève dès que
            // la bibliothèque est prête, au plus tard au bout de SPLASH_MAX_MS. Les 2 s
            // fixes d'avant retardaient chaque démarrage, même à chaud.
            LaunchedEffect(Unit) {
                if (!showSplash) return@LaunchedEffect
                val started = System.currentTimeMillis()
                withTimeoutOrNull(SPLASH_MAX_MS) { viewModel.library.first { it.hasScanned } }
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < SPLASH_MIN_MS) delay(SPLASH_MIN_MS - elapsed)
                showSplash = false
            }

            val intentToHandle by pendingIntent.collectAsStateWithLifecycle()
            LaunchedEffect(intentToHandle, library.hasScanned) {
                val current = intentToHandle ?: return@LaunchedEffect
                when (current.action) {
                    ACTION_SHOW_PLAYER -> openPlayerRequest = System.nanoTime()
                    Intent.ACTION_VIEW -> current.data?.let { uri ->
                        playerConnection.playExternalUri(uri, displayNameOf(uri))
                        openPlayerRequest = System.nanoTime()
                    }
                    // Raccourcis : on attend la bibliothèque, sinon « Tout mélanger »
                    // démarrerait sur une liste vide.
                    ACTION_SHUFFLE_ALL, ACTION_PLAY_FAVORITES, ACTION_RESUME -> {
                        if (!library.hasScanned) return@LaunchedEffect
                        when (current.action) {
                            ACTION_SHUFFLE_ALL -> viewModel.shufflePlay(library.songs)
                            ACTION_PLAY_FAVORITES -> viewModel.play(viewModel.favoriteSongs.value)
                            ACTION_RESUME -> viewModel.togglePlayPause()
                        }
                    }
                }
                pendingIntent.value = null
            }

            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                viewModel.onDeleteConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
            }

            LaunchedEffect(viewModel) {
                viewModel.deleteIntentSenders.collect { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            }

            SynxioTheme(
                themeMode = settings.themeMode,
                accentSource = settings.accentSource,
                artworkColors = artworkColors,
                themePreset = ThemePreset.fromName(settings.themePreset),
                customAccent = settings.primaryColor,
                reduceAnimations = settings.reduceAnimations,
                glowEffects = settings.glowEffects,
                textScale = TextSize.entries.firstOrNull { it.name == settings.textSize }?.scale ?: 1f,
            ) {
                val stage = when {
                    showSplash -> Stage.SPLASH
                    showOnboarding -> Stage.ONBOARDING
                    else -> Stage.APP
                }
                val reduce = LocalReduceMotion.current
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        fadeIn(Motion.enter(if (reduce) Motion.SHORT else Motion.LONG)) togetherWith
                            fadeOut(Motion.exit())
                    },
                    label = "stage",
                ) { target ->
                    when (target) {
                        Stage.SPLASH -> SplashScreenContent()
                        Stage.ONBOARDING -> OnboardingScreen(
                            onFinish = {
                                markOnboardingSeen(this@MainActivity)
                                showOnboarding = false
                            }
                        )
                        Stage.APP -> SynxioRoot(
                            viewModel = viewModel,
                            openPlayerRequest = openPlayerRequest,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    /** Nom du fichier ouvert depuis une autre app, pour lui donner un titre lisible. */
    private fun displayNameOf(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private enum class Stage { SPLASH, ONBOARDING, APP }

    companion object {
        const val ACTION_SHOW_PLAYER = "fr.synxio.player.SHOW_PLAYER"
        const val ACTION_SHUFFLE_ALL = "fr.synxio.player.SHUFFLE_ALL"
        const val ACTION_PLAY_FAVORITES = "fr.synxio.player.PLAY_FAVORITES"
        const val ACTION_RESUME = "fr.synxio.player.RESUME"

        private const val SPLASH_MIN_MS = 650L
        private const val SPLASH_MAX_MS = 1_400L
    }
}

/**
 * Splash Synxio : le logo se pose, le nom de l'app monte et apparaît.
 *
 * Pas d'indicateur de chargement : le splash disparaît dès que la bibliothèque est
 * prête, un spinner ne ferait qu'annoncer une attente.
 */
@Composable
fun SplashScreenContent() {
    val reduce = LocalReduceMotion.current
    val logo = remember { Animatable(if (reduce) 1f else 0f) }
    val title = remember { Animatable(if (reduce) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        launch { logo.animateTo(1f, Motion.enter(Motion.LONG)) }
        delay(160)
        title.animateTo(1f, Motion.enter(Motion.LONG))
    }

    val colors = fr.synxio.player.ui.theme.SynxioTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Même scène que l'app : l'aurore Lumen, puis un halo de signature qui s'ouvre
        // derrière le logo comme une lumière qu'on allume.
        fr.synxio.player.ui.theme.AmbientBackdrop()
        Box(
            Modifier
                .size(320.dp)
                .graphicsLayer {
                    val s = 0.6f + 0.4f * logo.value
                    scaleX = s
                    scaleY = s
                    alpha = logo.value
                }
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(colors.brandEnd.copy(alpha = 0.32f), androidx.compose.ui.graphics.Color.Transparent)
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        val s = 0.86f + 0.14f * logo.value
                        scaleX = s
                        scaleY = s
                        alpha = logo.value
                        // Calque séparé : le dégradé ne colore que les pixels du logo.
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(colors.brandBrush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn)
                    },
            )
            Text(
                text = "Synxio",
                style = MaterialTheme.typography.displayMedium.copy(brush = colors.brandBrushHorizontal),
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 14.dp.toPx()
                },
            )
            Text(
                text = "MUSIC",
                style = fr.synxio.player.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.graphicsLayer { alpha = title.value },
            )
        }
    }
}
