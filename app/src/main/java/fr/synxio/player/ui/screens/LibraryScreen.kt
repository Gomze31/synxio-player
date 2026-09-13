package fr.synxio.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import fr.synxio.player.ui.components.AddToPlaylistSheet
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
                LibraryTab.PODCASTS -> PodcastsTab(viewModel, onOpenAlbum, onOpenArtist, onEditTags)
                LibraryTab.RADIOS -> RadiosTab(viewModel)
            }
        }
    }
}

@Composable
private fun RadiosTab(viewModel: AppViewModel) {
    val progress by viewModel.similarityProgress.collectAsStateWithLifecycle()
    val analysedCount by viewModel.similarityAnalysedCount.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val totalSongs = library.songs.size
        
        if (analysedCount < totalSongs * 0.9f || progress.running) {
            // Pas assez de musiques analysées
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Analyse requise",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Pour générer des radios intelligentes, Synxio a besoin d'analyser l'empreinte sonore de tes musiques (basses, tempo, brillance...).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            if (progress.running) {
                CircularProgressIndicator(progress = { progress.fraction })
                Spacer(modifier = Modifier.height(8.dp))
                Text("${progress.done} / ${progress.total}", style = MaterialTheme.typography.labelMedium)
            } else {
                Button(onClick = { viewModel.analyseLibrary() }) {
                    Text("Lancer l'analyse ($analysedCount / $totalSongs)")
                }
            }
        } else {
            // Radios prêtes
            Text(
                "Choisis ton humeur",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            fr.synxio.player.data.repo.Mood.entries.forEach { mood ->
                Card(
                    onClick = { viewModel.playMoodRadio(mood) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            mood.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastsTab(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    if (podcasts.isEmpty()) {
        EmptyState("Aucun podcast", "Ta bibliothèque de podcasts et livres audio est vide pour le moment.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        items(podcasts, key = { it.id }) { song ->
            SongRow(
                song = song,
                onClick = { viewModel.playSong(song, podcasts) },
                isCurrent = playerState.currentSong?.id == song.id,
                isPlaying = playerState.isPlaying,
                isFavorite = song.id in favorites,
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
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // Sélection multiple : l'appui long bascule en mode sélection au lieu d'ouvrir
    // le menu, et les appuis suivants cochent/décochent.
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val selectionMode = selected.isNotEmpty()
    val selectedSongs = remember(selected, songs) { songs.filter { it.id in selected } }

    BackHandler(enabled = selectionMode) { selected = emptySet() }

    if (songs.isEmpty()) {
        EmptyState("Aucun titre", "Ta bibliothèque est vide pour le moment.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (selectionMode) {
            stickyHeader {
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
                )
            }
        }

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
            val isSelected = song.id in selected
            SongRow(
                song = song,
                onClick = {
                    if (selectionMode) {
                        selected = if (isSelected) selected - song.id else selected + song.id
                    } else {
                        viewModel.playSong(song, songs)
                    }
                },
                isCurrent = playerState.currentSong?.id == song.id,
                isPlaying = playerState.isPlaying,
                isFavorite = song.id in favorites,
                modifier = if (isSelected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.shapes.medium,
                    )
                } else {
                    Modifier
                },
                onLongClick = { selected = selected + song.id },
                onMenuClick = { if (!selectionMode) menuSong = song },
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

/** Barre contextuelle affichée pendant une sélection multiple. */
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Annuler la sélection")
                }
                Text(
                    text = "$count sélectionné${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = if (allSelected) onClear else onSelectAll) {
                    Text(if (allSelected) "Aucun" else "Tout")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Lire la sélection")
                }
                IconButton(onClick = onPlayNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Lire juste après")
                }
                IconButton(onClick = onAddToQueue) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Ajouter à la file")
                }
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Ajouter à une playlist")
                }
                IconButton(onClick = onFavorite) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Basculer les favoris")
                }
            }
        }
    }
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
