package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.playback.PlayerUiState
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandPlayButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/** Taille des commandes secondaires : bien au-delà des 48 dp, pour viser sans regarder. */
private val DriveButton = 92.dp
private val DrivePlayButton = 124.dp
private val DriveExitButton = 72.dp

/**
 * Mode voiture : très gros boutons, texte très contrasté, et un glissement horizontal
 * sur toute la surface pour changer de titre.
 *
 * Le titre en cours est dérivé de [AppViewModel.playerState] : l'écran ne se recompose
 * que lorsque le morceau change, pas à chaque tic de position. La barre de progression
 * lit la position au dessin seulement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveModeScreen(
    viewModel: AppViewModel,
    onExit: () -> Unit,
) {
    val playerState = viewModel.playerState.collectAsStateWithLifecycle()
    val song by remember { derivedStateOf { playerState.value.currentSong } }
    val isBuffering by remember { derivedStateOf { playerState.value.isBuffering } }
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val reduce = LocalReduceMotion.current
    val glow = LocalGlowEffects.current
    val scheme = MaterialTheme.colorScheme
    val art = LocalArtworkColors.current
    val accent by animateColorAsState(
        art.vibrant ?: art.seed ?: SynxioTheme.colors.brandEnd,
        Motion.standardOrSnap(reduce, Motion.PALETTE),
        label = "driveAccent",
    )
    // Suivi du doigt pendant le glissement : la pochette accompagne le geste, puis revient
    // en place sur un ressort. Lu au dessin uniquement (graphicsLayer).
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        // Lisibilité au soleil : on assombrit (ou éclaircit) l'aurore derrière tout l'écran.
        // Le voile reste translucide, mais le texte ne lutte plus contre les halos colorés.
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background.copy(alpha = 0.62f)),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = {
                    Column {
                        Text("MODE VOITURE", style = EyebrowTextStyle, color = scheme.primary)
                        Text("En route", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    }
                },
                navigationIcon = {
                    DriveRoundButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        description = "Quitter le mode voiture",
                        onClick = onExit,
                        size = DriveExitButton,
                        iconSize = 36.dp,
                        modifier = Modifier.padding(start = Space.sm),
                    )
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
                            scope.launch { drag.animateTo(0f, Motion.spatialOrSnap(reduce)) }
                        },
                        onDragCancel = { scope.launch { drag.snapTo(0f) } },
                        onHorizontalDrag = { _, amount ->
                            total += amount
                            if (!reduce) scope.launch { drag.snapTo(total * 0.35f) }
                        },
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
                // La pochette sur scène : un halo de sa couleur derrière, une lueur dessous,
                // et un fondu enchaîné à chaque changement de titre.
                Box(
                    Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            translationX = drag.value
                            rotationZ = drag.value / 60f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (glow) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.35f
                                    scaleY = 1.35f
                                }
                                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent)))
                        )
                    }
                    AnimatedContent(
                        targetState = current,
                        contentKey = { it.id },
                        transitionSpec = {
                            if (reduce) {
                                fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(Motion.exit(Motion.XS))
                            } else {
                                (fadeIn(Motion.enter()) + scaleIn(Motion.spatial(), initialScale = 0.92f)) togetherWith
                                    (fadeOut(Motion.exit()) + scaleOut(Motion.exit(), targetScale = 1.04f))
                            }
                        },
                        label = "driveArtwork",
                    ) { s ->
                        Artwork(
                            model = s.artworkUri,
                            shape = SynxioShape.artworkLg,
                            modifier = Modifier
                                .fillMaxSize()
                                .glowShadow(SynxioShape.artworkLg, accent, elevation = 36.dp),
                        )
                    }
                }

                Spacer(Modifier.height(Space.xxl))

                AnimatedContent(
                    targetState = current,
                    contentKey = { it.id },
                    transitionSpec = {
                        fadeIn(Motion.enter(if (reduce) Motion.XS else Motion.MEDIUM)) togetherWith
                            fadeOut(Motion.exit(Motion.XS))
                    },
                    label = "driveTitle",
                ) { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            text = s.displayArtist,
                            style = MaterialTheme.typography.titleLarge,
                            // onSurface plutôt qu'onSurfaceVariant : lisible d'un coup d'œil, en plein soleil.
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(Space.xl))
                DriveProgress(playerState)
                Spacer(Modifier.height(Space.xxl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DriveRoundButton(
                        icon = Icons.Rounded.SkipPrevious,
                        description = "Titre précédent",
                        onClick = viewModel::previous,
                    )

                    BrandPlayButton(
                        isPlaying = isPlaying,
                        onClick = viewModel::togglePlayPause,
                        size = DrivePlayButton,
                        isBuffering = isBuffering,
                    )

                    DriveRoundButton(
                        icon = Icons.Rounded.SkipNext,
                        description = "Titre suivant",
                        onClick = viewModel::next,
                    )
                }

                Spacer(Modifier.height(Space.xl))
                Text(
                    "Glisse sur l'écran pour changer de titre",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Progression du titre, épaisse pour être lue d'un coup d'œil. La position n'est lue
 * qu'au dessin : la barre avance sans recomposer l'écran quatre fois par seconde.
 */
@Composable
private fun DriveProgress(playerState: State<PlayerUiState>) {
    val brand = SynxioTheme.colors.brandBrushHorizontal
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(SynxioShape.pill)
            .semantics { contentDescription = "Progression du titre" }
            .drawBehind {
                val s = playerState.value
                val f = if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(track, cornerRadius = r)
                if (f > 0f) drawRoundRect(brand, size = Size(size.width * f, size.height), cornerRadius = r)
            }
    )
}

/**
 * Commande ronde du mode voiture : disque de verre dense, liseré lumineux, icône pleine
 * en onSurface pour un contraste maximal, et un enfoncement franc sous le doigt.
 */
@Composable
private fun DriveRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = DriveButton,
    iconSize: androidx.compose.ui.unit.Dp = 52.dp,
) {
    Box(
        modifier
            .pressBounce(0.9f)
            .size(size)
            .clip(CircleShape)
            .glass(CircleShape, strength = 1f)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}
