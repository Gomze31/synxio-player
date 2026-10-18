package fr.synxio.player.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralAlbums
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.LibraryTab
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.data.repo.Mood
import fr.synxio.player.data.repo.SmartPlaylistId
import fr.synxio.player.ui.components.AddToPlaylistSheet
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.ArtistCard
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.FolderRow
import fr.synxio.player.ui.components.GenreRow
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.ScreenHeader
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.components.shimmer
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
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pastille d'icône des ambiances. */
private val MoodBadgeSize = 52.dp

/** Pastille de l'état « analyse requise » : même gabarit que celle d'[EmptyState]. */
private val StateBadgeSize = 96.dp

/**
 * Part de la largeur d'une page dont le contenu « retient » le glissement du pager : le
 * contenu avance un peu moins vite que la page, ce qui donne de la profondeur au geste.
 */
private const val PAGE_PARALLAX = 0.18f

@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmartPlaylist: (SmartPlaylistId) -> Unit,
    onOpenRulePlaylist: (Long) -> Unit,
    onCreateRulePlaylist: () -> Unit,
    onEditRulePlaylist: (Long) -> Unit,
    onOpenAudiobooks: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tabs = LibraryTab.entries
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(settings.defaultTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current

    // Le sous-titre dit d'un coup d'œil ce que contient la collection ; pendant le premier
    // scan, il dit que ça arrive plutôt que d'afficher « 0 titre ».
    val subtitle = remember(library.songs.size, library.albums.size, library.artists.size, library.hasScanned) {
        if (!library.hasScanned && library.songs.isEmpty()) {
            "Analyse de ta musique…"
        } else {
            val artists = library.artists.size
            listOf(
                library.songs.size.pluralSongs(),
                library.albums.size.pluralAlbums(),
                if (artists <= 1) "$artists artiste" else "$artists artistes",
            ).joinToString(" · ")
        }
    }

    Column(modifier.fillMaxSize()) {
        // En-tête éditorial : on sait où l'on est après une navigation. Les livres audio y
        // ont leur entrée depuis qu'ils ont quitté la barre de navigation (cinq
        // destinations au plus, règle Material).
        ScreenHeader(
            title = "Bibliothèque",
            eyebrow = "Ta collection",
            subtitle = subtitle,
            actions = {
                IconButton(onClick = onOpenAudiobooks, modifier = Modifier.size(Space.touchTarget)) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Livres audio")
                }
            },
        )

        LibraryTabRow(
            labels = remember(tabs) { tabs.map { it.label } },
            pagerState = pagerState,
            onSelect = { index ->
                scope.launch {
                    if (reduce) pagerState.scrollToPage(index)
                    else pagerState.animateScrollToPage(index, animationSpec = Motion.spatial())
                }
            },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { tabs[it].name },
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .pageTransition(pagerState, page, reduce)
            ) {
                when (tabs[page]) {
                    LibraryTab.SONGS -> SongsTab(viewModel, onOpenAlbum, onOpenArtist, onEditTags)
                    LibraryTab.ALBUMS -> AlbumsTab(viewModel, onOpenAlbum)
                    LibraryTab.ARTISTS -> ArtistsTab(viewModel, onOpenArtist)
                    LibraryTab.GENRES -> GenresTab(viewModel, onOpenGenre)
                    LibraryTab.FOLDERS -> FoldersTab(viewModel, onOpenFolder)
                    LibraryTab.PODCASTS -> PodcastsTab(viewModel, onOpenAlbum, onOpenArtist, onEditTags)
                    LibraryTab.RADIOS -> RadiosTab(viewModel)
                    LibraryTab.PLAYLISTS -> PlaylistsTab(
                        viewModel = viewModel,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenSmartPlaylist = onOpenSmartPlaylist,
                        onOpenRulePlaylist = onOpenRulePlaylist,
                        onCreateRulePlaylist = onCreateRulePlaylist,
                        onEditRulePlaylist = onEditRulePlaylist,
                    )
                }
            }
        }
    }
}

// --- Onglets ---------------------------------------------------------------------------

/** Position d'un onglet dans sa rangée, en pixels. */
private data class TabBounds(val left: Float = 0f, val width: Float = 0f)

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Rangée d'onglets Lumen : une piste de verre où glisse une pilule à la signature.
 *
 * La pilule suit le pager au pixel près (position lue au dessin, donc sans
 * recomposition) : pendant un balayage du doigt elle s'étire d'un onglet à l'autre, et
 * un appui sur un onglet lointain la fait voyager en ressort. La rangée défile d'elle-même
 * pour garder l'onglet actif au centre.
 */
