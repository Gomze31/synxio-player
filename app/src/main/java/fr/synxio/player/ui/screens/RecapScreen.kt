package fr.synxio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Recap
import fr.synxio.player.data.model.RecapPeriod
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.viewmodel.RecapViewModel
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Récap de la période, en cartes plein écran que l'on fait défiler comme des stories.
 *
 * Une idée par carte : un chiffre, un titre, un classement. Le texte reste court et gros,
 * lisible d'un coup d'œil, et la dernière carte propose de partager le bilan en image.
 */
@Composable
fun RecapScreen(
    onBack: () -> Unit,
    onPlay: (List<Song>) -> Unit,
) {
    val viewModel: RecapViewModel = hiltViewModel()
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val colors = SynxioTheme.colors
    val background = MaterialTheme.colorScheme.background

    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                // Aurore de la marque, plus franche qu'ailleurs : c'est un écran de fête.
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.brandEnd.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.maxDimension * 0.8f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.brandStart.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(0f, size.height),
                        radius = size.maxDimension * 0.7f,
                    )
                )
            }
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                }
                Spacer(Modifier.weight(1f))
                RecapPeriod.entries.forEach { option ->
                    LumenChip(
                        label = option.label,
                        selected = option == period,
                        onClick = { viewModel.setPeriod(option) },
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
                Spacer(Modifier.width(Space.sm))
            }

            val current = recap
            when {
                current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                current.isTooEarly -> EmptyState(
                    title = "Ton récap se prépare",
                    subtitle = "Il faut au moins 5 écoutes sur la période pour en tirer un bilan " +
                        "(tu en as ${current.plays}). Chaque titre écouté jusqu'à la moitié compte.",
                    icon = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.fillMaxSize(),
                )

                // Changer de période repart de la première carte.
                else -> key(current.period) {
                    RecapPager(
                        recap = current,
                        onPlay = onPlay,
                        onShare = viewModel::shareIntent,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapPager(
    recap: Recap,
    onPlay: (List<Song>) -> Unit,
    onShare: suspend (Recap) -> android.content.Intent?,
) {
    val pages = remember(recap) { recapPages(recap) }
    val pagerState = rememberPagerState { pages.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key = { "${recap.period}-${pages[it]}" },
        ) { page ->
            RecapCardFrame(pagerState, page) {
                when (pages[page]) {
                    RecapPage.TIME -> TimeCard(recap)
                    RecapPage.TOP_SONG -> TopSongCard(recap)
                    RecapPage.TOP_ARTISTS -> TopArtistsCard(recap)
                    RecapPage.MOMENT -> MomentCard(recap)
                    RecapPage.DISCOVERIES -> DiscoveriesCard(recap)
                    RecapPage.SUMMARY -> SummaryCard(
                        recap = recap,
                        onPlay = { onPlay(recap.topSongs.map { it.item }) },
                        onShare = {
                            scope.launch {
                                onShare(recap)?.let {
                                    context.startActivity(
                                        android.content.Intent.createChooser(it, "Partager mon récap")
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        PageDots(count = pages.size, current = pagerState.currentPage)
    }
}

private enum class RecapPage { TIME, TOP_SONG, TOP_ARTISTS, MOMENT, DISCOVERIES, SUMMARY }

/** Les cartes sans contenu sont retirées : pas de « top artistes » vide. */
private fun recapPages(recap: Recap): List<RecapPage> = buildList {
    add(RecapPage.TIME)
    if (recap.topSongs.isNotEmpty()) add(RecapPage.TOP_SONG)
    if (recap.topArtists.isNotEmpty()) add(RecapPage.TOP_ARTISTS)
    if (recap.favoriteDay != null) add(RecapPage.MOMENT)
    add(RecapPage.DISCOVERIES)
    add(RecapPage.SUMMARY)
}

/** Les cartes voisines reculent et s'estompent pendant le glissement. */
@Composable
private fun RecapCardFrame(pagerState: PagerState, page: Int, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (!reduce) {
                    val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.08f * offset
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.5f * offset
                }
            }
            .padding(horizontal = Space.gutter, vertical = Space.lg),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(Space.md))
}

@Composable
private fun BigNumber(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.displayLarge.merge(TabularNumbers)
            .copy(brush = SynxioTheme.colors.brandBrushHorizontal),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimeCard(recap: Recap) {
    Eyebrow("Ton récap · ${recap.period.label}")
    Text("Tu as écouté", style = MaterialTheme.typography.headlineSmall)
    BigNumber(recap.totalListenedMs.asLongDuration())
    Spacer(Modifier.height(Space.md))
    Caption("${recap.plays} lectures, ${recap.distinctSongs.pluralSongs()} différents, sur ${recap.activeDays} jours.")
}

@Composable
private fun TopSongCard(recap: Recap) {
    val top = recap.topSongs.first()
    val tint = SynxioTheme.colors.brandEnd
    Eyebrow("Ton titre n°1")
    Artwork(
        model = top.item.artworkUri,
        modifier = Modifier
            .size(220.dp)
            .glowShadow(SynxioShape.artworkLg, tint, elevation = 28.dp),
        shape = SynxioShape.artworkLg,
        contentDescription = "Pochette de ${top.item.title}",
    )
    Spacer(Modifier.height(Space.xl))
    Text(
        top.item.title,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Caption(top.item.displayArtist)
    Spacer(Modifier.height(Space.sm))
    Text(
        "Écouté ${top.plays} fois",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TopArtistsCard(recap: Recap) {
    Eyebrow("Tes artistes")
    recap.topArtists.forEachIndexed { index, rank ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers)
                    .copy(brush = SynxioTheme.colors.brandBrushHorizontal),
                modifier = Modifier.width(48.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    rank.item,
                    style = if (index == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${rank.plays} lectures · ${rank.listenedMs.asLongDuration()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MomentCard(recap: Recap) {
    val day = recap.favoriteDay?.getDisplayName(TextStyle.FULL, Locale.FRENCH).orEmpty()
    val part = recap.favoriteDayPart?.label.orEmpty()
    Eyebrow("Ton moment musique")
    Text("Tu écoutes surtout", style = MaterialTheme.typography.headlineSmall)
    BigNumber("le $day")
    if (part.isNotEmpty()) {
        Text(part, style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun DiscoveriesCard(recap: Recap) {
    Eyebrow("Tes découvertes")
    BigNumber("${recap.discoveries}")
    Text(
        if (recap.discoveries <= 1) "titre écouté pour la première fois" else "titres écoutés pour la première fois",
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(Space.md))
    Caption("Les mixs du jour de l'accueil t'en proposent de nouveaux chaque matin.")
}

@Composable
private fun SummaryCard(recap: Recap, onPlay: () -> Unit, onShare: () -> Unit) {
    Eyebrow("En résumé")
    Text("${recap.period.label}, c'était…", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(Space.lg))
    SummaryLine("Temps d'écoute", recap.totalListenedMs.asLongDuration())
    recap.topSongs.firstOrNull()?.let { SummaryLine("Titre n°1", it.item.title) }
    recap.topArtists.firstOrNull()?.let { SummaryLine("Artiste n°1", it.item) }
    SummaryLine("Découvertes", recap.discoveries.pluralSongs())
    Spacer(Modifier.height(Space.xl))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        BrandButton(text = "Partager", onClick = onShare, icon = Icons.Rounded.Share)
        if (recap.topSongs.isNotEmpty()) {
            GlassButton(text = "Écouter le top", onClick = onPlay, icon = Icons.Rounded.PlayArrow)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.lg)
            .semantics { contentDescription = "Carte ${current + 1} sur $count" },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) SynxioTheme.colors.brandBrushHorizontal
                        else Brush.linearGradient(
                            List(2) { MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f) }
                        )
                    ),
            )
        }
    }
}
