package fr.synxio.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.playback.PlayerUiState
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.glass
import androidx.compose.ui.draw.clip

/** Épaisseur de la barre de progression : un repère, pas un contrôle. */
private val ProgressHeight = 3.dp

/**
 * Barre de lecture compacte posée au-dessus de la navigation.
 *
 * Elle affiche la progression sous forme de fine barre pleine largeur : plus lisible
 * qu'un texte de position, et ça donne un repère permanent sans encombrer.
 * C'est un élément flottant : seul cas, avec le FAB, où une ombre portée est légitime.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return
    val reduceMotion = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme

    // La position arrive par paliers (~4×/s) : on lisse entre deux, sauf animations réduites.
    val progress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else Motion.standard(Motion.LONG),
        label = "miniProgress",
    )

    // Retour tactile : le mini-lecteur s'enfonce légèrement sous le doigt.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        animationSpec = Motion.press(),
        label = "miniPress",
    )

    // Verre flottant : laisse deviner le fond ambiant teinté par la pochette.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 1f),
    ) {
        Column(
            Modifier.clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "Ouvrir le lecteur",
                role = Role.Button,
                onClick = onExpand,
            )
        ) {
            Row(
                modifier = Modifier.padding(
                    start = Space.sm,
                    end = Space.xs,
                    top = Space.sm,
                    bottom = Space.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    model = song.artworkUri,
                    modifier = Modifier.size(48.dp),
                    shape = SynxioShape.artworkSm,
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        animate = state.isPlaying,
                    )
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(44.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ) {
                    Crossfade(
                        targetState = state.isPlaying,
                        animationSpec = Motion.standardOrSnap(reduceMotion, Motion.SHORT),
                        label = "miniPlayPause",
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Lecture",
                        )
                    }
                }
                IconButton(
                    onClick = onNext,
                    enabled = state.hasNext,
                    modifier = Modifier.size(Space.touchTarget),
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Titre suivant")
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg)
                    .height(ProgressHeight)
                    .clip(SynxioShape.pill)
                    .background(scheme.onSurface.copy(alpha = 0.12f))
            ) {
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(ProgressHeight)
                            .clip(SynxioShape.pill)
                            .background(scheme.primary)
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
        }
    }
}
