package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.AlbumStats
import fr.synxio.player.data.model.ArtistStats
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.StatsViewModel
import java.util.Locale

private val WEEKDAYS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/**
 * Statistiques d'écoute : résumé de la période choisie, podiums artistes/albums, genres,
 * titres récents et les plus joués, puis graphiques.
 *
 * Tous les flux viennent de propriétés du [StatsViewModel] : aucun appel ne crée de Flow
 * pendant la composition (chaque recomposition en aurait créé un nouveau). Chaque graphique
 * porte une description qui le résume en mots pour TalkBack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit
) {
    val statsViewModel: StatsViewModel = hiltViewModel()

    val statsSummary by statsViewModel.statsSummary.collectAsStateWithLifecycle()
    val periodStats by statsViewModel.periodStats.collectAsStateWithLifecycle()
    val selectedPeriod by statsViewModel.selectedPeriod.collectAsStateWithLifecycle()
    val topArtists by statsViewModel.topArtists.collectAsStateWithLifecycle()
    val topAlbums by statsViewModel.topAlbums.collectAsStateWithLifecycle()
    val topGenres by statsViewModel.topGenres.collectAsStateWithLifecycle()
    val playedSongsCount by statsViewModel.playedSongsCount.collectAsStateWithLifecycle()
    val unplayedSongsCount by statsViewModel.unplayedSongsCount.collectAsStateWithLifecycle()
    val mostPlayedSongs by statsViewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val recentlyPlayedSongs by statsViewModel.recentlyPlayedSongs.collectAsStateWithLifecycle()
    val weekdayStats by statsViewModel.weekdayStats.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val recent = remember(recentlyPlayedSongs) { recentlyPlayedSongs.take(SONG_PREVIEW) }
    val most = remember(mostPlayedSongs) { mostPlayedSongs.take(SONG_PREVIEW) }
    val genres = remember(topGenres) { topGenres.sortedByDescending { it.second }.take(5) }
    val periodEmpty = periodStats.totalPlayCount == 0

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Statistiques") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // Rien n'a jamais été écouté : un graphique de zéros n'apprend rien.
        if (statsSummary.totalPlayCount == 0 && periodEmpty) {
            EmptyState(
                title = "Pas encore de statistiques",
                subtitle = "Écoute quelques titres : tes artistes, albums et habitudes d'écoute apparaîtront ici.",
                icon = Icons.Rounded.BarChart,
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
                PeriodSelector(selected = selectedPeriod, onSelect = statsViewModel::setSelectedPeriod)
            }

            item(key = "summary", contentType = "summary") {
                SummaryTiles(
                    plays = periodStats.totalPlayCount,
                    listenedMs = periodStats.totalPlayTimeMs,
                    librarySize = periodStats.totalSongs,
                )
            }

            if (periodEmpty) {
                item(key = "period-empty", contentType = "note") {
                    Text(
                        text = "Rien d'écouté sur cette période. Choisis une période plus longue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.lg),
                    )
                }
            }

            if (topArtists.isNotEmpty()) {
                item(key = "artists-title", contentType = "header") { SectionHeader("Artistes les plus écoutés") }
                item(key = "artists", contentType = "podium") {
                    Podium(
                        entries = topArtists.take(3),
                        label = { it.artist.name },
                        plays = { it.playCount },
                        artwork = { it.artist.artworkUri },
                        artworkShape = CircleShape,
                        clickLabel = "Ouvrir l'artiste",
                        onClick = { onOpenArtist(it.artist.name) },
                    )
                }
            }

            if (topAlbums.isNotEmpty()) {
                item(key = "albums-title", contentType = "header") { SectionHeader("Albums les plus écoutés") }
                item(key = "albums", contentType = "podium") {
                    Podium(
                        entries = topAlbums.take(3),
                        label = { it.album.title },
                        plays = { it.playCount },
                        artwork = { it.album.artworkUri },
                        artworkShape = SynxioShape.artworkMd,
                        clickLabel = "Ouvrir l'album",
                        onClick = { onOpenAlbum(it.album.id) },
                    )
                }
            }

            if (genres.isNotEmpty()) {
                item(key = "genres-title", contentType = "header") { SectionHeader("Genres") }
                item(key = "genres", contentType = "chart") { GenreBars(genres) }
            }

            if (recent.isNotEmpty()) {
                item(key = "recent-title", contentType = "header") { SectionHeader("Écoutés récemment") }
                // Clé par position : un même titre peut revenir plusieurs fois dans l'historique.
                itemsIndexed(recent, key = { index, _ -> "recent-$index" }, contentType = { _, _ -> "song" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(recentlyPlayedSongs, index) },
                        isCurrent = song.id == currentSongId,
                        isPlaying = song.id == currentSongId && isPlaying,
                        modifier = Modifier.padding(horizontal = Space.sm),
                    )
                }
            }

            if (most.isNotEmpty()) {
                item(key = "most-title", contentType = "header") { SectionHeader("Les plus joués") }
                itemsIndexed(most, key = { index, _ -> "most-$index" }, contentType = { _, _ -> "song" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(mostPlayedSongs, index) },
                        isCurrent = song.id == currentSongId,
                        isPlaying = song.id == currentSongId && isPlaying,
                        modifier = Modifier.padding(horizontal = Space.sm),
                    )
                }
            }

            if (weekdayStats.values.any { it > 0 }) {
                item(key = "weekday-title", contentType = "header") { SectionHeader("Lectures par jour de la semaine") }
                item(key = "weekday", contentType = "chart") { WeekdayBarChart(weekdayStats) }
            }

            if (playedSongsCount + unplayedSongsCount > 0) {
                item(key = "coverage-title", contentType = "header") { SectionHeader("Bibliothèque explorée") }
                item(key = "coverage", contentType = "chart") {
                    CoverageDonut(played = playedSongsCount, unplayed = unplayedSongsCount)
                }
            }
        }
    }
}

private const val SONG_PREVIEW = 5

/** Choix exclusif de la période, toujours visible (l'ancien menu caché était peu découvrable). */
@Composable
private fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(StatsPeriod.entries, key = { it.name }) { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(period.label) },
                shape = SynxioShape.pill,
            )
        }
    }
}

