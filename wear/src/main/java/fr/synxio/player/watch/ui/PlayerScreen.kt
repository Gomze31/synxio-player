package fr.synxio.player.watch.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.LevelIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.synxio.player.watch.R
import fr.synxio.player.watch.ui.theme.Ink
import kotlinx.coroutines.delay

/**
 * Écran lecteur : fond pochette floutée, pochette ronde cerclée de progression, titre,
 * artiste, précédent · lecture/pause · suivant, cœur. La couronne règle le volume.
 */
@Composable
fun PlayerScreen(
    ui: PlayerUiState.Playing,
    art: ByteArray?,
    artBlurred: ByteArray?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onFavorite: () -> Unit,
    onVolumeStep: (Int) -> Unit,
    now: () -> Long,
) {
    val ambient = LocalAmbient.current
    val artBitmap = remember(art) { art?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val blurBitmap = remember(artBlurred) { artBlurred?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }

    // Tick par seconde pour l'anneau, hors ambiant et seulement en lecture.
    var tick by remember { mutableLongStateOf(now()) }
    LaunchedEffect(ui.isPlaying, ambient) {
        tick = now()
        if (!ui.isPlaying || ambient) return@LaunchedEffect
        while (true) {
            delay(1_000)
            tick = now()
        }
    }
    val progress = if (ui.isPlaying) ui.state.progressFractionAt(tick) else ui.state.progressFractionAt(ui.state.capturedAtMs)

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ScreenScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
                .onRotaryScrollEvent { event ->
                    onVolumeStep(if (event.verticalScrollPixels > 0f) 1 else -1)
                    true
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            if (!ambient && blurBitmap != null) {
                Image(blurBitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.6f)))
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    if (!ui.state.live && ui.state.durationMs > 0L) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 3.dp,
                        )
                    }
                    if (artBitmap != null && !ambient) {
                        Image(artBitmap, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
                    } else {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = ui.state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee().alpha(if (ambient) 0.7f else 1f),
                )
                Text(
                    text = ui.state.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!ambient) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous))
                        }
                        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(52.dp)) {
                            Icon(
                                if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(if (ui.isPlaying) R.string.pause else R.string.play),
                            )
                        }
                        IconButton(onClick = onNext, enabled = ui.state.hasNext) {
                            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HeartButton(favorite = ui.favorite, onClick = onFavorite)
                }
            }

            AnimatedVisibility(
                visible = ui.volumeVisible && !ambient,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                LevelIndicator(value = { ui.volume }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Cœur plein ou vide ; battement de 250 ms et retour haptique à la bascule. */
@Composable
private fun HeartButton(favorite: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var beat by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (beat) 1.25f else 1f,
        animationSpec = keyframes { durationMillis = 250; 1.25f at 100; 1f at 250 },
        finishedListener = { beat = false },
        label = "heart",
    )
    IconButton(
        onClick = {
            beat = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.scale(scale),
    ) {
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else Color.White,
        )
    }
}