@Composable
private fun LibraryTabRow(
    labels: List<String>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val bounds = remember(labels.size) {
        mutableStateListOf<TabBounds>().apply { repeat(labels.size) { add(TabBounds()) } }
    }
    var viewport by remember { mutableIntStateOf(0) }
    val selected = pagerState.currentPage

    LaunchedEffect(selected, viewport) {
        val b = bounds.getOrNull(selected) ?: return@LaunchedEffect
        if (viewport == 0 || b.width == 0f) return@LaunchedEffect
        val inset = with(density) { (Space.gutter + Space.xs).toPx() }
        val target = (inset + b.left + b.width / 2f - viewport / 2f).roundToInt().coerceIn(0, scroll.maxValue)
        if (reduce) scroll.scrollTo(target) else scroll.animateScrollTo(target, Motion.spatial())
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scroll)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
    ) {
        Row(
            Modifier
                .clip(SynxioShape.pill)
                .glass(SynxioShape.pill, strength = 0.5f)
                .padding(Space.xs)
                // Après le rembourrage : la zone de dessin coïncide avec celle des onglets.
                .drawBehind {
                    val page = pagerState.currentPage
                    val fraction = pagerState.currentPageOffsetFraction
                    val from = bounds.getOrNull(page) ?: return@drawBehind
                    val to = bounds.getOrNull(if (fraction >= 0f) page + 1 else page - 1) ?: from
                    val t = abs(fraction).coerceIn(0f, 1f)
                    val left = mix(from.left, to.left, t)
                    val width = mix(from.width, to.width, t)
                    if (width <= 0f) return@drawBehind
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(colors.brandStart, colors.brandEnd),
                            startX = left,
                            endX = left + width,
                        ),
                        topLeft = Offset(left, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val content by animateColorAsState(
                    if (isSelected) colors.onBrand else scheme.onSurfaceVariant,
                    Motion.standardOrSnap(reduce, Motion.SHORT),
                    label = "tabContent",
                )
                Box(
                    Modifier
                        .onPlaced { c ->
                            bounds[index] = TabBounds(c.positionInParent().x, c.size.width.toFloat())
                        }
                        .heightIn(min = Space.touchTarget)
                        .clip(SynxioShape.pill)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = Space.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Transition entre deux pages : la page qui part s'estompe, et son contenu glisse un peu
 * moins vite que le pager (parallaxe, dans le sens du geste). Animations réduites : le
 * fondu seul reste, plus aucun décalage.
 */
private fun Modifier.pageTransition(state: PagerState, page: Int, reduce: Boolean): Modifier =
    graphicsLayer {
        val offset = (state.currentPage - page) + state.currentPageOffsetFraction
        val distance = abs(offset).coerceIn(0f, 1f)
        alpha = 1f - 0.55f * distance
        if (!reduce) translationX = offset * size.width * PAGE_PARALLAX
    }

// --- Chargement ------------------------------------------------------------------------

private enum class SkeletonKind { Rows, Grid, Circles }

/**
 * Affiche des squelettes tant que le premier scan n'est pas terminé, sinon l'état vide.
 *
 * Sans cette distinction, chaque onglet annonçait « bibliothèque vide » pendant les
 * premières secondes après le lancement, ce qui faisait croire à un problème. Les
 * squelettes reprennent la forme du contenu attendu : l'écran ne « saute » pas quand
 * les vraies lignes arrivent.
 */
@Composable
private fun EmptyOrLoading(
    viewModel: AppViewModel,
    title: String,
    subtitle: String,
    icon: ImageVector,
    skeleton: SkeletonKind = SkeletonKind.Rows,
    columns: Int = 2,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    if (!library.hasScanned) {
        LibrarySkeleton(skeleton, columns)
    } else {
        EmptyState(title = title, subtitle = subtitle, icon = icon)
    }
}

@Composable
private fun LibrarySkeleton(kind: SkeletonKind, columns: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.md, vertical = Space.sm)
            .semantics { contentDescription = "Chargement de la bibliothèque" },
    ) {
        when (kind) {
            SkeletonKind.Rows -> repeat(9) { SkeletonSongRow() }
            SkeletonKind.Grid, SkeletonKind.Circles -> repeat(3) {
                Row(Modifier.fillMaxWidth()) {
                    repeat(columns) {
                        SkeletonTile(
                            shape = if (kind == SkeletonKind.Circles) CircleShape else SynxioShape.artworkMd,
                            centered = kind == SkeletonKind.Circles,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Tuile fantôme carrée (album) ou ronde (artiste), au gabarit des vraies cartes. */
@Composable
private fun SkeletonTile(shape: Shape, centered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(Space.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .shimmer()
        )
        Spacer(Modifier.height(Space.sm))
        Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(SynxioShape.pill).shimmer())
        Spacer(Modifier.height(Space.xs))
        Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(SynxioShape.pill).shimmer())
    }
}

/** Annonce le titre en cours aux lecteurs d'écran : la couleur seule ne suffit pas. */
private fun Modifier.nowPlayingSemantics(isCurrent: Boolean, isPlaying: Boolean): Modifier =
    if (!isCurrent) this
    else semantics { stateDescription = if (isPlaying) "En cours de lecture" else "En pause" }

// --- Ambiances ------------------------------------------------------------------------

private val Mood.icon: ImageVector
    get() = when (this) {
        Mood.SPORT -> Icons.Rounded.FitnessCenter
        Mood.RELAXATION -> Icons.Rounded.Spa
        Mood.FOCUS -> Icons.Rounded.Psychology
    }

private val Mood.description: String
    get() = when (this) {
        Mood.SPORT -> "Énergique, percussif et brillant"
        Mood.RELAXATION -> "Doux, peu de percussions"
        Mood.FOCUS -> "Régulier, sans à-coups"
    }

/** Part minimale de la bibliothèque à analyser avant de proposer les ambiances. */
private const val MOOD_ANALYSIS_THRESHOLD = 0.9f

@Composable
private fun RadiosTab(viewModel: AppViewModel) {
    val progress by viewModel.similarityProgress.collectAsStateWithLifecycle()
    val analysedCount by viewModel.similarityAnalysedCount.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val totalSongs = library.songs.size

    when {
        totalSongs == 0 -> EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun titre",
            subtitle = "Ajoute de la musique sur l'appareil pour générer des ambiances.",
            icon = Icons.Rounded.AutoAwesome,
            skeleton = SkeletonKind.Grid,
        )

        progress.running || analysedCount < totalSongs * MOOD_ANALYSIS_THRESHOLD -> AnalysisRequired(
            running = progress.running,
            fraction = progress.fraction,
            done = progress.done,
            total = progress.total,
            analysedCount = analysedCount,
            totalSongs = totalSongs,
            onAnalyse = { viewModel.analyseLibrary() },
        )

        else -> MoodGrid(
            analysedCount = analysedCount,
            totalSongs = totalSongs,
            onMood = { viewModel.playMoodRadio(it) },
            onAnalyse = { viewModel.analyseLibrary() },
        )
    }
}

/**
 * Pastille d'icône des états explicatifs : disque de verre cerclé de la signature, posé
 * sur un halo. Même grammaire que [EmptyState], pour que ces écrans se reconnaissent.
 */
@Composable
private fun StateBadge(icon: ImageVector) {
    val colors = SynxioTheme.colors
    Box(contentAlignment = Alignment.Center) {
        if (LocalGlowEffects.current) {
            Box(
                Modifier
                    .size(StateBadgeSize + 54.dp)
                    .background(Brush.radialGradient(listOf(colors.brandEnd.copy(alpha = 0.28f), Color.Transparent)))
            )
        }
        Box(
            Modifier
                .size(StateBadgeSize)
                .clip(CircleShape)
                .glass(CircleShape, strength = 1f, border = false)
                .border(1.5.dp, colors.brandBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Explique pourquoi les ambiances ne sont pas encore disponibles.
 *
 * Défilant : avec une grande taille de police, le texte et le bouton débordaient de
 * l'écran sans moyen de les atteindre.
 */
@Composable
private fun AnalysisRequired(
    running: Boolean,
    fraction: Float,
    done: Int,
    total: Int,
    analysedCount: Int,
    totalSongs: Int,
    onAnalyse: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val numbers = MaterialTheme.typography.labelLarge.merge(TabularNumbers)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .enterCascade(0, key = "analysis")
            .padding(horizontal = Space.xxl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateBadge(Icons.Rounded.AutoAwesome)
        Spacer(Modifier.height(Space.lg))
        Text(
            "Analyse requise",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Pour composer des ambiances, Synxio analyse l'empreinte sonore de tes titres " +
                "(basses, tempo, brillance). Tout se passe sur l'appareil.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xl))

        if (running) {
            BrandProgressBar(
                progress = fraction,
                height = 6.dp,
                modifier = Modifier.semantics { contentDescription = "Analyse en cours" },
            )
            Spacer(Modifier.height(Space.sm))
            Text("$done / $total titres analysés", style = numbers, color = scheme.onSurfaceVariant)
        } else {
            Text("$analysedCount / $totalSongs titres déjà analysés", style = numbers, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.lg))
            BrandButton(text = "Lancer l'analyse", onClick = onAnalyse, icon = Icons.Rounded.AutoAwesome)
        }
    }
}

@Composable
private fun MoodGrid(
    analysedCount: Int,
    totalSongs: Int,
    onMood: (Mood) -> Unit,
    onAnalyse: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.sm,
            bottom = Space.bottomInset,
        ),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Column(Modifier.padding(bottom = Space.xs)) {
                Text(
                    "AMBIANCES",
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "Choisis ton ambiance",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "Un mix jusqu'à 50 titres de ta bibliothèque, choisis selon leur son.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(Mood.entries, key = { _, it -> it.name }, contentType = { _, _ -> "mood" }) { index, mood ->
            MoodCard(
                mood = mood,
                onClick = { onMood(mood) },
                modifier = Modifier.enterCascade(index, "mood-${mood.name}"),
            )
        }

        // Des titres ajoutés depuis la dernière analyse ne seraient jamais proposés :
        // on laisse la possibilité de compléter sans bloquer l'accès aux ambiances.
        if (analysedCount < totalSongs) {
            item(key = "refresh", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${totalSongs - analysedCount} titres pas encore analysés",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAnalyse, shape = SynxioShape.pill) { Text("Compléter") }
                }
            }
        }
    }
}

/** Carte d'ambiance : verre, pastille à la signature qui rayonne, nom en Bricolage. */
@Composable
private fun MoodCard(mood: Mood, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Lancer l'ambiance ${mood.label}" },
        onClick = onClick,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(MoodBadgeSize)
                    .glowShadow(CircleShape, colors.brandEnd, elevation = 14.dp)
                    .clip(CircleShape)
                    .background(colors.brandBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mood.icon, contentDescription = null, tint = colors.onBrand)
            }
            Spacer(Modifier.height(Space.lg))
            Text(
                mood.label,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                mood.description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --- Onglets de liste ----------------------------------------------------------------

@Composable
private fun PodcastsTab(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    if (podcasts.isEmpty()) {
        EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun podcast",
            subtitle = "Tes podcasts et livres audio apparaîtront ici.",
            icon = Icons.Rounded.Podcasts,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.sm, end = Space.sm, top = Space.xs, bottom = Space.bottomInset),
    ) {
        itemsIndexed(podcasts, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
            val isCurrent = currentSongId == song.id
            SongRow(
                song = song,
                onClick = { viewModel.playSong(song, podcasts) },
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                isFavorite = song.id in favorites,
                onMenuClick = { menuSong = song },
                modifier = Modifier
                    .animateItem()
                    .enterCascade(index, song.id)
                    .nowPlayingSemantics(isCurrent, isPlaying),
            )
        }
    }

    SongMenuHost(
        viewModel = viewModel,
        song = menuSong,
        onDismiss = { menuSong = null },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onEditTags = onEditTags,
    )
}

@Composable
private fun SongsTab(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val songs by viewModel.sortedSongs.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // Sélection multiple : l'appui long bascule en mode sélection au lieu d'ouvrir
    // le menu, et les appuis suivants cochent/décochent.
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val selectionMode = selected.isNotEmpty()
    val selectedSongs = remember(selected, songs) { songs.filter { it.id in selected } }

    BackHandler(enabled = selectionMode) { selected = emptySet() }

    if (songs.isEmpty()) {
        EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun titre",
            subtitle = "Ta bibliothèque est vide pour le moment.",
            icon = Icons.Rounded.LibraryMusic,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.sm, end = Space.sm, top = Space.xs, bottom = Space.bottomInset),
    ) {
        if (selectionMode) {
            stickyHeader(key = "selection", contentType = "selection") {
                SelectionBar(
                    count = selected.size,
                    allSelected = selected.size == songs.size,
                    onClear = { selected = emptySet() },
                    onSelectAll = { selected = songs.map { it.id }.toSet() },
                    onPlay = { viewModel.play(selectedSongs); selected = emptySet() },
                    onPlayNext = { viewModel.playNext(selectedSongs); selected = emptySet() },
                    onAddToQueue = { viewModel.addToQueue(selectedSongs); selected = emptySet() },
                    onAddToPlaylist = { showPlaylistPicker = true },
                    onFavorite = {
                        selectedSongs.forEach(viewModel::toggleFavorite)
                        selected = emptySet()
                    },
                    onDelete = {
                        viewModel.deleteSongs(selectedSongs)
                        selected = emptySet()
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "toolbar", contentType = "toolbar") {
            ListToolbar(
                count = songs.size,
                sortLabel = settings.songSort.label,
                descending = settings.songSortDescending,
                sortOptions = SongSort.entries.map { it.label },
                onSortSelected = { index ->
                    viewModel.setSongSort(SongSort.entries[index], settings.songSortDescending)
                },
                onToggleDirection = {
                    viewModel.setSongSort(settings.songSort, !settings.songSortDescending)
                },
                onPlayAll = { viewModel.play(songs) },
                onShuffle = { viewModel.shufflePlay(songs) },
            )
        }

        itemsIndexed(songs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
            val isSelected = song.id in selected
            val isCurrent = currentSongId == song.id
            val selectionTint by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                Motion.standardOrSnap(LocalReduceMotion.current, Motion.SHORT),
                label = "selectionTint",
            )
            SongRow(
                song = song,
                onClick = {
                    if (selectionMode) {
                        selected = if (isSelected) selected - song.id else selected + song.id
                    } else {
                        viewModel.playSong(song, songs)
                    }
                },
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                isFavorite = song.id in favorites,
                modifier = Modifier
                    .animateItem()
                    .enterCascade(index, song.id)
                    .background(selectionTint, SynxioShape.artworkMd)
                    .then(
                        if (selectionMode) {
                            Modifier.semantics { stateDescription = if (isSelected) "Sélectionné" else "Non sélectionné" }
                        } else {
                            Modifier.nowPlayingSemantics(isCurrent, isPlaying)
                        }
                    ),
                onLongClick = { selected = selected + song.id },
                // En sélection, le menu laisse place à la case : le geste n'aurait rien fait.
                onMenuClick = if (selectionMode) null else ({ menuSong = song }),
                trailing = if (selectionMode) ({ SelectionCheck(isSelected) }) else null,
            )
        }
    }

    SongMenuHost(
        viewModel = viewModel,
        song = menuSong,
        onDismiss = { menuSong = null },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onEditTags = onEditTags,
    )

    if (showPlaylistPicker) {
        AddToPlaylistSheet(
            playlists = playlists,
            onSelect = { viewModel.addToPlaylist(it.id, selectedSongs) },
            onCreate = { viewModel.createPlaylist(it, selectedSongs) },
            onDismiss = {
                showPlaylistPicker = false
                selected = emptySet()
            },
        )
    }
}

/**
 * Case de sélection d'une ligne : un anneau discret au repos, un disque à la signature
 * qui éclot (petit rebond) quand le titre est coché. Décorative : l'état est annoncé par
 * la ligne elle-même.
 */
@Composable
private fun SelectionCheck(selected: Boolean) {
    val colors = SynxioTheme.colors
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) snap() else Motion.spatialBouncy(),
        label = "selectionCheck",
    )
    Box(
        Modifier
            .padding(horizontal = Space.md)
            .size(24.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 1f - scale.coerceIn(0f, 1f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(colors.brandBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Barre contextuelle affichée pendant une sélection multiple : verre dense teinté de la
 * signature, compteur qui roule quand il change.
 */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = scheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
            title = {
                Text("Supprimer $count titre${if (count > 1) "s" else ""} ?", style = MaterialTheme.typography.headlineSmall)
            },
            text = { Text("Les fichiers seront définitivement supprimés de l'appareil.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, shape = SynxioShape.pill) {
                    Text("Supprimer", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, shape = SynxioShape.pill) { Text("Annuler") }
            },
        )
    }

    // Sous-couche dense sous le verre : la liste défile dessous, le texte des lignes ne
    // doit pas transparaître à travers les actions.
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .clip(SynxioShape.card)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .glass(SynxioShape.card, strength = 1f, tint = SynxioTheme.colors.brandEnd)
            .padding(horizontal = Space.xs, vertical = Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Annuler la sélection")
            }
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    if (reduce) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        val up = targetState > initialState
                        (slideInVertically(Motion.spatial()) { if (up) it else -it } + fadeIn(Motion.enter(Motion.SHORT)))
                            .togetherWith(slideOutVertically(Motion.spatial()) { if (up) -it else it } + fadeOut(Motion.exit()))
                    }
                },
                modifier = Modifier.weight(1f),
                label = "selectionCount",
            ) { n ->
                Text(
                    text = "$n sélectionné${if (n > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                )
            }
            TextButton(onClick = if (allSelected) onClear else onSelectAll, shape = SynxioShape.pill) {
                Text(if (allSelected) "Aucun" else "Tout")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
            IconButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Lire la sélection")
            }
            IconButton(onClick = onPlayNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Lire juste après")
            }
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Ajouter à la file")
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Ajouter à une playlist")
            }
            IconButton(onClick = onFavorite) {
                Icon(Icons.Rounded.Favorite, contentDescription = "Basculer les favoris", tint = SynxioTheme.colors.favorite)
            }
            IconButton(onClick = {
                // Android 11+ confirme lui-même (corbeille système).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) onDelete() else confirmDelete = true
            }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer de l'appareil")
            }
        }
    }
}

@Composable
private fun AlbumsTab(viewModel: AppViewModel, onOpenAlbum: (Long) -> Unit) {
    val albums by viewModel.sortedAlbums.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val columns = settings.albumGridColumns.coerceIn(2, 4)

    if (albums.isEmpty()) {
        EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun album",
            subtitle = "Tes albums apparaîtront ici après le scan.",
            icon = Icons.Rounded.Album,
            skeleton = SkeletonKind.Grid,
            columns = columns,
        )
        return
    }

    // AlbumCard porte déjà sa propre marge interne : gouttière de grille + marge de carte
    // retombent sur la marge latérale d'écran.
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.xs, bottom = Space.bottomInset),
    ) {
        item(key = "toolbar", span = { GridItemSpan(maxLineSpan) }, contentType = "toolbar") {
            ListToolbar(
                count = albums.size,
                sortLabel = settings.albumSort.label,
                descending = settings.albumSortDescending,
                sortOptions = AlbumSort.entries.map { it.label },
                onSortSelected = { index ->
                    viewModel.setAlbumSort(AlbumSort.entries[index], settings.albumSortDescending)
                },
                onToggleDirection = {
                    viewModel.setAlbumSort(settings.albumSort, !settings.albumSortDescending)
                },
                onPlayAll = { viewModel.play(albums.flatMap { it.songs }) },
                onShuffle = { viewModel.shufflePlay(albums.flatMap { it.songs }) },
                inset = Space.sm,
            )
        }

        itemsIndexed(albums, key = { _, it -> it.id }, contentType = { _, _ -> "album" }) { index, album ->
            AlbumCard(
                album = album,
                onClick = { onOpenAlbum(album.id) },
                modifier = Modifier
                    .animateItem()
                    .enterCascade(index, album.id),
            )
        }
    }
}

@Composable
private fun ArtistsTab(viewModel: AppViewModel, onOpenArtist: (String) -> Unit) {
    val artists by viewModel.sortedArtists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    if (artists.isEmpty()) {
        EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun artiste",
            subtitle = "Tes artistes apparaîtront ici après le scan.",
            icon = Icons.Rounded.Person,
            skeleton = SkeletonKind.Circles,
            columns = 3,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.xs, bottom = Space.bottomInset),
    ) {
        item(key = "toolbar", span = { GridItemSpan(maxLineSpan) }, contentType = "toolbar") {
            ListToolbar(
                count = artists.size,
                sortLabel = settings.artistSort.label,
                descending = settings.artistSortDescending,
                sortOptions = ArtistSort.entries.map { it.label },
                onSortSelected = { index ->
                    viewModel.setArtistSort(ArtistSort.entries[index], settings.artistSortDescending)
                },
                onToggleDirection = {
                    viewModel.setArtistSort(settings.artistSort, !settings.artistSortDescending)
                },
                onPlayAll = { viewModel.play(artists.flatMap { it.songs }) },
                onShuffle = { viewModel.shufflePlay(artists.flatMap { it.songs }) },
                inset = Space.sm,
            )
        }

        itemsIndexed(artists, key = { _, it -> it.name }, contentType = { _, _ -> "artist" }) { index, artist ->
            ArtistCard(
                artist = artist,
                onClick = { onOpenArtist(artist.name) },
                modifier = Modifier
                    .animateItem()
                    .enterCascade(index, artist.name),
            )
        }
    }
}

