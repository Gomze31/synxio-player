package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.ArtistCard
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GenreRow
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.ScreenHeader
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

/**
 * Titres montrés avant « Tout voir » : assez pour trouver le bon morceau d'un coup d'œil,
 * pas assez pour repousser artistes et albums hors de l'écran.
 */
private const val SONG_PREVIEW_COUNT = 6

/** Recherches récentes gardées : au-delà, la rangée devient une liste qu'on ne lit plus. */
private const val MAX_RECENT_QUERIES = 8

/** Genres proposés quand le champ est vide. */
private const val GENRE_SUGGESTIONS = 10

/**
 * Au-delà de ce délai sans nouveaux résultats, la requête est considérée comme traitée
 * (le ViewModel attend 180 ms avant de chercher, puis classe hors du thread principal).
 */
private const val SETTLE_MS = 450L

private val ArtistCardWidth = 116.dp
private val AlbumCardWidth = 156.dp

/** Ce que montre l'écran sous le champ : une transition à chaque changement. */
private enum class SearchPane { LOADING, IDLE, SEARCHING, RESULTS, NONE }

/** Filtre des résultats, côté affichage seulement : la recherche reste la même. */
private enum class SearchFilter(val label: String) {
    ALL("Tout"), SONGS("Titres"), ARTISTS("Artistes"), ALBUMS("Albums"), GENRES("Genres")
}

