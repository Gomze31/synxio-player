package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

/** Délai sans frappe avant de relancer le filtre. */
private const val FILTER_DEBOUNCE_MS = 150L

/** Ce que montre la liste : une transition en fondu à chaque changement. */
private enum class AdvancedPane { LOADING, EMPTY_LIBRARY, NONE, RESULTS }

/**
 * Recherche avancée : un filtre plein texte sur toute la bibliothèque, en liste plate.
 * Contrairement à l'onglet Recherche, elle ne regroupe pas par artiste/album : on vient
 * ici pour constituer une file de lecture à partir de tout ce qui correspond, d'où les
 * deux actions « Tout lire » et « Aléatoire » au-dessus des résultats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var menuSong by remember { mutableStateOf<Song?>(null) }

    val library by viewModel.library.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()

    // Le filtre tourne hors du thread principal et ne part qu'une fois la frappe posée :
    // produceState annule le calcul précédent à chaque nouvelle requête.
    val filteredSongs by produceState(initialValue = library.songs, query, library) {
        if (query.isNotBlank()) delay(FILTER_DEBOUNCE_MS)
        value = viewModel.filterSongs(query, library)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(Unit) { if (query.isBlank()) focusRequester.requestFocus() }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    val pane = when {
        library.isLoading && library.songs.isEmpty() -> AdvancedPane.LOADING
        library.songs.isEmpty() -> AdvancedPane.EMPTY_LIBRARY
        filteredSongs.isEmpty() -> AdvancedPane.NONE
        else -> AdvancedPane.RESULTS
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Recherche avancée", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LumenSearchField(
                query = query,
                onQueryChange = { query = it },
                onClear = {
                    query = ""
                    focusRequester.requestFocus()
                },
                onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
                focusRequester = focusRequester,
                placeholder = "Titre, artiste, album…",
            )

            AnimatedContent(
                targetState = pane,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (reduceMotion) fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(snap())
                    else fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit())
                },
                label = "advancedPane",
            ) { target ->
                when (target) {
                    AdvancedPane.LOADING -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Space.sm, vertical = Space.md)
                            .semantics { contentDescription = "Chargement de la bibliothèque" },
                    ) { repeat(8) { SkeletonSongRow() } }

                    AdvancedPane.EMPTY_LIBRARY -> EmptyState(
                        title = "Bibliothèque vide",
                        subtitle = "Aucun morceau à filtrer pour l'instant. Ajoute de la musique sur ton appareil puis relance un scan.",
                        actionLabel = "Relancer le scan",
                        onAction = { viewModel.rescan() },
                    )

                    AdvancedPane.NONE -> EmptyState(
                        title = "Aucun résultat",
                        subtitle = "Rien ne correspond à « $query ». Vérifie l'orthographe ou essaie un mot plus court.",
                        icon = Icons.Rounded.SearchOff,
                    )

                    AdvancedPane.RESULTS -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Space.sm,
                            end = Space.sm,
                            top = Space.xs,
                            bottom = Space.bottomInset + Space.xl,
                        ),
                    ) {
                        item(key = "summary", contentType = "summary") {
                            ResultSummary(
                                count = filteredSongs.size,
                                isWholeLibrary = query.isBlank(),
                                onPlayAll = { onPlaySong(filteredSongs, 0) },
                                onShuffle = { viewModel.shufflePlay(filteredSongs) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        itemsIndexed(
                            filteredSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "song" },
                        ) { index, song ->
                            val isCurrent = currentSongId == song.id
                            SongRow(
                                song = song,
                                onClick = { onPlaySong(filteredSongs, index) },
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isFavorite = song.id in favorites,
                                onLongClick = { menuSong = song },
                                onMenuClick = { menuSong = song },
                                modifier = Modifier
                                    .then(
                                        if (reduceMotion) Modifier.animateItem(null, null, null)
                                        else Modifier.animateItem(
                                            fadeInSpec = Motion.enter(Motion.SHORT),
                                            placementSpec = Motion.standard(),
                                            fadeOutSpec = Motion.exit(),
                                        )
                                    )
                                    // Seuls les premiers titres entrent en cascade : au-delà,
                                    // chaque ligne révélée au défilement rejouerait l'effet.
                                    .then(if (index < Motion.MAX_STAGGERED) Modifier.enterCascade(index, song.id) else Modifier),
                            )
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

/** Nombre de correspondances en surtitre, puis les deux façons de tout écouter. */
@Composable
private fun ResultSummary(
    count: Int,
    isWholeLibrary: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
        Text(
            text = ((if (isWholeLibrary) "Toute la bibliothèque · " else "Correspondances · ") + count.pluralSongs()).uppercase(),
            style = EyebrowTextStyle.merge(fr.synxio.player.ui.theme.TabularNumbers),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md, bottom = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandButton(text = "Tout lire", onClick = onPlayAll, icon = Icons.Rounded.PlayArrow)
            GlassButton(text = "Aléatoire", onClick = onShuffle, icon = Icons.Rounded.Shuffle)
        }
    }
}
