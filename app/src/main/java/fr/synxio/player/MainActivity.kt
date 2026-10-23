package fr.synxio.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.ui.SynxioRoot
import fr.synxio.player.ui.screens.OnboardingScreen
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.ThemePreset
import fr.synxio.player.ui.theme.rememberArtworkColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow

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

    /**
     * Action de l'intent en attente de traitement (raccourci d'accueil, widget).
     *
     * L'activité est `singleTask` : un raccourci tapé pendant que l'app tourne arrive
     * par [onNewIntent] et non par [onCreate]. On fait transiter l'action par un flux
     * consommé une seule fois par Compose, remis à `null` ensuite : relire `intent.action`
     * dans la composition rejouait le raccourci à chaque rotation.
     */
    private val pendingAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Le splash statique du thème laisse place à l'UI Compose dès le premier frame.
        setTheme(R.style.Theme_Synxio)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Seul un démarrage à froid porte une action : après une recréation (rotation,
        // changement de thème système) l'intent d'origine a déjà été traité.
        if (savedInstanceState == null) pendingAction.value = intent?.action

        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val artworkUri by viewModel.currentArtworkUri.collectAsStateWithLifecycle()
            val artworkColors by rememberArtworkColors(artworkUri)

            // Premier lancement : l'onboarding remplace l'accueil. Pas de splash artificiel,
            // le thème de démarrage du système en affiche déjà un.
            var showOnboarding by rememberSaveable {
                mutableStateOf(!hasSeenOnboarding(this@MainActivity))
            }

            // Raccourci d'écran d'accueil : on attend que la bibliothèque soit chargée,
            // sinon « Tout mélanger » démarrerait sur une liste vide. L'ouverture du
            // lecteur est lue directement par SynxioRoot et n'a pas besoin d'attendre.
            val library by viewModel.library.collectAsStateWithLifecycle()
            val action by pendingAction.collectAsStateWithLifecycle()

            LaunchedEffect(action, library.hasScanned) {
                val current = action ?: return@LaunchedEffect
                if (current != ACTION_SHOW_PLAYER && !library.hasScanned) return@LaunchedEffect
                when (current) {
                    ACTION_SHUFFLE_ALL -> viewModel.shufflePlay(library.songs)
                    ACTION_PLAY_FAVORITES -> viewModel.play(viewModel.favoriteSongs.value)
                    ACTION_RESUME -> viewModel.togglePlayPause()
                    else -> Unit
                }
                pendingAction.value = null
            }

            SynxioTheme(
                themeMode = settings.themeMode,
                accentSource = settings.accentSource,
                artworkColors = artworkColors,
                themePreset = ThemePreset.fromName(settings.themePreset),
                customAccent = settings.primaryColor,
            ) {
                if (showOnboarding) {
                    OnboardingScreen(
                        onBack = { showOnboarding = false },
                        onFinish = {
                            markOnboardingSeen(this@MainActivity)
                            showOnboarding = false
                        }
                    )
                } else {
                    SynxioRoot(
                        viewModel = viewModel,
                        openPlayerOnStart = action == ACTION_SHOW_PLAYER,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction.value = intent.action
    }

    companion object {
        const val ACTION_SHOW_PLAYER = "fr.synxio.player.SHOW_PLAYER"
        const val ACTION_SHUFFLE_ALL = "fr.synxio.player.SHUFFLE_ALL"
        const val ACTION_PLAY_FAVORITES = "fr.synxio.player.PLAY_FAVORITES"
        const val ACTION_RESUME = "fr.synxio.player.RESUME"
    }
}
