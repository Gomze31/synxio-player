package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

/** Délai sans frappe avant de relancer le filtre. */
private const val FILTER_DEBOUNCE_MS = 150L

/**
 * Recherche avancée : un filtre plein texte sur toute la bibliothèque, en liste plate.
 * Contrairement à l'onglet Recherche, elle ne regroupe pas par artiste/album : on vient
 * ici pour constituer une file de lecture à partir de tout ce qui correspond.
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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { if (query.isBlank()) focusRequester.requestFocus() }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Recherche avancée") },
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                // fillMaxSize faisait occuper tout l'écran au champ et poussait les
                // résultats hors de la vue.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm)
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = SynxioShape.pill,
                placeholder = { Text("Titre, artiste, album…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Effacer la recherche")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
            )

            when {
                library.isLoading && library.songs.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                library.songs.isEmpty() -> EmptyState(
                    title = "Bibliothèque vide",
                    subtitle = "Aucun morceau à filtrer pour l'instant. Ajoute de la musique sur ton appareil puis relance un scan.",
                    actionLabel = "Relancer le scan",
                    onAction = { viewModel.rescan() },
                )

                filteredSongs.isEmpty() -> EmptyState(
                    title = "Aucun résultat",
                    subtitle = "Rien ne correspond à « $query ». Vérifie l'orthographe ou essaie un mot plus court.",
                    icon = Icons.Rounded.SearchOff,
                )

                else -> {
                    Text(
                        text = if (query.isBlank()) {
                            "Toute la bibliothèque · ${filteredSongs.size.pluralSongs()}"
                        } else {
                            filteredSongs.size.pluralSongs()
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = Space.sm,
                            end = Space.sm,
                            top = Space.xs,
                            bottom = Space.xl,
                        ),
                    ) {
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
                                modifier = Modifier.semantics {
                                    if (isCurrent) stateDescription = "En cours de lecture"
                                },
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
