package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.ArtistCard
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GenreRow
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel

/**
 * Titres montrés avant « Tout voir » : assez pour trouver le bon morceau d'un coup d'œil,
 * pas assez pour repousser artistes et albums hors de l'écran.
 */
private const val SONG_PREVIEW_COUNT = 6

private val ArtistCardWidth = 110.dp
private val AlbumCardWidth = 150.dp

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
    // Identifiant + lecture/pause seulement : playerState bouge 4×/s avec la position.
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    // Chaque nouvelle requête repart sur l'aperçu court.
    var songsExpanded by rememberSaveable(query) { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current

    // On arrive sur l'onglet pour taper : le champ prend le focus. Pas quand une requête
    // est déjà là, sinon le clavier recouvrirait les résultats qu'on revient consulter.
    LaunchedEffect(Unit) {
        if (query.isBlank()) focusRequester.requestFocus()
    }
    // Faire défiler les résultats range le clavier, comme partout sur Android.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    val visibleSongs = remember(results.songs, songsExpanded) {
        if (songsExpanded) results.songs else results.songs.take(SONG_PREVIEW_COUNT)
    }

    Column(modifier.fillMaxSize()) {
        Text(
            text = "Rechercher",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.xl),
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md)
                .focusRequester(focusRequester),
            placeholder = { Text("Titre, artiste, album, genre…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onQueryChange("")
                            focusRequester.requestFocus()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Effacer la recherche")
                        }
                    }
                    // Discret mais toujours à portée : télécharger n'est pas l'usage
                    // principal de l'onglet, il ne doit pas repousser les résultats.
                    IconButton(onClick = onNavigateToDownload) {
                        Icon(Icons.Rounded.Download, contentDescription = "Télécharger depuis YouTube")
                    }
                }
            },
            singleLine = true,
            shape = SynxioShape.pill,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                // La recherche est déjà en direct : « Rechercher » sert à libérer l'écran.
                keyboard?.hide()
                focusManager.clearFocus()
            }),
        )

        when {
            query.isBlank() -> EmptyState(
                title = "Cherche dans ta bibliothèque",
                subtitle = "Titres, artistes, albums ou genres. La recherche ignore les accents et tolère les fautes de frappe.",
                icon = Icons.Rounded.Search,
            )

            results.isEmpty -> EmptyState(
                title = "Aucun résultat",
                subtitle = "Rien ne correspond à « $query » dans ta bibliothèque. Tu peux le chercher sur YouTube et le télécharger.",
                icon = Icons.Rounded.SearchOff,
                actionLabel = "Télécharger depuis YouTube",
                onAction = onNavigateToDownload,
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.xl),
            ) {
                if (results.songs.isNotEmpty()) {
                    item(key = "h-songs", contentType = "header") {
                        val canExpand = results.songs.size > SONG_PREVIEW_COUNT
                        SectionHeader(
                            title = "Titres",
                            actionLabel = when {
                                !canExpand -> null
                                songsExpanded -> "Réduire"
                                else -> "Tout voir (${results.songs.size})"
                            },
                            onAction = if (canExpand) ({ songsExpanded = !songsExpanded }) else null,
                        )
                    }
                    items(visibleSongs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
                        val isCurrent = currentSongId == song.id
                        SongRow(
                            song = song,
                            onClick = { viewModel.playSong(song, results.songs) },
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isFavorite = song.id in favorites,
                            modifier = itemMotion(reduceMotion)
                                .padding(horizontal = Space.sm)
                                .semantics { if (isCurrent) stateDescription = "En cours de lecture" },
                            onLongClick = { menuSong = song },
                            onMenuClick = { menuSong = song },
                        )
                    }
                }

                if (results.artists.isNotEmpty()) {
                    item(key = "h-artists", contentType = "header") { SectionHeader("Artistes") }
                    item(key = "artists", contentType = "artistRow") {
                        LazyRow(
                            // ArtistCard porte Space.sm de marge interne : gutter = md + sm.
                            contentPadding = PaddingValues(horizontal = Space.md),
                        ) {
                            items(results.artists, key = { it.name }) { artist ->
                                ArtistCard(
                                    artist = artist,
                                    onClick = { onOpenArtist(artist.name) },
                                    modifier = Modifier.width(ArtistCardWidth),
                                )
                            }
                        }
                    }
                }

                if (results.albums.isNotEmpty()) {
                    item(key = "h-albums", contentType = "header") { SectionHeader("Albums") }
                    item(key = "albums", contentType = "albumRow") {
                        LazyRow(contentPadding = PaddingValues(horizontal = Space.md)) {
                            items(results.albums, key = { it.id }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { onOpenAlbum(album.id) },
                                    modifier = Modifier.width(AlbumCardWidth),
                                )
                            }
                        }
                    }
                }

                if (results.genres.isNotEmpty()) {
                    item(key = "h-genres", contentType = "header") { SectionHeader("Genres") }
                    items(results.genres, key = { "genre-${it.name}" }, contentType = { "genre" }) { genre ->
                        GenreRow(
                            genre = genre,
                            onClick = { onOpenGenre(genre.name) },
                            modifier = itemMotion(reduceMotion)
                                .padding(horizontal = Space.sm),
                        )
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
