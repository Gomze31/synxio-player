package fr.synxio.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.playback.PlayerPosition
import fr.synxio.player.playback.PlayerUiState
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.haptics.LocalSynxioHaptics
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Distance de balayage qui déclenche un changement de piste. */
private val SwipeThreshold = 72.dp

/** Part de la hauteur du mini-lecteur à parcourir vers le bas pour l'écarter. */
private const val DISMISS_FRACTION = 0.4f

/** Axe d'un balayage, fixé au premier mouvement. */
private enum class Axis { Vertical, Horizontal }

/**
 * Barre de lecture compacte posée au-dessus de la navigation.
 *
 * Un verre teinté par la pochette en cours, qui flotte avec une lueur de la même
 * couleur : on reconnaît le morceau avant même d'avoir lu son titre. La pochette est un
 * élément partagé avec le lecteur plein écran : elle grandit jusqu'à sa place quand on
 * l'ouvre.
 *
 * Gestes : toucher ouvre le lecteur ; balayer vers le haut l'ouvre en suivant le doigt
 * ([onExpandDrag] reçoit le déplacement cumulé, négatif, puis [onExpandRelease] la
 * vélocité verticale au relâcher) ; balayer vers le bas appelle [onDismiss] ; vers la gauche
 * passe au titre suivant, vers la droite revient au précédent. La carte suit le doigt et
 * revient en place par un ressort. Les mêmes commandes existent en boutons et en
 * actions d'accessibilité : aucun geste n'est le seul chemin.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    position: StateFlow<PlayerPosition>,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onExpandDrag: (Float) -> Unit = {},
    onExpandRelease: (Float) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val song = state.currentSong ?: return
    val haptics = LocalSynxioHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    val art = LocalArtworkColors.current
    val tint = art.vibrant ?: art.seed

    // Retour tactile : le mini-lecteur s'enfonce légèrement sous le doigt.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        animationSpec = Motion.press(),
        label = "miniPress",
    )

    val density = LocalDensity.current
    val threshold = with(density) { SwipeThreshold.toPx() }
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestNext by rememberUpdatedState(onNext)
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestExpand by rememberUpdatedState(onExpand)
    val hasNext by rememberUpdatedState(state.hasNext)
    val latestExpandDrag by rememberUpdatedState(onExpandDrag)
    val latestExpandRelease by rememberUpdatedState(onExpandRelease)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestHaptics by rememberUpdatedState(haptics)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = dragX.value
                // La carte s'estompe un peu en s'éloignant : on sent qu'on la « jette ».
                alpha = 1f - (abs(dragX.value) / (threshold * 4f)).coerceIn(0f, 0.35f)
            }
            .glowShadow(SynxioShape.card, (tint ?: colors.brandEnd).copy(alpha = 0.7f), elevation = 16.dp)
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 1f, tint = tint)
            .pointerInput(threshold, reduceMotion) {
                val tracker = VelocityTracker()
                // L'axe est fixé au premier mouvement après le seuil tactile : un balayage
                // vertical ouvre (ou écarte), un balayage horizontal change de piste.
                var axis: Axis? = null
                var totalY = 0f
                detectDragGestures(
                    onDragStart = { axis = null; totalY = 0f; tracker.resetTracking() },
                    onDragEnd = {
                        when (axis) {
                            Axis.Vertical -> {
                                val velocity = tracker.calculateVelocity().y
                                if (totalY < 0f) {
                                    // Vers le haut : ouvrir ; la racine décide d'après la vélocité.
                                    if (reduceMotion) {
                                        if (totalY < -threshold * 0.7f) latestExpand()
                                    } else {
                                        latestExpandRelease(velocity)
                                    }
                                } else {
                                    // Revenu sous le point de départ : l'expansion est déjà à 0,
                                    // la racine la confirme.
                                    if (!reduceMotion) latestExpandRelease(velocity.coerceAtLeast(0f))
                                    if (totalY > size.height * DISMISS_FRACTION || velocity >= PlayerSheetState.FLING_VELOCITY) {
                                        latestHaptics.tick()
                                        latestDismiss()
                                    }
                                }
                            }
                            Axis.Horizontal -> {
                                val x = dragX.value
                                when {
                                    x < -threshold && hasNext -> { latestHaptics.tick(); latestNext() }
                                    x > threshold && latestPrevious != null -> { latestHaptics.tick(); latestPrevious?.invoke() }
                                }
                            }
                            null -> Unit
                        }
                        scope.launch { dragX.animateTo(0f, Motion.spatial()) }
                    },
                    onDragCancel = {
                        if (axis == Axis.Vertical && !reduceMotion) latestExpandRelease(0f)
                        scope.launch { dragX.animateTo(0f, Motion.spatial()) }
                    },
                ) { change, amount ->
                    change.consume()
                    if (axis == null) axis = if (abs(amount.y) >= abs(amount.x)) Axis.Vertical else Axis.Horizontal
                    tracker.addPosition(change.uptimeMillis, change.position)
                    when (axis) {
                        Axis.Vertical -> {
                            totalY += amount.y
                            // Borné à 0 : redescendre sous le point de départ referme sans
                            // laisser le lecteur entrouvert.
                            if (!reduceMotion) latestExpandDrag(totalY.coerceAtMost(0f))
                        }
                        Axis.Horizontal -> {
                            // Résistance : au-delà du seuil, la carte suit de moins en moins.
                            val next = dragX.value + amount.x
                            val resisted = if (abs(next) > threshold) dragX.value + amount.x * 0.35f else next
                            scope.launch { dragX.snapTo(resisted) }
                        }
                        null -> Unit
                    }
                }
            }
            .semantics {
                customActions = buildList {
                    add(CustomAccessibilityAction("Titre suivant") { latestNext(); true })
                    add(CustomAccessibilityAction("Arrêter la lecture") { latestDismiss(); true })
                    latestPrevious?.let { prev -> add(CustomAccessibilityAction("Titre précédent") { prev(); true }) }
                }
            },
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
                modifier = Modifier.padding(start = Space.sm, end = Space.xs, top = Space.sm, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Changement de piste : pochette et titre glissent comme une page qu'on
                // tourne. La clé partagée porte l'id du morceau : l'ancienne et la nouvelle
                // pochette, présentes ensemble le temps du glissement, ne se confondent pas.
                AnimatedContent(
                    targetState = song,
                    contentKey = { it.id },
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit())
                        } else {
                            (slideInHorizontally(Motion.spatial()) { it / 3 } + fadeIn(Motion.enter())) togetherWith
                                (slideOutHorizontally(Motion.spatial()) { -it / 3 } + fadeOut(Motion.exit()))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = "miniTrack",
                ) { current ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(
                            model = current.artworkUri,
                            modifier = Modifier
                                .size(50.dp)
                                .sharedPlayerElement(SharedKeys.playerArt(current.id), SynxioShape.artworkSm),
                            shape = SynxioShape.artworkSm,
                        )
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            MarqueeText(
                                text = current.title,
                                style = MaterialTheme.typography.titleSmall,
                                animate = state.isPlaying,
                            )
                            Text(
                                text = current.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.brandBrush)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (state.isPlaying) "Mettre en pause" else "Lire",
                            onClick = { haptics.tap(); onPlayPause() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayPauseIcon(
                        isPlaying = state.isPlaying,
                        color = colors.onBrand,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = state.hasNext,
                    modifier = Modifier.size(Space.touchTarget),
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Titre suivant")
                }
            }

            MiniProgress(position = position, reduceMotion = reduceMotion)
            Spacer(Modifier.height(Space.sm))
        }
    }
}

/** Barre fine : seule à se recomposer au rythme de la position. */
@Composable
private fun MiniProgress(position: StateFlow<PlayerPosition>, reduceMotion: Boolean) {
    val current by position.collectAsStateWithLifecycle()
    // La position arrive par paliers (~4×/s) : on lisse entre deux, sauf animations réduites.
    val progress by animateFloatAsState(
        targetValue = current.progress,
        animationSpec = if (reduceMotion) snap() else Motion.standard(Motion.LONG),
        label = "miniProgress",
    )
    BrandProgressBar(
        progress = progress,
        height = 3.dp,
        modifier = Modifier.padding(horizontal = Space.lg),
    )
}
