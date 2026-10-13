package fr.synxio.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.HistoryViewModel

/**
 * Historique de lecture, groupé par jour, filtrable par période.
 *
 * Le groupement et le formatage des heures sont faits dans [HistoryViewModel.days] :
 * la liste ne fait qu'afficher. Chaque ligne a un vrai menu (lire, file d'attente,
 * album, artiste) au lieu de l'ancien bouton sans effet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit
) {
    val historyViewModel: HistoryViewModel = hiltViewModel()

    val days by historyViewModel.days.collectAsStateWithLifecycle()
    val periodFilter by historyViewModel.periodFilter.collectAsStateWithLifecycle()
    val filteredCount by historyViewModel.filteredCount.collectAsStateWithLifecycle()
    val totalCount by historyViewModel.historyCount.collectAsStateWithLifecycle()
    val lastSevenDays by historyViewModel.lastSevenDays.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current

    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Historique") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (totalCount > 0) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Effacer l'historique")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (totalCount == 0) {
            EmptyState(
                title = "Aucune écoute enregistrée",
                subtitle = "Les titres que tu écoutes apparaîtront ici, jour par jour.",
                icon = Icons.Rounded.History,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Space.xxl,
            ),
        ) {
            item(key = "periods", contentType = "periods") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(StatsPeriod.entries, key = { it.name }) { period ->
                        FilterChip(
                            selected = period == periodFilter,
                            onClick = { historyViewModel.setPeriodFilter(period) },
                            label = { Text(period.label) },
                            shape = SynxioShape.pill,
                        )
                    }
                }
            }

            item(key = "summary", contentType = "summary") {
                HistorySummary(count = filteredCount, lastSevenDays = lastSevenDays)
            }

            if (days.isEmpty()) {
                item(key = "period-empty", contentType = "note") {
                    Text(
                        text = "Aucune écoute sur cette période.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.lg),
                    )
                }
            }

            days.forEach { day ->
                stickyHeader(key = "day-${day.key}", contentType = "day") {
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = Space.gutter, vertical = Space.sm)
                            .semantics { heading() },
                    )
                }
                items(day.entries, key = { it.key }, contentType = { "entry" }) { entry ->
                    val song = entry.item.song
                    HistoryRow(
                        song = song,
                        time = entry.time,
                        isCurrent = song.id == currentSongId,
                        isPlaying = song.id == currentSongId && isPlaying,
                        onPlay = { onPlaySong(listOf(song), 0) },
                        onPlayNext = { viewModel.playNext(listOf(song)) },
                        onAddToQueue = { viewModel.addToQueue(listOf(song)) },
                        onOpenAlbum = { onOpenAlbum(song.albumId) },
                        onOpenArtist = { onOpenArtist(song.artist) },
                        modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Effacer l'historique ?") },
            text = {
                Text("Toutes les écoutes enregistrées seront supprimées. Tes statistiques de lecture ne sont pas touchées. Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    historyViewModel.clear()
                }) { Text("Effacer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annuler") }
            },
        )
    }
}

/** Nombre d'écoutes de la période et activité des sept derniers jours. */
@Composable
private fun HistorySummary(count: Int, lastSevenDays: List<HistoryViewModel.DayCount>) {
    Column(modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
        Text(
            text = if (count > 1) "$count écoutes" else "$count écoute",
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (lastSevenDays.any { it.count > 0 }) {
            Spacer(Modifier.height(Space.md))
            SevenDayChart(lastSevenDays)
        }
    }
}

@Composable
private fun SevenDayChart(days: List<HistoryViewModel.DayCount>) {
    val max = days.maxOf { it.count }.coerceAtLeast(1)
    val summary = remember(days) {
        "Écoutes des sept derniers jours : " + days.joinToString { "${it.fullLabel} ${it.count}" }
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${day.count}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Space.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(64.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((day.count.toFloat() / max).coerceAtLeast(0.04f))
                            .clip(SynxioShape.pill)
                            .background(if (day.count > 0) scheme.primary else scheme.surfaceContainerHighest)
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = day.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Ligne d'historique : un appui lit le titre, un appui long ou le bouton « Options »
 * ouvre le menu d'actions.
 */
@Composable
private fun HistoryRow(
    song: Song,
    time: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    SongRow(
        song = song,
        onClick = onPlay,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        onLongClick = { menuOpen = true },
        modifier = modifier.padding(horizontal = Space.sm),
        trailing = {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.sm),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(Space.touchTarget)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options de ${song.title}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuEntry("Lire", Icons.Rounded.PlayArrow) { menuOpen = false; onPlay() }
                    MenuEntry("Lire ensuite", Icons.AutoMirrored.Rounded.QueueMusic) { menuOpen = false; onPlayNext() }
                    MenuEntry("Ajouter à la file", Icons.AutoMirrored.Rounded.PlaylistAdd) { menuOpen = false; onAddToQueue() }
                    MenuEntry("Ouvrir l'album", Icons.Rounded.Album) { menuOpen = false; onOpenAlbum() }
                    MenuEntry("Ouvrir l'artiste", Icons.Rounded.Person) { menuOpen = false; onOpenArtist() }
                }
            }
        },
    )
}

@Composable
private fun MenuEntry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
