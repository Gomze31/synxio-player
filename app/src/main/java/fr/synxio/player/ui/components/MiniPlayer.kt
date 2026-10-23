package fr.synxio.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.playback.PlaybackPosition
import fr.synxio.player.playback.PlayerUiState
import fr.synxio.player.ui.theme.MiniPlayerShape
import kotlinx.coroutines.flow.StateFlow

/**
 * Barre de lecture compacte posée au-dessus de la navigation.
 *
 * Elle affiche la progression sous forme de fine barre pleine largeur : plus lisible
 * qu'un texte de position, et ça donne un repère permanent sans encombrer.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    position: StateFlow<PlaybackPosition>,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(MiniPlayerShape)
            .clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = MiniPlayerShape,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    model = song.artworkUri,
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        animate = state.isPlaying,
                    )
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Titre suivant")
                }
            }

            MiniProgressBar(position)
        }
    }
}

/**
 * Barre de progression isolée dans sa propre recomposition.
 *
 * La position est le seul état qui change en continu : l'observer ici, et non dans
 * [MiniPlayer], évite de redessiner pochette, textes et boutons quatre fois par seconde.
 */
@Composable
private fun MiniProgressBar(position: StateFlow<PlaybackPosition>) {
    val current by position.collectAsStateWithLifecycle()
    val progress by animateFloatAsState(current.progress, label = "miniProgress")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary

    // Dessin direct : la valeur animée n'est lue qu'à la phase de dessin, pas en
    // composition ni en mise en page.
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        drawRect(track)
        drawRect(fill, size = Size(size.width * progress, size.height))
    }
}
