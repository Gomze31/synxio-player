package fr.synxio.player

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.ui.SynxioRoot
import fr.synxio.player.ui.screens.OnboardingScreen
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.ThemePreset
import fr.synxio.player.ui.theme.rememberArtworkColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fonction utilitaire pour vérifier si on a déjà vu l'onboarding.
 */
fun hasSeenOnboarding(context: Context): Boolean {
    val prefs = context.getSharedPreferences("synxio_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("onboarding_seen", false)
}

/**
 * Fonction utilitaire pour marquer l'onboarding comme vu.
 */
fun markOnboardingSeen(context: Context) {
    val prefs = context.getSharedPreferences("synxio_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("onboarding_seen", true).apply()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Le splash statique du thème laisse place à l'UI Compose dès le premier frame.
        setTheme(R.style.Theme_Synxio)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val artworkUri by viewModel.currentArtworkUri.collectAsStateWithLifecycle()
            val artworkColors by rememberArtworkColors(artworkUri)
            
            // État pour gérer le flow de démarrage
            var showSplash by remember { mutableStateOf(true) }
            var showOnboarding by remember { mutableStateOf(false) }
            
            // Vérifier si on a déjà vu l'onboarding
            LaunchedEffect(Unit) {
                // Afficher le splash pendant 2 secondes
                delay(2000)
                showSplash = false
                
                // Vérifier si c'est la première fois
                if (!hasSeenOnboarding(this@MainActivity)) {
                    showOnboarding = true
                }
            }
            
            // Raccourci d'écran d'accueil : on attend que la bibliothèque soit chargée,
            // sinon « Tout mélanger » démarrerait sur une liste vide. L'action est
            // consommée une seule fois pour ne pas se rejouer à chaque recomposition.
            val library by viewModel.library.collectAsStateWithLifecycle()
            var shortcutHandled by remember { mutableStateOf(false) }

            LaunchedEffect(library.hasScanned, shortcutHandled) {
                if (shortcutHandled || !library.hasScanned) return@LaunchedEffect
                when (intent?.action) {
                    ACTION_SHUFFLE_ALL -> viewModel.shufflePlay(library.songs)
                    ACTION_PLAY_FAVORITES -> viewModel.play(viewModel.favoriteSongs.value)
                    ACTION_RESUME -> viewModel.togglePlayPause()
                    else -> Unit
                }
                shortcutHandled = true
            }

            SynxioTheme(
                themeMode = settings.themeMode,
                accentSource = settings.accentSource,
                artworkColors = artworkColors,
                themePreset = ThemePreset.fromName(settings.themePreset),
                customAccent = settings.primaryColor,
            ) {
                when {
                    showSplash -> {
                        SplashScreenContent()
                    }
                    showOnboarding -> {
                        OnboardingScreen(
                            onBack = {
                                showOnboarding = false
                            },
                            onFinish = {
                                markOnboardingSeen(this@MainActivity)
                                showOnboarding = false
                            }
                        )
                    }
                    else -> {
                        SynxioRoot(
                            viewModel = viewModel,
                            openPlayerOnStart = intent?.action == ACTION_SHOW_PLAYER,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW_PLAYER = "fr.synxio.player.SHOW_PLAYER"
        const val ACTION_SHUFFLE_ALL = "fr.synxio.player.SHUFFLE_ALL"
        const val ACTION_PLAY_FAVORITES = "fr.synxio.player.PLAY_FAVORITES"
        const val ACTION_RESUME = "fr.synxio.player.RESUME"
    }
}

/**
 * Contenu du splash screen.
 */
@Composable
fun SplashScreenContent() {
    val scope = rememberCoroutineScope()
    
    // Animation de l'icône
    val logoScale = remember { Animatable(0.8f) }
    
    // Animation de l'opacité du texte
    val textAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Animation du logo
        logoScale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(
                durationMillis = 800,
                easing = EaseInOut
            )
        )
        
        // Animation du texte
        delay(300)
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 500,
                easing = EaseInOut
            )
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Logo avec animation de scale
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(fr.synxio.player.R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale.value),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    MaterialTheme.colorScheme.primary
                )
            )
            
            // Texte avec animation d'opacité
            Text(
                text = "Synxio Player",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = textAlpha.value),
                modifier = Modifier.scale(textAlpha.value)
            )
            
            // Indicateurs de chargement
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
