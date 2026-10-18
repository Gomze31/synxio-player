package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import androidx.compose.ui.graphics.Color
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TrimArtworkSize = 64.dp
private val PreviewButtonSize = 64.dp
private val PreviewIconSize = 32.dp
private val ProgressStroke = 2.dp

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

    val numbers = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Découpage audio") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Carte d'information sur la chanson
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SynxioShape.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(
                        model = song.artworkUri,
                        modifier = Modifier.size(TrimArtworkSize),
                        shape = SynxioShape.artworkSm,
                    )
                    Spacer(modifier = Modifier.width(Space.lg))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
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
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Space.xl))

            // Durée de la sélection
            val selectionDuration = (range.endInclusive - range.start).toLong()
            Surface(
                shape = SynxioShape.card,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(bottom = Space.lg)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Extrait sélectionné",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = selectionDuration.asDuration(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Slider de sélection
            RangeSlider(
                value = range,
                onValueChange = { newRange ->
                    stopPreview()
                    range = newRange
                },
                valueRange = 0f..totalDuration,
                modifier = Modifier.fillMaxWidth()
            )

            // Début et fin avec réglages fins (-1s / +1s). Boutons à 48 dp : à 36 dp, ils
            // passaient sous la cible tactile minimale et leur libellé était illisible.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contrôles du Début
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Début : ${range.start.toLong().asDuration()}",
                        style = numbers
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
                    Text(
                        text = "Fin : ${range.endInclusive.toLong().asDuration()}",
                        style = numbers
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

            // Boutons de pré-écoute
            Text(
                text = "Pré-écoute de l'extrait",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(Space.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tester le début (3s)
                OutlinedButton(
                    onClick = {
                        val start = range.start.toLong()
                        playPreview(start, (start + 3000L).coerceAtMost(range.endInclusive.toLong()))
                    },
                    shape = SynxioShape.pill,
                    modifier = Modifier.heightIn(min = Space.touchTarget)
                ) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Début 3s")
                }

                // Écouter tout l'extrait
                Button(
                    onClick = {
                        if (isPlayingPreview) {
                            stopPreview()
                        } else {
                            playPreview(range.start.toLong(), range.endInclusive.toLong())
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(PreviewButtonSize),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingPreview) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlayingPreview) "Arrêter la pré-écoute" else "Écouter l'extrait",
                        modifier = Modifier.size(PreviewIconSize)
                    )
                }

                // Tester la fin (3s)
                OutlinedButton(
                    onClick = {
                        val end = range.endInclusive.toLong()
                        playPreview((end - 3000L).coerceAtLeast(range.start.toLong()), end)
                    },
                    shape = SynxioShape.pill,
                    modifier = Modifier.heightIn(min = Space.touchTarget)
                ) {
                    Text("Fin 3s")
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(Icons.Rounded.FastForward, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
            }

            Spacer(modifier = Modifier.height(Space.xxl))

            // Bouton de sauvegarde
            Button(
                onClick = {
                    stopPreview()
                    isProcessing = true
                    viewModel.trimSong(song, range.start.toLong(), range.endInclusive.toLong())
                    onBack()
                },
                enabled = !isProcessing && selectionDuration >= 1000L,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget),
                shape = SynxioShape.pill
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = ProgressStroke,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Création du fichier audio…")
                } else {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Sauvegarder l'extrait découpé")
                }
            }
        }
    }
}

/** Réglage fin d'une borne : bouton tonal de 48 dp, libellé lisible en `labelLarge`. */
@Composable
private fun NudgeButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(Space.touchTarget)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
    }
}
