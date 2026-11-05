package fr.synxio.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import fr.synxio.player.core.car.DriveQueue
import fr.synxio.player.data.model.TextSize
import fr.synxio.player.data.model.ThemeMode
import fr.synxio.player.playback.PlayerUiState
import fr.synxio.player.ui.theme.ThemePreset
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
import fr.synxio.player.playback.PlayerPosition
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandPlayButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.haptics.LocalSynxioHaptics
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
 * lit [AppViewModel.position] au dessin seulement.
 */
@Composable
fun DriveModeScreen(
    viewModel: AppViewModel,
    onExit: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val view = LocalView.current
    // Écran allumé et au maximum tant que le mode voiture est affiché ; on rend tout à la sortie.
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val previousBrightness = window?.attributes?.screenBrightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = previousBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
    // Thème sombre forcé : la nuit comme au soleil, l'encre lit mieux que le papier au volant.
    SynxioTheme(
        themeMode = ThemeMode.DARK,
        accentSource = settings.accentSource,
        artworkColors = LocalArtworkColors.current,
        themePreset = ThemePreset.fromName(settings.themePreset),
        customAccent = settings.primaryColor,
        reduceAnimations = settings.reduceAnimations,
        glowEffects = settings.glowEffects,
        textScale = TextSize.entries.firstOrNull { it.name == settings.textSize }?.scale ?: 1f,
    ) {
        DriveModeContent(viewModel, onExit)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveModeContent(viewModel: AppViewModel, onExit: () -> Unit) {
    val haptics = LocalSynxioHaptics.current
    val playerState = viewModel.playerState.collectAsStateWithLifecycle()
    val position = viewModel.position.collectAsStateWithLifecycle()
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.xxl, vertical = Space.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // La pochette sur scène : un halo de sa couleur derrière, une lueur dessous,
                // et un fondu enchaîné à chaque changement de titre.
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
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
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            text = s.displayArtist,
                            style = MaterialTheme.typography.headlineSmall,
                            // onSurface plutôt qu'onSurfaceVariant : lisible d'un coup d'œil, en plein soleil.
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(Space.xl))
                DriveProgress(position)
                Spacer(Modifier.height(Space.xxl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DriveRoundButton(
                        icon = Icons.Rounded.SkipPrevious,
                        description = "Titre précédent",
                        onClick = { haptics.tap(); viewModel.previous() },
                    )

                    BrandPlayButton(
                        isPlaying = isPlaying,
                        onClick = { haptics.tap(); viewModel.togglePlayPause() },
                        size = DrivePlayButton,
                        isBuffering = isBuffering,
                    )

                    DriveRoundButton(
                        icon = Icons.Rounded.SkipNext,
                        description = "Titre suivant",
                        onClick = { haptics.tap(); viewModel.next() },
                    )
                }

                Spacer(Modifier.height(Space.xl))
                val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
                val isFavorite = current.id in favoriteIds
                val shuffle by remember { derivedStateOf { playerState.value.shuffleEnabled } }
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DriveRoundButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        description = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                        onClick = { haptics.tap(); viewModel.toggleFavorite(current) },
                        size = DriveExitButton,
                        iconSize = 36.dp,
                        tint = if (isFavorite) scheme.primary else scheme.onSurface,
                    )
                    DriveRoundButton(
                        icon = Icons.Rounded.Shuffle,
                        description = if (shuffle) "Désactiver l'aléatoire" else "Activer l'aléatoire",
                        onClick = { haptics.tap(); viewModel.toggleShuffle() },
                        size = DriveExitButton,
                        iconSize = 36.dp,
                        tint = if (shuffle) scheme.primary else scheme.onSurface,
                    )
                    DriveRoundButton(
                        icon = Icons.Rounded.Mic,
                        description = "Parler à l'Assistant",
                        onClick = {
                            haptics.tap()
                            // L'Assistant revient par la session média (« joue … sur Synxio »).
                            runCatching { context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                        size = DriveExitButton,
                        iconSize = 36.dp,
                    )
                }

                Spacer(Modifier.height(Space.xl))
                DriveUpNext(playerState, onSkipTo = { haptics.tap(); viewModel.skipToQueueIndex(it) })
                Spacer(Modifier.height(Space.lg))
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

/** Les trois titres suivants, en grand : un toucher y saute. Rien en fin de file ou en répétition du titre. */
@Composable
private fun DriveUpNext(playerState: State<PlayerUiState>, onSkipTo: (Int) -> Unit) {
    val state = playerState.value
    val next = remember(state.queue, state.queueIndex, state.repeatMode) {
        DriveQueue.upcoming(state.queue.size, state.queueIndex, state.repeatMode)
    }
    if (next.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("ENSUITE", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Space.sm))
        for (index in next) {
            val song = state.queue.getOrNull(index) ?: continue
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(SynxioShape.card)
                    .glass(SynxioShape.card, strength = 0.8f)
                    .clickable(role = Role.Button) { onSkipTo(index) }
                    .padding(horizontal = Space.lg, vertical = Space.md)
                    .semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.displayArtist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(Space.sm))
        }
    }
}

/**
 * Progression du titre, épaisse pour être lue d'un coup d'œil. La position n'est lue
 * qu'au dessin : la barre avance sans recomposer l'écran quatre fois par seconde.
 */
@Composable
private fun DriveProgress(position: State<PlayerPosition>) {
    val brand = SynxioTheme.colors.brandBrushHorizontal
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(SynxioShape.pill)
            .semantics { contentDescription = "Progression du titre" }
            .drawBehind {
                val f = position.value.progress
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
    tint: Color = MaterialTheme.colorScheme.onSurface,
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
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
