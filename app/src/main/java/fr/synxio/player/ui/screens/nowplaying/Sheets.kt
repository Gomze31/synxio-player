package fr.synxio.player.ui.screens.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.LyricLine
import fr.synxio.player.data.model.Lyrics
import fr.synxio.player.data.model.Song
import fr.synxio.player.playback.SleepTimerState
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.LyricsTextStyle

/**
 * Paroles synchronisées. La ligne active grossit et s'éclaire ; toucher une ligne
 * déplace la lecture à cet instant, ce qui rend l'écran utile et pas seulement joli.
 */
@Composable
fun LyricsPane(
    lyrics: Lyrics,
    loading: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        Box(modifier, Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (lyrics.lines.isEmpty()) {
        Box(modifier, Alignment.Center) {
            Text(
                text = "Aucune parole trouvée pour ce titre.\nDépose un fichier .lrc à côté du morceau,\nou active la recherche en ligne dans les réglages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val activeIndex = remember(positionMs, lyrics) { lyrics.indexAt(positionMs) }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            // On garde la ligne active à un tiers de l'écran : on lit ce qui arrive.
            listState.animateScrollToItem(activeIndex.coerceAtLeast(0), scrollOffset = -220)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricRow(
                line = line,
                active = index == activeIndex,
                synced = lyrics.synced,
                onClick = { line.timeMs?.let(onSeek) },
            )
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, synced: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(
        targetValue = when {
            !synced -> scheme.onSurface
            active -> scheme.primary
            else -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        label = "lyricColor",
    )
    val scale by animateFloatAsState(if (active) 1f else 0.92f, label = "lyricScale")

    Text(
        text = line.text.ifBlank { "♪" },
        style = LyricsTextStyle.copy(fontSize = LyricsTextStyle.fontSize * scale),
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = synced, onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

@Composable
fun QueueSheet(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "File d'attente",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Vider") }
            }
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                itemsIndexed(queue, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onSelect(index) },
                        isCurrent = index == currentIndex,
                        isPlaying = isPlaying,
                        trailing = {
                            // Réordonnancement au clavier : simple, accessible, sans drag fragile.
                            Column {
                                IconButton(
                                    onClick = { if (index > 0) onMove(index, index - 1) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Monter dans la file",
                                    )
                                }
                                IconButton(
                                    onClick = { if (index < queue.lastIndex) onMove(index, index + 1) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Descendre dans la file",
                                    )
                                }
                            }
                        },
                        onMenuClick = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStart: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(30) }
    var finishTrack by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "Minuterie de veille",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            if (state.active) {
                Text(
                    text = "Arrêt dans ${state.remainingMs.asDuration()}" +
                        if (state.finishCurrentTrack) " (à la fin du morceau)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { onCancel(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Annuler la minuterie") }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { preset ->
                    FilterChip(
                        selected = minutes == preset,
                        onClick = { minutes = preset },
                        label = { Text("$preset min") },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("$minutes minutes", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = it.toInt() },
                valueRange = 5f..180f,
                steps = 34,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Finir le morceau en cours", Modifier.weight(1f))
                Switch(checked = finishTrack, onCheckedChange = { finishTrack = it })
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onStart(minutes, finishTrack); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Démarrer") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SpeedSheet(
    speed: Float,
    pitch: Float,
    onApply: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentSpeed by remember { mutableFloatStateOf(speed) }
    var currentPitch by remember { mutableFloatStateOf(pitch) }
    var linkPitch by remember { mutableStateOf(pitch == speed) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "Vitesse et tonalité",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            Text("Vitesse : ${"%.2f".format(currentSpeed)}×")
            Slider(
                value = currentSpeed,
                onValueChange = {
                    currentSpeed = it
                    if (linkPitch) currentPitch = it
                },
                valueRange = 0.5f..2.5f,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Lier la tonalité à la vitesse", Modifier.weight(1f))
                Switch(
                    checked = linkPitch,
                    onCheckedChange = {
                        linkPitch = it
                        if (it) currentPitch = currentSpeed
                    },
                )
            }

            if (!linkPitch) {
                Text("Tonalité : ${"%.2f".format(currentPitch)}×")
                Slider(
                    value = currentPitch,
                    onValueChange = { currentPitch = it },
                    valueRange = 0.5f..2f,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
                    FilterChip(
                        selected = currentSpeed == preset,
                        onClick = {
                            currentSpeed = preset
                            if (linkPitch) currentPitch = preset
                        },
                        label = { Text("${preset}×") },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onApply(1f, 1f); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) { Text("Réinitialiser") }
                Button(
                    onClick = { onApply(currentSpeed, currentPitch); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) { Text("Appliquer") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun NowPlayingMenuSheet(
    song: Song,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onEditTags: () -> Unit,
    onRefreshLyrics: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.artworkUri, Modifier.size(52.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        text = "${song.extension} · ~${song.approxBitrateKbps} kb/s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            MenuRow(Icons.Rounded.Album, "Aller à l'album") { onOpenAlbum(); onDismiss() }
            MenuRow(Icons.Rounded.Person, "Aller à l'artiste") { onOpenArtist(); onDismiss() }
            MenuRow(Icons.Rounded.Edit, "Modifier les tags") { onEditTags(); onDismiss() }
            MenuRow(Icons.Rounded.Refresh, "Rechercher les paroles") { onRefreshLyrics(); onDismiss() }
            MenuRow(Icons.Rounded.Close, "Fermer") { onDismiss() }
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
