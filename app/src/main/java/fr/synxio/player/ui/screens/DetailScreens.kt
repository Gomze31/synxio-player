package fr.synxio.player.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.ArtworkMosaic
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.SynxioDialog
import fr.synxio.player.ui.viewmodel.AppViewModel

/**
 * Écran générique « une pochette, un titre, une liste de morceaux ».
 * Sert pour les genres, les dossiers et tout regroupement ponctuel.
 */
@Composable
fun SongListScreen(
    viewModel: AppViewModel,
    title: String,
    subtitle: String,
    songs: List<Song>,
    artworkModel: Any?,
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTrackNumbers: Boolean = false,
    onOpenAlbum: ((Long) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    onRemoveSong: ((Song) -> Unit)? = null,
    topBarActions: @Composable () -> Unit = {},
    headerExtras: @Composable () -> Unit = {},
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = { topBarActions() },
            )
        },
    ) { padding ->
        if (songs.isEmpty()) {
            EmptyState("Rien ici", "Cette sélection ne contient aucun titre.", Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            item {
                DetailHeader(
                    artworkModel = artworkModel,
                    title = title,
                    subtitle = subtitle,
                    stats = "${songs.size.pluralSongs()} · ${songs.sumOf { it.durationMs }.asLongDuration()}",
                    onPlay = { viewModel.play(songs) },
                    onShuffle = { viewModel.shufflePlay(songs) },
                    extras = headerExtras,
                )
            }

            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.playSong(song, songs) },
                    isCurrent = playerState.currentSong?.id == song.id,
                    isPlaying = playerState.isPlaying,
                    isFavorite = song.id in favorites,
                    showArtwork = !showTrackNumbers,
                    trackNumber = song.track.takeIf { showTrackNumbers },
                    onLongClick = { menuSong = song },
                    onMenuClick = { menuSong = song },
                )
            }
        }
    }

    val selected = menuSong
    SongMenuHost(
        viewModel = viewModel,
        song = selected,
        onDismiss = { menuSong = null },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onEditTags = onEditTags,
        onRemoveFromPlaylist = if (onRemoveSong != null && selected != null) {
            { onRemoveSong(selected) }
        } else null,
    )
}

@Composable
private fun DetailHeader(
    artworkModel: Any?,
    title: String,
    subtitle: String,
    stats: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    extras: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (artworkModel is List<*>) {
            ArtworkMosaic(
                models = artworkModel,
                modifier = Modifier.size(200.dp),
                shape = RoundedCornerShape(24.dp),
            )
        } else {
            Artwork(
                model = artworkModel,
                modifier = Modifier.size(200.dp),
                shape = RoundedCornerShape(24.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stats,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text("Lire", Modifier.padding(start = 6.dp))
            }
            FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Text("Aléatoire", Modifier.padding(start = 6.dp))
            }
        }
        extras()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun AlbumDetailScreen(
    viewModel: AppViewModel,
    albumId: Long?,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val album = remember(albumId, library) { albumId?.let { viewModel.albumById(it) } }

    if (album == null) {
        EmptyState("Album introuvable", "Il a peut-être été supprimé depuis le dernier scan.")
        return
    }

    SongListScreen(
        viewModel = viewModel,
        title = album.title,
        subtitle = album.artist,
        songs = album.songs,
        artworkModel = album.artworkUri,
        onBack = onBack,
        onEditTags = onEditTags,
        showTrackNumbers = true,
        onOpenArtist = onOpenArtist,
        headerExtras = {
            if (album.year > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sorti en ${album.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onOpenArtist(album.artist) }) {
                Text("Voir tout de ${album.artist}")
            }
        },
    )
}

@Composable
fun ArtistDetailScreen(
    viewModel: AppViewModel,
    artistName: String,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val artist = remember(artistName, library) { viewModel.artistByName(artistName) }
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    if (artist == null) {
        EmptyState("Artiste introuvable", "Il a peut-être été supprimé depuis le dernier scan.")
        return
    }

    val topSongs = remember(artist) { artist.songs.sortedByDescending { it.dateAddedSec }.take(5) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                DetailHeader(
                    artworkModel = artist.artworkUri,
                    title = artist.name,
                    subtitle = "",
                    stats = "${artist.albumCount} albums · ${artist.songCount.pluralSongs()} · ${artist.durationMs.asLongDuration()}",
                    onPlay = { viewModel.play(artist.songs) },
                    onShuffle = { viewModel.shufflePlay(artist.songs) },
                )
            }

            item { SectionHeader("Titres récents") }
            items(topSongs, key = { "top-${it.id}" }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.playSong(song, artist.songs) },
                    isCurrent = playerState.currentSong?.id == song.id,
                    isPlaying = playerState.isPlaying,
                    isFavorite = song.id in favorites,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onLongClick = { menuSong = song },
                    onMenuClick = { menuSong = song },
                )
            }

            item { SectionHeader("Albums") }
            items(artist.albums, key = { it.id }) { album ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAlbum(album.id) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(album.artworkUri, Modifier.size(56.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Column {
                        Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            text = listOfNotNull(
                                album.year.takeIf { it > 0 }?.toString(),
                                album.songCount.pluralSongs(),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    )
}

@Composable
fun PlaylistDetailScreen(
    viewModel: AppViewModel,
    playlistId: Long,
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val playlist by produceState<Playlist?>(initialValue = null, playlistId) {
        viewModel.observePlaylist(playlistId).collect { value = it }
    }
    var renaming by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val current = playlist
    if (current == null) {
        EmptyState("Playlist introuvable", "Elle a peut-être été supprimée.")
        return
    }

    SongListScreen(
        viewModel = viewModel,
        title = current.name,
        subtitle = "Playlist",
        songs = current.songs,
        artworkModel = current.artworkUris,
        onBack = onBack,
        onEditTags = onEditTags,
        onRemoveSong = { viewModel.removeFromPlaylist(current.id, it.id) },
        topBarActions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options de la playlist")
                }
                DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { renaming = true; menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer la playlist") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = {
                            viewModel.deletePlaylist(current)
                            menuExpanded = false
                            onBack()
                        },
                    )
                }
            }
        },
    )

    if (renaming) {
        var name by remember { mutableStateOf(current.name) }
        SynxioDialog(onDismiss = { renaming = false }) {
            Text("Renommer la playlist", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nom") },
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { renaming = false }) { Text("Annuler") }
                Button(onClick = {
                    if (name.isNotBlank()) viewModel.renamePlaylist(current.id, name.trim())
                    renaming = false
                }) { Text("Renommer") }
            }
        }
    }
}