/** Trois chiffres clés de la période ; lus d'un seul tenant par TalkBack. */
@Composable
private fun SummaryTiles(plays: Int, listenedMs: Long, librarySize: Int) {
    val time = formatListening(listenedMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        StatTile(value = "$plays", label = if (plays > 1) "lectures" else "lecture", modifier = Modifier.weight(1f))
        StatTile(value = time, label = "d'écoute", modifier = Modifier.weight(1f))
        StatTile(value = "$librarySize", label = if (librarySize > 1) "titres" else "titre", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = SynxioShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$value $label" },
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Podium des trois premiers. Le rang est écrit en chiffres sur une médaille aux couleurs
 * sémantiques du thème ; la couleur du chiffre est choisie pour rester lisible sur chacune.
 */
@Composable
private fun <T> Podium(
    entries: List<T>,
    label: (T) -> String,
    plays: (T) -> Int,
    artwork: (T) -> Any?,
    artworkShape: androidx.compose.ui.graphics.Shape,
    clickLabel: String,
    onClick: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        entries.forEachIndexed { index, entry ->
            RankCard(
                rank = index + 1,
                label = label(entry),
                plays = plays(entry),
                artwork = artwork(entry),
                artworkShape = artworkShape,
                clickLabel = clickLabel,
                onClick = { onClick(entry) },
                modifier = Modifier.weight(1f),
            )
        }
        // Moins de trois entrées : on garde la largeur d'une carte pour ne pas les étirer.
        repeat(3 - entries.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun RankCard(
    rank: Int,
    label: String,
    plays: Int,
    artwork: Any?,
    artworkShape: androidx.compose.ui.graphics.Shape,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val medal = rankColor(rank)
    val playsText = if (plays > 1) "$plays lectures" else "$plays lecture"
    Column(
        modifier = modifier
            .clip(SynxioShape.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$rank. $label, $playsText" }
            .padding(Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Artwork(model = artwork, shape = artworkShape, modifier = Modifier.size(64.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(medal),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = readableOn(medal),
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = playsText,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rankColor(rank: Int): Color = when (rank) {
    1 -> SynxioTheme.colors.rankGold
    2 -> SynxioTheme.colors.rankSilver
    else -> SynxioTheme.colors.rankBronze
}

/** Noir ou blanc, selon celui qui contraste le plus avec [background] (rapport WCAG). */
private fun readableOn(background: Color): Color {
    val l = background.luminance()
    val withBlack = (l + 0.05f) / 0.05f
    val withWhite = 1.05f / (l + 0.05f)
    return if (withBlack >= withWhite) Color.Black else Color.White
}

/** Barres horizontales : lisibles même avec des noms de genre longs. */
@Composable
private fun GenreBars(genres: List<Pair<String, Int>>) {
    val max = genres.maxOf { it.second }.coerceAtLeast(1)
    val summary = genres.joinToString(prefix = "Genres les plus écoutés : ") { (name, count) -> "$name, $count" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        genres.forEach { (name, count) ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Bar(fraction = count.toFloat() / max, horizontal = true)
            }
        }
    }
}

/** Barre de graphique animée (coupée si les animations sont réduites). */
@Composable
private fun Bar(fraction: Float, horizontal: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.standardOrSnap(reduce, Motion.LONG),
        label = "bar",
    )
    val scheme = MaterialTheme.colorScheme
    if (horizontal) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(Space.sm)
                .clip(SynxioShape.pill)
                .background(scheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(SynxioShape.pill)
                    .background(scheme.primary)
            )
        }
    } else {
        Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animated.coerceAtLeast(0.02f))
                    .clip(SynxioShape.pill)
                    .background(if (fraction > 0f) scheme.primary else scheme.surfaceContainerHighest)
            )
        }
    }
}

@Composable
private fun WeekdayBarChart(stats: Map<String, Int>) {
    val max = (stats.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val summary = remember(stats) {
        val busiest = stats.maxByOrNull { it.value }?.key
        "Lectures par jour : " + WEEKDAYS.joinToString { "${it.lowercase(Locale.FRENCH)} ${stats[it] ?: 0}" } +
            (busiest?.let { ". Jour le plus actif : ${it.lowercase(Locale.FRENCH)}" } ?: "")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        WEEKDAYS.forEach { day ->
            val count = stats[day] ?: 0
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Space.xs))
                Bar(
                    fraction = count.toFloat() / max,
                    horizontal = false,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(140.dp),
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = day.take(3),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Part de la bibliothèque déjà écoutée, en anneau + légende chiffrée. */
@Composable
private fun CoverageDonut(played: Int, unplayed: Int) {
    val total = played + unplayed
    val fraction = if (total > 0) played.toFloat() / total else 0f
    val percent = (fraction * 100).toInt()
    val reduce = LocalReduceMotion.current
    val sweep by animateFloatAsState(
        targetValue = fraction * 360f,
        animationSpec = Motion.standardOrSnap(reduce, Motion.LONG),
        label = "donut",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .heightIn(min = Space.touchTarget)
            .clearAndSetSemantics {
                contentDescription = "$played titres écoutés sur $total, soit $percent %. $unplayed jamais écoutés."
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                if (sweep > 0f) {
                    drawArc(fill, startAngle = -90f, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                }
            }
            Text(
                "$percent %",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
        Spacer(Modifier.width(Space.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            LegendLine(color = fill, value = played, label = "écoutés")
            LegendLine(color = track, value = unplayed, label = "jamais écoutés")
        }
    }
}

@Composable
private fun LegendLine(color: Color, value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            "$value",
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(Space.xs))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** « 3 h 05 » ou « 42 min » : court, pour tenir dans une tuile. */
private fun formatListening(ms: Long): String {
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return when {
        hours > 0 -> String.format(Locale.FRENCH, "%d h %02d", hours, minutes)
        else -> "$minutes min"
    }
}
