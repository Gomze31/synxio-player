package fr.synxio.player.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandPlayButton
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val TrimArtworkSize = 72.dp
private val PreviewButtonSize = 68.dp

/** Hauteur de la bande d'onde : assez haute pour viser une poignée au pouce. */
private val WaveHeight = 128.dp
/** Largeur tactile d'une poignée ; le trait visible est bien plus fin. */
private val HandleWidth = 32.dp
private val BarWidth = 3.dp
private val BarGap = 2.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    song: Song,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val totalDuration = song.durationMs.coerceAtLeast(1000L).toFloat()
    var range by remember { mutableStateOf(0f..totalDuration) }
    var isProcessing by remember { mutableStateOf(false) }

    // Pré-écoute audio
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    // Tête de lecture de la pré-écoute, en ms (négative = aucune). Lue seulement au dessin
    // de l'onde : la suivre ne recompose pas l'écran à chaque image.
    val playhead = remember { mutableFloatStateOf(-1f) }

    DisposableEffect(song.uri) {
        val player = runCatching { MediaPlayer.create(context, song.uri) }.getOrNull()
        mediaPlayer = player
        onDispose {
            previewJob?.cancel()
            player?.runCatching {
                if (isPlaying) stop()
                release()
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        mediaPlayer?.runCatching {
            if (isPlaying) pause()
        }
        isPlayingPreview = false
    }

    fun playPreview(fromMs: Long, toMs: Long) {
        stopPreview()
        val player = mediaPlayer ?: return
        val start = fromMs.coerceAtLeast(0L).toInt()
        val end = toMs.coerceAtMost(song.durationMs).toInt()
        if (start >= end) return

        runCatching {
            player.seekTo(start)
            player.start()
            isPlayingPreview = true

            previewJob = coroutineScope.launch {
                val durationToPlay = end - start
                delay(durationToPlay.toLong())
                stopPreview()
            }
        }
    }

    // Suivi de la tête de lecture, uniquement pendant une pré-écoute : la boucle s'arrête
    // avec elle, rien ne tourne quand on ne joue pas.
    LaunchedEffect(isPlayingPreview) {
        if (!isPlayingPreview) {
            playhead.floatValue = -1f
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameMillis { }
            playhead.floatValue = mediaPlayer?.runCatching { currentPosition.toFloat() }?.getOrNull() ?: -1f
        }
    }

    val selectionDuration = (range.endInclusive - range.start).toLong()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Découpage audio", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = {
                        stopPreview()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Carte d'information sur la chanson
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(
                        model = song.artworkUri,
                        modifier = Modifier
                            .size(TrimArtworkSize)
                            .glowShadow(SynxioShape.artworkSm, SynxioTheme.colors.brandEnd, elevation = 16.dp),
                        shape = SynxioShape.artworkSm,
                    )
                    Spacer(modifier = Modifier.width(Space.lg))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.displayArtist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Durée totale : ${song.durationMs.asDuration()}",
                            style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Space.xl))

            // Durée de la sélection : le chiffre héros de l'écran.
            Text("EXTRAIT SÉLECTIONNÉ", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Text(
                text = selectionDuration.asDuration(),
                style = MaterialTheme.typography.displayLarge.merge(TabularNumbers),
                modifier = Modifier.semantics { heading() },
            )

            Spacer(modifier = Modifier.height(Space.lg))

            // Onde et poignées : le RangeSlider garde le geste et l'accessibilité (deux
            // curseurs réglables au lecteur d'écran), on ne fait que redessiner son allure.
            WaveformRange(
                seed = song.id,
                range = range,
                totalDuration = totalDuration,
                playhead = { playhead.floatValue },
                onRangeChange = { newRange ->
                    stopPreview()
                    range = newRange
                },
            )

            Spacer(modifier = Modifier.height(Space.md))

            // Début et fin avec réglages fins (-1s / +1s). Boutons à 48 dp : à 36 dp, ils
            // passaient sous la cible tactile minimale et leur libellé était illisible.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contrôles du Début
                Column(horizontalAlignment = Alignment.Start) {
                    Text("DÉBUT", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = range.start.toLong().asDuration(),
                        style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                    )
                    Row(
                        modifier = Modifier.padding(top = Space.xs),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        NudgeButton(
                            label = "-1s",
                            description = "Début une seconde plus tôt",
                            enabled = range.start > 0f,
                            onClick = {
                                val newStart = (range.start - 1000f).coerceAtLeast(0f)
                                if (newStart < range.endInclusive) range = newStart..range.endInclusive
                            },
                        )
                        NudgeButton(
                            label = "+1s",
                            description = "Début une seconde plus tard",
                            enabled = range.start + 1000f <= range.endInclusive - 1000f,
                            onClick = {
                                val newStart = range.start + 1000f
                                if (newStart <= range.endInclusive - 1000f) {
                                    range = newStart..range.endInclusive
                                }
                            },
                        )
                    }
                }

                // Contrôles de la Fin
                Column(horizontalAlignment = Alignment.End) {
                    Text("FIN", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = range.endInclusive.toLong().asDuration(),
                        style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                    )
                    Row(
                        modifier = Modifier.padding(top = Space.xs),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        NudgeButton(
                            label = "-1s",
                            description = "Fin une seconde plus tôt",
                            enabled = range.endInclusive - 1000f >= range.start + 1000f,
                            onClick = {
                                val newEnd = range.endInclusive - 1000f
                                if (newEnd >= range.start + 1000f) {
                                    range = range.start..newEnd
                                }
                            },
                        )
                        NudgeButton(
                            label = "+1s",
                            description = "Fin une seconde plus tard",
                            enabled = range.endInclusive < totalDuration,
                            onClick = {
                                val newEnd = (range.endInclusive + 1000f).coerceAtMost(totalDuration)
                                if (newEnd > range.start) range = range.start..newEnd
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Space.xl))

            // Pré-écoute
            GlassCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Pré-écoute de l'extrait",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(Space.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tester le début (3s)
                        GlassButton(
                            text = "Début 3 s",
                            icon = Icons.Rounded.FastRewind,
                            onClick = {
                                val start = range.start.toLong()
                                playPreview(start, (start + 3000L).coerceAtMost(range.endInclusive.toLong()))
                            },
                        )

                        // Écouter tout l'extrait
                        BrandPlayButton(
                            isPlaying = isPlayingPreview,
                            size = PreviewButtonSize,
                            onClick = {
                                if (isPlayingPreview) {
                                    stopPreview()
                                } else {
                                    playPreview(range.start.toLong(), range.endInclusive.toLong())
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (isPlayingPreview) "Arrêter la pré-écoute" else "Écouter l'extrait"
                            },
                        )

                        // Tester la fin (3s)
                        GlassButton(
                            text = "Fin 3 s",
                            icon = Icons.Rounded.FastForward,
                            onClick = {
                                val end = range.endInclusive.toLong()
                                playPreview((end - 3000L).coerceAtLeast(range.start.toLong()), end)
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Space.xxl))

            // Bouton de sauvegarde
            AnimatedContent(
                targetState = isProcessing,
                transitionSpec = { fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.XS)) },
                label = "trimSave",
                modifier = Modifier.fillMaxWidth(),
            ) { processing ->
                BrandButton(
                    text = if (processing) "Création du fichier audio…" else "Sauvegarder l'extrait découpé",
                    icon = Icons.Rounded.ContentCut,
                    enabled = !processing && selectionDuration >= 1000L,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        stopPreview()
                        isProcessing = true
                        viewModel.trimSong(song, range.start.toLong(), range.endInclusive.toLong())
                        onBack()
                    },
                )
            }
            Spacer(modifier = Modifier.height(Space.bottomInset))
        }
    }
}

