package fr.synxio.player.watch.ui

import android.widget.Toast
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import fr.synxio.player.watch.ui.theme.WatchTheme

private const val ROUTE_PLAYER = "player"
private const val ROUTE_LAUNCH = "launch"
private const val SWIPE_THRESHOLD_PX = 120f

/**
 * Deux destinations : lecteur (accueil) et lancer. Balayage droit = retour système,
 * balayage gauche depuis le lecteur = « Lancer » (couche de geste sous le contenu : les
 * boutons consomment leurs touchers, un glissé sur une zone vide atteint la couche).
 */
@Composable
fun WatchNav(viewModel: PlayerViewModel, now: () -> Long = System::currentTimeMillis) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val art by viewModel.art.collectAsStateWithLifecycle()
    val artBlurred by viewModel.artBlurred.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nav: NavHostController = rememberSwipeDismissableNavController()

    LaunchedEffect(Unit) {
        viewModel.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val accent = (ui as? PlayerUiState.Playing)?.state?.accent ?: 0
    Box(Modifier.fillMaxSize()) {
        HorizontalSwipeToLaunch(nav = nav)
        WatchTheme(accent = accent) {
            AppScaffold(timeText = { TimeText() }) {
                SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_PLAYER) {
                    composable(ROUTE_PLAYER) {
                        when (val state = ui) {
                            is PlayerUiState.Playing -> PlayerScreen(
                                ui = state,
                                art = art,
                                artBlurred = artBlurred,
                                onPlayPause = viewModel::playPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                                onFavorite = viewModel::toggleFavorite,
                                onVolumeStep = viewModel::volumeStep,
                                now = now,
                            )
                            else -> DisconnectedScreen(ui = state, onStart = viewModel::start)
                        }
                    }
                    composable(ROUTE_LAUNCH) {
                        LaunchScreen(onStart = { id ->
                            viewModel.start(id)
                            nav.popBackStack()
                        })
                    }
                }
            }
        }
    }
}

/** Couche transparente qui détecte un glissé vers la gauche et pousse la route « Lancer ». */
@Composable
private fun HorizontalSwipeToLaunch(nav: NavHostController) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                    onDragEnd = {
                        if (total < -SWIPE_THRESHOLD_PX && nav.currentDestination?.route == ROUTE_PLAYER) nav.navigate(ROUTE_LAUNCH)
                    },
                )
            },
    )
}
