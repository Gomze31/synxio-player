package fr.synxio.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.HistoryViewModel

/** Durée du comptage des chiffres et de la pousse des barres à l'apparition. */
private const val COUNT_UP_MS = 900

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Historique", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                bottom = padding.calculateBottomPadding() + Space.bottomInset + Space.xl,
            ),
        ) {
            item(key = "periods", contentType = "periods") {
                LazyRow(
                    modifier = Modifier.enterCascade(0, "history-periods"),
                    contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(StatsPeriod.entries, key = { it.name }) { period ->
                        LumenChip(
                            label = period.label,
                            selected = period == periodFilter,
                            onClick = { historyViewModel.setPeriodFilter(period) },
                        )
                    }
                }
            }

            item(key = "summary", contentType = "summary") {
                HistorySummary(
                    count = filteredCount,
                    period = periodFilter,
                    lastSevenDays = lastSevenDays,
                    modifier = Modifier.enterCascade(1, "history-summary"),
                )
            }

            if (days.isEmpty()) {
                item(key = "period-empty", contentType = "note") {
                    Text(
                        text = "Aucune écoute sur cette période.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = Space.gutter, vertical = Space.lg),
                    )
                }
            }

            // Rang global : seules les premières lignes de l'écran entrent en cascade.
            var rank = 0
            days.forEach { day ->
                stickyHeader(key = "day-${day.key}", contentType = "day") {
                    DayHeader(label = day.label, count = day.entries.size)
                }
                val base = rank
                rank += day.entries.size
                itemsIndexed(day.entries, key = { _, it -> it.key }, contentType = { _, _ -> "entry" }) { i, entry ->
                    val song = entry.item.song
                    val position = base + i
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
                        modifier = Modifier
                            .then(if (reduceMotion) Modifier else Modifier.animateItem())
                            .then(
                                if (position < Motion.MAX_STAGGERED) Modifier.enterCascade(position + 2, entry.key)
                                else Modifier
                            ),
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text("Effacer l'historique ?", style = MaterialTheme.typography.headlineSmall) },
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

/**
 * En-tête de jour collant : une pastille de verre en surtitre plutôt qu'une bande opaque,
 * pour que le fond aurore reste visible pendant le défilement.
 */
@Composable
private fun DayHeader(label: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
    ) {
        Row(
            Modifier
                .clip(SynxioShape.pill)
                .glass(SynxioShape.pill, strength = 1f)
                .padding(horizontal = Space.md, vertical = Space.xs + Space.xxs)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = "  ·  $count",
                style = EyebrowTextStyle.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Nombre d'écoutes de la période (compté à l'apparition) et activité des sept derniers jours. */
@Composable
private fun HistorySummary(
    count: Int,
    period: StatsPeriod,
    lastSevenDays: List<HistoryViewModel.DayCount>,
    modifier: Modifier = Modifier,
) {
    val shown = countUp(count)
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        contentPadding = Space.lg + Space.xs,
    ) {
        Column {
            Text(
                text = period.label.uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Space.xs))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = if (count > 1) "$count écoutes" else "$count écoute"
                },
            ) {
                Text(
                    text = "$shown",
                    style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(Space.sm))
                Text(
                    text = if (count > 1) "écoutes" else "écoute",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.xs),
                )
            }
            if (lastSevenDays.any { it.count > 0 }) {
                Spacer(Modifier.height(Space.lg))
                SevenDayChart(lastSevenDays)
            }
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
    val busiest = days.maxOf { it.count }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${day.count}",
                    style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                    color = if (day.count == busiest && busiest > 0) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Space.xs))
                GrowingBar(
                    fraction = day.count.toFloat() / max,
                    delayIndex = index,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(72.dp),
                )
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
 * Barre verticale à la signature Lumen qui pousse depuis le bas à l'apparition, en léger
 * décalage d'une colonne à l'autre. Une barre vide garde un socle discret.
 */
@Composable
private fun GrowingBar(fraction: Float, delayIndex: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    var started by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { started = true }
    val grown by animateFloatAsState(
        targetValue = if (started) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = if (reduce) snap() else tween(
            durationMillis = Motion.LONG + 150,
            delayMillis = (delayIndex * Motion.STAGGER_MS).toInt(),
            easing = Motion.Decelerate,
        ),
        label = "historyBar",
    )
    val brand = SynxioTheme.colors.brandBrush
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        // Socle : la colonne reste lisible même à zéro.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(SynxioShape.pill)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        )
        if (grown > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(grown.coerceAtLeast(0.06f))
                    .clip(SynxioShape.pill)
                    .background(brand)
            )
        }
    }
}

/** Compte de 0 jusqu'à [target] à la première apparition, puis suit ses changements. */
@Composable
private fun countUp(target: Int): Int {
    val reduce = LocalReduceMotion.current
    var started by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { started = true }
    val value by animateIntAsState(
        targetValue = if (started) target else 0,
        animationSpec = if (reduce) snap() else tween(COUNT_UP_MS, easing = Motion.Decelerate),
        label = "countUp",
    )
    return value
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
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.sm),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(Space.touchTarget)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options de ${song.title}")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = SynxioShape.cardSm,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
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
private fun MenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick,
    )
}