/**
 * Bande d'onde avec sa zone de sélection.
 *
 * L'onde est une silhouette générée à partir de l'identifiant du titre, pas l'enveloppe
 * réelle du fichier : la décoder imposerait un passage FFmpeg complet avant même de
 * pouvoir toucher les poignées. Elle sert de repère visuel stable (le même titre a
 * toujours la même allure) ; les durées affichées, elles, sont exactes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveformRange(
    seed: Long,
    range: ClosedFloatingPointRange<Float>,
    totalDuration: Float,
    playhead: () -> Float,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    val startSource = remember { MutableInteractionSource() }
    val endSource = remember { MutableInteractionSource() }
    val amplitudes = remember(seed) { waveAmplitudes(seed) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.5f)
            .padding(vertical = Space.md),
    ) {
        RangeSlider(
            value = range,
            onValueChange = onRangeChange,
            valueRange = 0f..totalDuration,
            startInteractionSource = startSource,
            endInteractionSource = endSource,
            startThumb = { GlowHandle(startSource) },
            endThumb = { GlowHandle(endSource) },
            track = { state -> WaveTrack(state, amplitudes, totalDuration, playhead) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Bornes de l'extrait" },
        )
    }
}

/** Le dessin de l'onde : barres éteintes hors sélection, à la signature dedans. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveTrack(
    state: RangeSliderState,
    amplitudes: FloatArray,
    totalDuration: Float,
    playhead: () -> Float,
) {
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    val idle = scheme.onSurface.copy(alpha = 0.22f)
    val zone = colors.brandStart.copy(alpha = 0.14f)
    val head = scheme.onSurface
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(WaveHeight)
    ) {
        val span = (state.valueRange.endInclusive - state.valueRange.start).coerceAtLeast(1f)
        val startX = (state.activeRangeStart - state.valueRange.start) / span * size.width
        val endX = (state.activeRangeEnd - state.valueRange.start) / span * size.width
        // Le dégradé couvre toute la largeur : chaque barre prend la teinte de sa position,
        // la sélection est un morceau de la signature, pas un aplat.
        val brand = Brush.horizontalGradient(listOf(colors.brandStart, colors.brandEnd), startX = 0f, endX = size.width)

        drawRoundRect(
            color = zone,
            topLeft = Offset(startX, 0f),
            size = Size((endX - startX).coerceAtLeast(0f), size.height),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )

        val bar = BarWidth.toPx()
        val step = bar + BarGap.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val mid = size.height / 2f
        for (i in 0 until count) {
            val x = i * step + bar / 2f
            val a = amplitudes[(i * amplitudes.size / count).coerceIn(0, amplitudes.lastIndex)]
            val h = (size.height * 0.86f * a).coerceAtLeast(bar)
            val inside = x in startX..endX
            val topLeft = Offset(x - bar / 2f, mid - h / 2f)
            val barSize = Size(bar, h)
            val radius = CornerRadius(bar / 2f)
            if (inside) {
                drawRoundRect(brush = brand, topLeft = topLeft, size = barSize, cornerRadius = radius)
            } else {
                drawRoundRect(color = idle, topLeft = topLeft, size = barSize, cornerRadius = radius)
            }
        }

        val ms = playhead()
        if (ms >= 0f) {
            val px = (ms / totalDuration).coerceIn(0f, 1f) * size.width
            drawLine(head, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/**
 * Poignée lumineuse : un trait fin à la signature sur toute la hauteur de l'onde, et une
 * perle au centre qui grossit sous le doigt.
 */