@Composable
private fun GenresTab(viewModel: AppViewModel, onOpenGenre: (String) -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    if (library.genres.isEmpty()) {
        EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun genre",
            subtitle = "Tes fichiers n'ont pas de tag de genre.",
            icon = Icons.Rounded.Category,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.sm, end = Space.sm, top = Space.sm, bottom = Space.bottomInset),
    ) {
        itemsIndexed(library.genres, key = { _, it -> it.name }) { index, genre ->
            GenreRow(
                genre = genre,
                onClick = { onOpenGenre(genre.name) },
                modifier = Modifier
                    .animateItem()
                    .enterCascade(index, "genre-${genre.name}"),
            )
        }
    }
}

@Composable
private fun FoldersTab(viewModel: AppViewModel, onOpenFolder: (String) -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    if (library.folders.isEmpty()) {
        EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun dossier",
            subtitle = "Aucun dossier ne contient de musique pour le moment.",
            icon = Icons.Rounded.Folder,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.sm, end = Space.sm, top = Space.sm, bottom = Space.bottomInset),
    ) {
        itemsIndexed(library.folders, key = { _, it -> it.path }) { index, folder ->
            FolderRow(
                folder = folder,
                onClick = { onOpenFolder(folder.path) },
                modifier = Modifier
                    .animateItem()
                    .enterCascade(index, "folder-${folder.path}"),
            )
        }
    }
}

