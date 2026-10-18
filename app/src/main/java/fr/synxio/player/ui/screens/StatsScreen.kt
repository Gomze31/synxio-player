package fr.synxio.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SharedKeys
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.sharedNavElement
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.StatsViewModel
import java.util.Locale

private val WEEKDAYS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** Durée du comptage des chiffres clés et de la pousse des barres à l'apparition. */
private const val COUNT_UP_MS = 900

/**
 * Statistiques d'écoute : résumé de la période choisie, podiums artistes/albums, genres,
 * titres récents et les plus joués, puis graphiques.
 *
 * Tous les flux viennent de propriétés du [StatsViewModel] : aucun appel ne crée de Flow
 * pendant la composition (chaque recomposition en aurait créé un nouveau). Chaque graphique
 * porte une description qui le résume en mots pour TalkBack.
 *
 * À l'apparition, les chiffres clés comptent depuis zéro et les barres poussent, une fois :
 * l'écran se « remplit » sous les yeux. Animations réduites, tout est posé d'emblée.
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Statistiques", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                bottom = padding.calculateBottomPadding() + Space.bottomInset + Space.xxl,
            ),
        ) {
            // Rang d'apparition des blocs, remis à zéro à chaque passage du constructeur.
            var order = 0

            val periodsIndex = order++
            item(key = "periods", contentType = "periods") {
                PeriodSelector(
                    selected = selectedPeriod,
                    onSelect = statsViewModel::setSelectedPeriod,
                    modifier = Modifier.enterCascade(periodsIndex, "stats-periods"),
                )
            }

            val summaryIndex = order++
            item(key = "summary", contentType = "summary") {
                SummaryTiles(
                    period = selectedPeriod,
                    plays = periodStats.totalPlayCount,
                    listenedMs = periodStats.totalPlayTimeMs,
                    librarySize = periodStats.totalSongs,
                    modifier = Modifier.enterCascade(summaryIndex, "stats-summary"),
                )
            }

            if (periodEmpty) {
                item(key = "period-empty", contentType = "note") {
                    Text(
                        text = "Rien d'écouté sur cette période. Choisis une période plus longue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = Space.gutter, vertical = Space.lg),
                    )
                }
            }

            if (topArtists.isNotEmpty()) {
                val index = order++
                item(key = "artists", contentType = "podium") {
                    Column(Modifier.animateItem().enterCascade(index, "stats-artists")) {
                        SectionHeader("Artistes les plus écoutés", eyebrow = "Podium")
                        Podium(
                            entries = topArtists.take(3),
                            label = { it.artist.name },
                            plays = { it.playCount },
                            artwork = { it.artist.artworkUri },
                            sharedKey = { SharedKeys.artistArt(it.artist.name) },
                            artworkShape = CircleShape,
                            clickLabel = "Ouvrir l'artiste",
                            onClick = { onOpenArtist(it.artist.name) },
                        )
                    }
                }
            }

            if (topAlbums.isNotEmpty()) {
                val index = order++
                item(key = "albums", contentType = "podium") {
                    Column(Modifier.animateItem().enterCascade(index, "stats-albums")) {
                        SectionHeader("Albums les plus écoutés", eyebrow = "Podium")
                        Podium(
                            entries = topAlbums.take(3),
                            label = { it.album.title },
                            plays = { it.playCount },
                            artwork = { it.album.artworkUri },
                            sharedKey = { SharedKeys.albumArt(it.album.id) },
                            artworkShape = SynxioShape.artworkMd,
                            clickLabel = "Ouvrir l'album",
                            onClick = { onOpenAlbum(it.album.id) },
                        )
                    }
                }
            }

            if (genres.isNotEmpty()) {
                val index = order++
                item(key = "genres", contentType = "chart") {
                    Column(Modifier.animateItem().enterCascade(index, "stats-genres")) {
                        SectionHeader("Genres", eyebrow = "Tes couleurs")
                        GenreBars(genres)
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item(key = "recent-title", contentType = "header") {
                    SectionHeader("Écoutés récemment", eyebrow = "Dernières écoutes", modifier = Modifier.animateItem())
                }
                // Clé par position : un même titre peut revenir plusieurs fois dans l'historique.
                itemsIndexed(recent, key = { index, _ -> "recent-$index" }, contentType = { _, _ -> "song" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(recentlyPlayedSongs, index) },
                        isCurrent = song.id == currentSongId,
                        isPlaying = song.id == currentSongId && isPlaying,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = Space.sm),
                    )
                }
            }

            if (most.isNotEmpty()) {
                item(key = "most-title", contentType = "header") {
                    SectionHeader("Les plus joués", eyebrow = "Classement", modifier = Modifier.animateItem())
                }
                itemsIndexed(most, key = { index, _ -> "most-$index" }, contentType = { _, _ -> "song" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(mostPlayedSongs, index) },
                        isCurrent = song.id == currentSongId,
                        isPlaying = song.id == currentSongId && isPlaying,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = Space.sm),
                        trailing = { RankBadge(rank = index + 1) },
                    )
                }
            }

            if (weekdayStats.values.any { it > 0 }) {
                item(key = "weekday", contentType = "chart") {
                    Column(Modifier.animateItem()) {
                        SectionHeader("Lectures par jour de la semaine", eyebrow = "Tes habitudes")
                        WeekdayBarChart(weekdayStats)
                    }
                }
            }

            if (playedSongsCount + unplayedSongsCount > 0) {
                item(key = "coverage", contentType = "chart") {
                    Column(Modifier.animateItem()) {
                        SectionHeader("Bibliothèque explorée", eyebrow = "Couverture")
                        CoverageDonut(played = playedSongsCount, unplayed = unplayedSongsCount)
                    }
                }
            }
        }
    }
}

private const val SONG_PREVIEW = 5

/** Choix exclusif de la période, toujours visible (l'ancien menu caché était peu découvrable). */
@Composable
private fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(StatsPeriod.entries, key = { it.name }) { period ->
            LumenChip(
                label = period.label,
                selected = period == selected,
                onClick = { onSelect(period) },
            )
        }
    }
}