@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onNavigateToDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    // Identifiant + lecture/pause seulement : playerState bouge 4×/s avec la position.
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    // Chaque nouvelle requête repart sur l'aperçu court.
    var songsExpanded by rememberSaveable(query) { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(SearchFilter.ALL.name) }
    // Recherches récentes de la session : gardées le temps que l'onglet vit (y compris
    // en changeant d'onglet), sans rien écrire sur le disque.
    val recentQueries = rememberSaveable(saver = recentSaver) { mutableStateOf(emptyList<String>()) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current

    // Requête pour laquelle les résultats affichés font foi. Sans elle, la première
    // lettre tapée affichait « Aucun résultat » le temps que la recherche parte.
    var settledQuery by remember { mutableStateOf(query) }
    LaunchedEffect(results) { settledQuery = query }
    LaunchedEffect(query) {
        delay(SETTLE_MS)
        settledQuery = query
    }

    // On arrive sur l'onglet pour taper : le champ prend le focus. Pas quand une requête
    // est déjà là, sinon le clavier recouvrirait les résultats qu'on revient consulter.
    LaunchedEffect(Unit) {
        if (query.isBlank()) focusRequester.requestFocus()
    }
    // Faire défiler les résultats range le clavier, comme partout sur Android.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    fun rememberQuery(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        recentQueries.value = (listOf(clean) + recentQueries.value.filterNot { it.equals(clean, ignoreCase = true) })
            .take(MAX_RECENT_QUERIES)
    }

    // Une catégorie choisie mais vide pour cette requête retombe sur « Tout ».
    val available = remember(results) {
        buildList {
            add(SearchFilter.ALL)
            if (results.songs.isNotEmpty()) add(SearchFilter.SONGS)
            if (results.artists.isNotEmpty()) add(SearchFilter.ARTISTS)
            if (results.albums.isNotEmpty()) add(SearchFilter.ALBUMS)
            if (results.genres.isNotEmpty()) add(SearchFilter.GENRES)
        }
    }
    val filter = SearchFilter.valueOf(filterName).takeIf { it in available } ?: SearchFilter.ALL
    val show = { f: SearchFilter -> filter == SearchFilter.ALL || filter == f }

    val visibleSongs = remember(results.songs, songsExpanded, filter) {
        if (songsExpanded || filter == SearchFilter.SONGS) results.songs else results.songs.take(SONG_PREVIEW_COUNT)
    }

    val pane = when {
        library.isLoading && library.songs.isEmpty() -> SearchPane.LOADING
        query.isBlank() -> SearchPane.IDLE
        !results.isEmpty -> SearchPane.RESULTS
        query != settledQuery -> SearchPane.SEARCHING
        else -> SearchPane.NONE
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Rechercher",
            eyebrow = "Ta bibliothèque",
            actions = {
                // Discret mais toujours à portée : télécharger n'est pas l'usage principal
                // de l'onglet, il ne doit pas repousser les résultats.
                IconButton(onClick = onNavigateToDownload, modifier = Modifier.size(Space.touchTarget)) {
                    Icon(Icons.Rounded.Download, contentDescription = "Télécharger depuis YouTube")
                }
            },
        )

        LumenSearchField(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onClear = {
                viewModel.onQueryChange("")
                focusRequester.requestFocus()
            },
            onSearch = {
                // La recherche est déjà en direct : « Rechercher » sert à libérer l'écran.
                rememberQuery(query)
                keyboard?.hide()
                focusManager.clearFocus()
            },
            focusRequester = focusRequester,
        )

        AnimatedContent(
            targetState = pane,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(snap())
                } else {
                    (fadeIn(Motion.enter(Motion.MEDIUM)) + slideInVertically(Motion.enter(Motion.MEDIUM)) { it / 16 }) togetherWith
                        fadeOut(Motion.exit())
                }
            },
            label = "searchPane",
        ) { target ->
            when (target) {
                SearchPane.LOADING, SearchPane.SEARCHING -> SearchSkeleton()

                SearchPane.IDLE -> IdlePane(
                    recent = recentQueries.value,
                    genres = remember(library.genres) {
                        library.genres.sortedByDescending { it.songCount }.take(GENRE_SUGGESTIONS).map { it.name }
                    },
                    onPickRecent = { viewModel.onQueryChange(it) },
                    onClearRecent = { recentQueries.value = emptyList() },
                    onOpenGenre = onOpenGenre,
                )

                SearchPane.NONE -> EmptyState(
                    title = "Aucun résultat",
                    subtitle = "Rien ne correspond à « $query » dans ta bibliothèque. Tu peux le chercher sur YouTube et le télécharger.",
                    icon = Icons.Rounded.SearchOff,
                    actionLabel = "Télécharger depuis YouTube",
                    onAction = onNavigateToDownload,
                )

                SearchPane.RESULTS -> Column(Modifier.fillMaxSize()) {
                    if (available.size > 2) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.xs),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            items(available, key = { it.name }) { f ->
                                LumenChip(
                                    label = f.label,
                                    selected = f == filter,
                                    onClick = { filterName = f.name },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Space.bottomInset + Space.xl),
                    ) {
                        if (show(SearchFilter.SONGS) && results.songs.isNotEmpty()) {
                            item(key = "h-songs", contentType = "header") {
                                val canExpand = results.songs.size > SONG_PREVIEW_COUNT && filter == SearchFilter.ALL
                                SectionHeader(
                                    title = "Titres",
                                    eyebrow = countLabel(results.songs.size, "titre", "titres"),
                                    actionLabel = when {
                                        !canExpand -> null
                                        songsExpanded -> "Réduire"
                                        else -> "Tout voir"
                                    },
                                    onAction = if (canExpand) ({ songsExpanded = !songsExpanded }) else null,
                                    modifier = itemMotion(reduceMotion),
                                )
                            }
                            items(visibleSongs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
                                val isCurrent = currentSongId == song.id
                                SongRow(
                                    song = song,
                                    onClick = {
                                        rememberQuery(query)
                                        viewModel.playSong(song, results.songs)
                                    },
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && isPlaying,
                                    isFavorite = song.id in favorites,
                                    modifier = itemMotion(reduceMotion).padding(horizontal = Space.sm),
                                    onLongClick = { menuSong = song },
                                    onMenuClick = { menuSong = song },
                                )
                            }
                        }

                        if (show(SearchFilter.ARTISTS) && results.artists.isNotEmpty()) {
                            item(key = "artists", contentType = "artistRow") {
                                Column(itemMotion(reduceMotion)) {
                                    SectionHeader(
                                        title = "Artistes",
                                        eyebrow = countLabel(results.artists.size, "artiste", "artistes"),
                                    )
                                    LazyRow(
                                        // ArtistCard porte Space.sm de marge interne : gutter = md + sm.
                                        contentPadding = PaddingValues(horizontal = Space.md),
                                    ) {
                                        items(results.artists, key = { it.name }) { artist ->
                                            ArtistCard(
                                                artist = artist,
                                                onClick = {
                                                    rememberQuery(query)
                                                    onOpenArtist(artist.name)
                                                },
                                                modifier = Modifier.animateItem().width(ArtistCardWidth),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (show(SearchFilter.ALBUMS) && results.albums.isNotEmpty()) {
                            item(key = "albums", contentType = "albumRow") {
                                Column(itemMotion(reduceMotion)) {
                                    SectionHeader(
                                        title = "Albums",
                                        eyebrow = countLabel(results.albums.size, "album", "albums"),
                                    )
                                    LazyRow(contentPadding = PaddingValues(horizontal = Space.md)) {
                                        items(results.albums, key = { it.id }) { album ->
                                            AlbumCard(
                                                album = album,
                                                onClick = {
                                                    rememberQuery(query)
                                                    onOpenAlbum(album.id)
                                                },
                                                modifier = Modifier.animateItem().width(AlbumCardWidth),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (show(SearchFilter.GENRES) && results.genres.isNotEmpty()) {
                            item(key = "h-genres", contentType = "header") {
                                SectionHeader(
                                    title = "Genres",
                                    eyebrow = countLabel(results.genres.size, "genre", "genres"),
                                    modifier = itemMotion(reduceMotion),
                                )
                            }
                            items(results.genres, key = { "genre-${it.name}" }, contentType = { "genre" }) { genre ->
                                GenreRow(
                                    genre = genre,
                                    onClick = {
                                        rememberQuery(query)
                                        onOpenGenre(genre.name)
                                    },
                                    modifier = itemMotion(reduceMotion).padding(horizontal = Space.sm),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SongMenuHost(
        viewModel = viewModel,
        song = menuSong,
        onDismiss = { menuSong = null },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    )
}

/** Sauvegarde des recherches récentes à travers les changements de configuration. */
private val recentSaver = listSaver<MutableState<List<String>>, String>(
    save = { it.value },
    restore = { mutableStateOf(it) },
)

private fun countLabel(n: Int, one: String, many: String) = if (n > 1) "$n $many" else "$n $one"

/**
 * Champ de recherche en verre. Au focus, il s'élargit vers les bords, se teinte de la
 * signature, se cercle d'un liseré lumineux et l'icône pivote d'un quart de tour léger,
 * comme une loupe qu'on prend en main. Animations réduites : tout bascule sans glisser.
 *
 * Partagé avec la recherche avancée (même module d'écrans), d'où la visibilité interne.
 */
@Composable
internal fun LumenSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    placeholder: String = "Titre, artiste, album, genre…",
) {
    val reduce = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = Motion.standardOrSnap(reduce, Motion.MEDIUM),
        label = "searchFocus",
    )
    val inset by animateDpAsState(
        targetValue = if (focused) Space.md else Space.gutter,
        animationSpec = Motion.spatialOrSnap(reduce),
        label = "searchInset",
    )
    val iconTurn by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduce) snap() else Motion.spatialBouncy(),
        label = "searchIcon",
    )
    val brandH = colors.brandBrushHorizontal
    val brand = colors.brandBrush

    Row(
        modifier
            .padding(horizontal = inset, vertical = Space.sm)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .glowShadow(SynxioShape.pill, colors.brandEnd.copy(alpha = focus), elevation = (18f * focus).dp)
            .clip(SynxioShape.pill)
            .glass(SynxioShape.pill, strength = 0.6f + 0.4f * focus)
            .drawBehind {
                // Lueur de la signature dans le verre, puis liseré lumineux : tous deux
                // apparaissent avec le focus.
                if (focus > 0f) {
                    drawRect(brandH, alpha = 0.10f * focus)
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        brush = brand,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                        style = Stroke(stroke),
                        alpha = focus,
                    )
                }
            }
            .padding(start = Space.lg, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = lerp(scheme.onSurfaceVariant, scheme.primary, focus),
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = 1f + 0.12f * iconTurn
                    scaleX = s
                    scaleY = s
                    rotationZ = -14f * iconTurn
                },
        )
        Spacer(Modifier.width(Space.md))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = brand,
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Rechercher dans la bibliothèque" },
            )
        }
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = if (reduce) fadeIn(snap()) else scaleIn(Motion.spatialBouncy()) + fadeIn(Motion.enter(Motion.SHORT)),
            exit = if (reduce) fadeOut(snap()) else scaleOut(Motion.exit()) + fadeOut(Motion.exit()),
        ) {
            IconButton(onClick = onClear, modifier = Modifier.size(Space.touchTarget)) {
                Icon(Icons.Rounded.Close, contentDescription = "Effacer la recherche")
            }
        }
    }
}

/**
 * Champ vide : recherches récentes, genres à parcourir, puis l'explication de ce que la
 * recherche sait faire. Rien d'autre : l'écran attend une frappe.
 */
@Composable
private fun IdlePane(
    recent: List<String>,
    genres: List<String>,
    onPickRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (recent.isNotEmpty()) {
            SectionHeader(
                title = "Recherches récentes",
                eyebrow = "Reprendre",
                actionLabel = "Effacer",
                onAction = onClearRecent,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(recent, key = { it }) { q ->
                    LumenChip(
                        label = q,
                        selected = false,
                        onClick = { onPickRecent(q) },
                        icon = Icons.Rounded.History,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        if (genres.isNotEmpty()) {
            SectionHeader(title = "Parcourir", eyebrow = "Genres")
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(genres, key = { it }) { name ->
                    LumenChip(label = name, selected = false, onClick = { onOpenGenre(name) })
                }
            }
        }
        EmptyState(
            title = "Cherche dans ta bibliothèque",
            subtitle = "Titres, artistes, albums ou genres. La recherche ignore les accents et tolère les fautes de frappe.",
            icon = Icons.Rounded.Search,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Lignes fantômes pendant le premier scan ou le temps que la recherche réponde. */
@Composable
private fun SearchSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.sm, vertical = Space.md)
            .semantics { contentDescription = "Recherche en cours" },
    ) {
        repeat(7) { SkeletonSongRow() }
    }
}

/**
 * Apparition/réordonnancement des résultats pendant la frappe. Animations réduites :
 * les lignes changent sans glisser ni fondre.
 */
private fun LazyItemScope.itemMotion(reduceMotion: Boolean): Modifier =
    if (reduceMotion) {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem(
            fadeInSpec = Motion.enter(Motion.SHORT),
            placementSpec = Motion.standard(),
            fadeOutSpec = Motion.exit(),
        )
    }
