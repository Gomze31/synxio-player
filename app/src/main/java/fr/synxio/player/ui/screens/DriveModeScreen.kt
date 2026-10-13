package fr.synxio.player.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Taille des commandes secondaires : bien au-delà des 48 dp, pour viser sans regarder. */
private val DriveButton = 88.dp
private val DrivePlayButton = 120.dp

/**
 * Mode voiture : très gros boutons, texte très contrasté, et un glissement horizontal
 * sur toute la surface pour changer de titre.
 *
 * Le titre en cours est dérivé de [AppViewModel.playerState] : l'écran ne se recompose
 * que lorsque le morceau change, pas à chaque tic de position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveModeScreen(
    viewModel: AppViewModel,
    onExit: () -> Unit,
) {
    val playerState = viewModel.playerState.collectAsStateWithLifecycle()
    val song by remember { derivedStateOf { playerState.value.currentSong } }
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Mode voiture") },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .padding(start = Space.sm)
                            .size(72.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quitter le mode voiture",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                },
                expandedHeight = 96.dp,
            )
        }
    ) { padding ->
        val current = song
        if (current == null) {
            EmptyState(
                title = "Aucune lecture en cours",
                subtitle = "Lance un titre depuis ta bibliothèque, puis reviens ici.",
                icon = Icons.Rounded.DirectionsCar,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Un seul changement de titre par geste : on cumule le glissement et on
                // décide à la fin (l'ancienne version sautait plusieurs titres d'un coup).
                .pointerInput(swipeThreshold) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total > swipeThreshold -> viewModel.previous()
                                total < -swipeThreshold -> viewModel.next()
                            }
                        },
                        onHorizontalDrag = { _, amount -> total += amount },
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Artwork(
                    model = current.artworkUri,
                    shape = SynxioShape.artworkLg,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )

                Spacer(Modifier.height(Space.xxl))

                Text(
                    text = current.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Space.sm))

                Text(
                    text = current.displayArtist,
                    style = MaterialTheme.typography.titleLarge,
                    // onSurface plutôt qu'onSurfaceVariant : lisible d'un coup d'œil, en plein soleil.
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Space.xxxl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = viewModel::previous,
                        modifier = Modifier.size(DriveButton),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Titre précédent", modifier = Modifier.size(48.dp))
                    }

                    FilledIconButton(
                        onClick = viewModel::togglePlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .size(DrivePlayButton)
                            .semantics { stateDescription = if (isPlaying) "En cours de lecture" else "En pause" },
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            modifier = Modifier.size(72.dp),
                        )
                    }

                    FilledTonalIconButton(
                        onClick = viewModel::next,
                        modifier = Modifier.size(DriveButton),
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Titre suivant", modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}