/**
 * Barre « lecture / aléatoire · n éléments · tri » en tête de chaque onglet.
 *
 * Lecture porte la signature (l'action principale de l'onglet), Aléatoire reste en verre.
 * Le tri tient dans une pilule de verre ; la flèche de sens pivote d'un demi-tour quand
 * l'ordre s'inverse, plutôt que de changer de caractère.
 *
 * [inset] : marge latérale supplémentaire quand la grille porte déjà la sienne.
 */
@Composable
private fun ListToolbar(
    count: Int,
    sortLabel: String,
    descending: Boolean,
    sortOptions: List<String>,
    onSortSelected: (Int) -> Unit,
    onToggleDirection: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    inset: androidx.compose.ui.unit.Dp = Space.md,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val rotation by animateFloatAsState(
        if (descending) 0f else 180f,
        if (reduce) snap() else Motion.spatialBouncy(),
        label = "sortArrow",
    )
    val direction = if (descending) "décroissant" else "croissant"

    Column(Modifier.padding(horizontal = inset, vertical = Space.sm)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            BrandButton(
                text = "Lecture",
                onClick = onPlayAll,
                icon = Icons.Rounded.PlayArrow,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "Aléatoire",
                onClick = onShuffle,
                icon = Icons.Rounded.Shuffle,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Space.sm))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$count élément${if (count > 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                Row(
                    Modifier
                        .pressBounce(0.95f)
                        .heightIn(min = Space.touchTarget)
                        .clip(SynxioShape.pill)
                        .clickable(onClickLabel = "Changer le tri", role = Role.Button) { expanded = true }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Trier : $sortLabel, ordre $direction"
                        }
                        .padding(start = Space.md, end = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .clip(SynxioShape.pill)
                            .glass(SynxioShape.pill, strength = 0.5f)
                            .padding(horizontal = Space.md, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SortByAlpha, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(sortLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Spacer(Modifier.width(Space.xs))
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = SynxioShape.cardSm,
                    containerColor = scheme.surfaceContainerHigh,
                ) {
                    sortOptions.forEachIndexed { index, label ->
                        val current = label == sortLabel
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = if (current) scheme.primary else scheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            trailingIcon = if (current) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary) }
                            } else null,
                            onClick = { onSortSelected(index); expanded = false },
                            modifier = Modifier.semantics {
                                stateDescription = if (current) "sélectionné" else "non sélectionné"
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (descending) "Ordre croissant" else "Ordre décroissant") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = if (descending) 180f else 0f },
                            )
                        },
                        onClick = { onToggleDirection(); expanded = false },
                    )
                }
            }
        }
    }
}