@Composable
private fun GlowHandle(source: MutableInteractionSource) {
    val colors = SynxioTheme.colors
    val reduce = LocalReduceMotion.current
    val glow = LocalGlowEffects.current
    val dragged by source.collectIsDraggedAsState()
    val pressed by source.collectIsPressedAsState()
    val active = dragged || pressed
    val scale by animateFloatAsState(
        targetValue = if (active && !reduce) 1.3f else 1f,
        animationSpec = Motion.spatialBouncy(),
        label = "handleScale",
    )
    Box(
        Modifier
            .width(HandleWidth)
            .height(WaveHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (glow) {
            Box(
                Modifier
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, colors.brandEnd.copy(alpha = if (active) 0.45f else 0.25f), Color.Transparent)
                        )
                    )
            )
        }
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(SynxioShape.pill)
                .background(Brush.verticalGradient(listOf(colors.brandStart, colors.brandEnd)))
        )
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .glowShadow(CircleShape, colors.brandEnd, elevation = if (active) 16.dp else 8.dp)
                .clip(CircleShape)
                .background(colors.brandBrush),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.onBrand)
            )
        }
    }
}

/**
 * Amplitudes pseudo-aléatoires mais stables pour un même titre : un bruit lissé, porté
 * par une enveloppe qui monte doucement puis retombe en fin de morceau.
 */
private fun waveAmplitudes(seed: Long, n: Int = 120): FloatArray {
    var s = seed xor 0x5DEECE66DL
    fun next(): Float {
        s = (s * 6364136223846793005L + 1442695040888963407L)
        return ((s ushr 33).toInt() and 0xFFFF) / 65535f
    }
    val raw = FloatArray(n) { next() }
    return FloatArray(n) { i ->
        val prev = raw[(i - 1).coerceAtLeast(0)]
        val nextV = raw[(i + 1).coerceAtMost(n - 1)]
        val smooth = (prev + raw[i] * 2f + nextV) / 4f
        val t = i / (n - 1f)
        val envelope = 0.45f + 0.55f * sin(t * PI.toFloat()).let { abs(it) }
        (0.18f + 0.82f * smooth * envelope).coerceIn(0.1f, 1f)
    }
}

/** Réglage fin d'une borne : disque de verre de 48 dp, libellé lisible en `labelLarge`. */
@Composable
private fun NudgeButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, Motion.effects(), label = "nudgeAlpha")
    Box(
        Modifier
            .pressBounce(0.9f)
            .size(Space.touchTarget)
            .clip(CircleShape)
            .glass(CircleShape, strength = 0.8f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}