/**
 * Chiffres clés de la période : le temps d'écoute en vedette sur toute la largeur, puis
 * lectures et taille de la bibliothèque côte à côte. Chaque carte est lue d'un tenant.
 */
@Composable
private fun SummaryTiles(
    period: StatsPeriod,
    plays: Int,
    listenedMs: Long,
    librarySize: Int,
    modifier: Modifier = Modifier,
) {
    // On compte en minutes : compter en millisecondes ferait défiler des chiffres illisibles.
    val minutes = countUp((listenedMs / 60_000).toInt())
    val shownPlays = countUp(plays)
    val shownSize = countUp(librarySize)
    val time = formatListening(listenedMs)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "$time d'écoute, ${period.label.lowercase(Locale.FRENCH)}" },
            contentPadding = Space.lg + Space.xs,
        ) {
            Column {
                Text(
                    "Temps d'écoute · ${period.label}".uppercase(),
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = formatListening(minutes * 60_000L),
                    style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatTile(
                value = "$shownPlays",
                finalValue = "$plays",
                label = if (plays > 1) "lectures" else "lecture",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "$shownSize",
                finalValue = "$librarySize",
                label = if (librarySize > 1) "titres" else "titre",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, finalValue: String, label: String, modifier: Modifier = Modifier) {
    GlassCard(
        // La valeur finale, pas celle en train de compter : TalkBack lit un chiffre stable.
        modifier = modifier.clearAndSetSemantics { contentDescription = "$finalValue $label" },
    ) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Podium des trois premiers, disposé comme un vrai podium : le premier au centre, plus
 * grand, encadré du deuxième et du troisième. TalkBack les lit tout de même dans l'ordre
 * du classement. Le rang est écrit en chiffres sur une médaille aux couleurs sémantiques
 * du thème ; la couleur du chiffre est choisie pour rester lisible sur chacune.
 */
@Composable
private fun <T> Podium(
    entries: List<T>,
    label: (T) -> String,
    plays: (T) -> Int,
    artwork: (T) -> Any?,
    sharedKey: (T) -> Any,
    artworkShape: Shape,
    clickLabel: String,
    onClick: (T) -> Unit,
) {
    // Ordre visuel 2 · 1 · 3 dès qu'il y a au moins deux marches.
    val visual = remember(entries) {
        val ranked = entries.mapIndexed { i, e -> (i + 1) to e }
        if (ranked.size >= 2) listOfNotNull(ranked.getOrNull(1), ranked[0], ranked.getOrNull(2)) else ranked
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        visual.forEach { (rank, entry) ->
            RankCard(
                rank = rank,
                label = label(entry),
                plays = plays(entry),
                artwork = artwork(entry),
                sharedKey = sharedKey(entry),
                artworkShape = artworkShape,
                clickLabel = clickLabel,
                onClick = { onClick(entry) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { traversalIndex = rank.toFloat() }
                    .enterCascade(rank, "podium-${sharedKey(entry)}"),
            )
        }
        // Moins de trois entrées : on garde la largeur d'une carte pour ne pas les étirer.
        repeat(3 - visual.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun RankCard(
    rank: Int,
    label: String,
    plays: Int,
    artwork: Any?,
    sharedKey: Any,
    artworkShape: Shape,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val medal = rankColor(rank)
    val playsText = if (plays > 1) "$plays lectures" else "$plays lecture"
    // La première marche est plus haute : une pochette plus grande et une lueur dorée.
    val artSize: Dp = if (rank == 1) 84.dp else 64.dp
    GlassCard(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$rank. $label, $playsText" },
        onClick = onClick,
        contentPadding = Space.md,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Artwork(
                    model = artwork,
                    shape = artworkShape,
                    modifier = Modifier
                        .padding(Space.xs)
                        .size(artSize)
                        .then(if (rank == 1) Modifier.glowShadow(artworkShape, medal, elevation = 18.dp) else Modifier)
                        .border(2.dp, medal, artworkShape)
                        .sharedNavElement(sharedKey, artworkShape),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(medal),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
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
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Rang en fin de ligne « Les plus joués » : médaille pour le podium, chiffre sinon. */
@Composable
private fun RankBadge(rank: Int) {
    val medal = if (rank <= 3) rankColor(rank) else null
    Box(
        modifier = Modifier
            .padding(start = Space.sm)
            .size(28.dp)
            .clip(CircleShape)
            .then(if (medal != null) Modifier.background(medal) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
            color = medal?.let(::readableOn) ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = summary },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            genres.forEachIndexed { index, (name, count) ->
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
                            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Space.xs))
                    Bar(fraction = count.toFloat() / max, horizontal = true, delayIndex = index)
                }
            }
        }
    }
}

/**
 * Barre de graphique à la signature Lumen qui pousse depuis zéro à l'apparition (puis suit
 * les changements de période), en léger décalage d'une barre à l'autre. Coupée si les
 * animations sont réduites.
 */
@Composable
private fun Bar(fraction: Float, horizontal: Boolean, modifier: Modifier = Modifier, delayIndex: Int = 0) {
    val reduce = LocalReduceMotion.current
    var started by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = if (reduce) snap() else tween(
            durationMillis = Motion.LONG + 150,
            delayMillis = (delayIndex * Motion.STAGGER_MS).toInt(),
            easing = Motion.Decelerate,
        ),
        label = "bar",
    )
    val colors = SynxioTheme.colors
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    if (horizontal) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(Space.sm)
                .clip(SynxioShape.pill)
                .background(track),
        ) {
            if (animated > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animated)
                        .clip(SynxioShape.pill)
                        .background(colors.brandBrushHorizontal)
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(SynxioShape.pill)
                    .background(track)
            )
            if (fraction > 0f && animated > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animated.coerceAtLeast(0.04f))
                        .clip(SynxioShape.pill)
                        // Verticale : la signature monte de la braise vers le magenta.
                        .background(Brush.verticalGradient(listOf(colors.brandEnd, colors.brandStart)))
                )
            }
        }
    }
}

@Composable
private fun WeekdayBarChart(stats: Map<String, Int>) {
    val max = (stats.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val busiest = stats.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    val summary = remember(stats) {
        "Lectures par jour : " + WEEKDAYS.joinToString { "${it.lowercase(Locale.FRENCH)} ${stats[it] ?: 0}" } +
            (busiest?.let { ". Jour le plus actif : ${it.lowercase(Locale.FRENCH)}" } ?: "")
    }
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = summary },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            WEEKDAYS.forEachIndexed { index, day ->
                val count = stats[day] ?: 0
                val isBusiest = day == busiest
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                        color = if (isBusiest) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Bar(
                        fraction = count.toFloat() / max,
                        horizontal = false,
                        delayIndex = index,
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(140.dp),
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        text = day.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isBusiest) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Part de la bibliothèque déjà écoutée, en anneau à la signature + légende chiffrée. */
@Composable
private fun CoverageDonut(played: Int, unplayed: Int) {
    val total = played + unplayed
    val fraction = if (total > 0) played.toFloat() / total else 0f
    val percent = (fraction * 100).toInt()
    val reduce = LocalReduceMotion.current
    var started by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { started = true }
    val sweep by animateFloatAsState(
        targetValue = if (started) fraction * 360f else 0f,
        animationSpec = if (reduce) snap() else tween(COUNT_UP_MS + 300, easing = Motion.Decelerate),
        label = "donut",
    )
    val shownPercent = countUp(percent)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val colors = SynxioTheme.colors
    val ring = remember(colors.brandStart, colors.brandEnd) {
        Brush.sweepGradient(listOf(colors.brandStart, colors.brandEnd, colors.brandStart))
    }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics {
                contentDescription = "$played titres écoutés sur $total, soit $percent %. $unplayed jamais écoutés."
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    val topLeft = Offset(inset, inset)
                    drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                    if (sweep > 0f) {
                        drawArc(ring, startAngle = -90f, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                    }
                }
                Text(
                    "$shownPercent %",
                    style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                )
            }
            Spacer(Modifier.width(Space.xl))
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                LegendLine(brush = colors.brandBrush, value = played, label = "écoutés")
                LegendLine(brush = Brush.linearGradient(listOf(track, track)), value = unplayed, label = "jamais écoutés")
            }
        }
    }
}

@Composable
private fun LegendLine(brush: Brush, value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(brush)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            "$value",
            style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
        )
        Spacer(Modifier.width(Space.xs))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** « 3 h 05 » ou « 42 min » : court, pour tenir dans une tuile. */
private fun formatListening(ms: Long): String {
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return when {
        hours > 0 -> String.format(Locale.FRENCH, "%d h %02d", hours, minutes)
        else -> "$minutes min"
    }
}
