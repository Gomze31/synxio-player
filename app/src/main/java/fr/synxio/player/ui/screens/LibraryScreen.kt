package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.ArtistSort
import fr.synxio.player.data.model.LibraryTab
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.ArtistCard
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.FolderRow
import fr.synxio.player.ui.components.GenreRow
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onEditTags: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = LibraryTab.entries
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(settings.defaultTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 12.dp) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label) },
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (tabs[page]) {
                LibraryTab.SONGS -> SongsTab(viewModel, onOpenAlbum, onOpenArtist, onEditTags)
                LibraryTab.ALBUMS -> AlbumsTab(viewModel, onOpenAlbum)
                LibraryTab.ARTISTS -> ArtistsTab(viewModel, onOpenArtist)
                LibraryTab.GENRES -> GenresTab(viewModel, onOpenGenre)
                LibraryTab.FOLDERS -> FoldersTab(viewModel, onOpenFolder)
            }
        }
    }
}

@Composable
private fun SongsTab(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val songs by viewModel.sortedSongs.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    if (songs.isEmpty()) {
        EmptyState("Aucun titre", "Ta bibliothèque est vide pour le moment.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        item {
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

        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                onClick = { viewModel.playSong(song, songs) },
                isCurrent = playerState.currentSong?.id == song.id,
                isPlaying = playerState.isPlaying,
                isFavorite = song.id in favorites,
                onLongClick = { menuSong = song },
                onMenuClick = { menuSong = song },
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
private fun AlbumsTab(viewModel: AppViewModel, onOpenAlbum: (Long) -> Unit) {
    val albums by viewModel.sortedAlbums.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    if (albums.isEmpty()) {
        EmptyState("Aucun album", "Rien à afficher pour l'instant.")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(settings.albumGridColumns.coerceIn(2, 4)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
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
            )
        }

        items(albums, key = { it.id }) { album ->
            AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
        }
    }
}

@Composable
private fun ArtistsTab(viewModel: AppViewModel, onOpenArtist: (String) -> Unit) {
    val artists by viewModel.sortedArtists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    if (artists.isEmpty()) {
        EmptyState("Aucun artiste", "Rien à afficher pour l'instant.")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
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
            )
        }

        items(artists, key = { it.name }) { artist ->
            ArtistCard(artist = artist, onClick = { onOpenArtist(artist.name) })
        }
    }
}

@Composable
private fun GenresTab(viewModel: AppViewModel, onOpenGenre: (String) -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    if (library.genres.isEmpty()) {
        EmptyState("Aucun genre", "Tes fichiers n'ont pas de tag de genre.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        items(library.genres, key = { it.name }) { genre ->
            GenreRow(genre = genre, onClick = { onOpenGenre(genre.name) })
        }
    }
}

@Composable
private fun FoldersTab(viewModel: AppViewModel, onOpenFolder: (String) -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        items(library.folders, key = { it.path }) { folder ->
            FolderRow(folder = folder, onClick = { onOpenFolder(folder.path) })
        }
    }
}

/** Barre « n éléments · tri · lecture / aléatoire » présente en tête de chaque onglet. */
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
) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onPlayAll, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text("Lire", Modifier.padding(start = 6.dp))
            }
            FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Text("Aléatoire", Modifier.padding(start = 6.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$count élément${if (count > 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                TextButton(onClick = { expanded = true }) {
                    Icon(Icons.Rounded.SortByAlpha, contentDescription = null)
                    Text(sortLabel, Modifier.padding(start = 6.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    sortOptions.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onSortSelected(index); expanded = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (descending) "Ordre croissant" else "Ordre décroissant") },
                        onClick = { onToggleDirection(); expanded = false },
                    )
                }
            }
        }
    }
}
