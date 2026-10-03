package fr.synxio.player.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Découpage audio",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Carte d'information sur la chanson
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Durée de la sélection
            val selectionDuration = (range.endInclusive - range.start).toLong()
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Extrait sélectionné",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = selectionDuration.asDuration(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
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

            // Début et fin avec réglages fins (-1s / +1s)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contrôles du Début
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Début : ${range.start.toLong().asDuration()}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                val newStart = (range.start - 1000f).coerceAtLeast(0f)
                                if (newStart < range.endInclusive) range = newStart..range.endInclusive
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = {
                                val newStart = (range.start + 1000f).coerceAtMost(range.endInclusive - 1000f)
                                range = newStart..range.endInclusive
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Contrôles de la Fin
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Fin : ${range.endInclusive.toLong().asDuration()}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                val newEnd = (range.endInclusive - 1000f).coerceAtLeast(range.start + 1000f)
                                range = range.start..newEnd
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = {
                                val newEnd = (range.endInclusive + 1000f).coerceAtMost(totalDuration)
                                range = range.start..newEnd
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Boutons de pré-écoute
            Text(
                text = "Pré-écoute de l'extrait",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
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
                    modifier = Modifier.size(60.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingPreview) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlayingPreview) "Pause" else "Lire",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Tester la fin (3s)
                OutlinedButton(
                    onClick = {
                        val end = range.endInclusive.toLong()
                        playPreview((end - 3000L).coerceAtLeast(range.start.toLong()), end)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fin 3s")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Rounded.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

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
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Création du fichier audio...")
                } else {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sauvegarder l'extrait découpé", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
