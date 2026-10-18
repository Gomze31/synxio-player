package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralAlbums
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.ArtworkMosaic
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Taille de la pochette héroïque des en-têtes de détail. */
private val HeroArtworkSize = 216.dp

/** Vignette d'album dans la liste d'un artiste. */
private val AlbumThumbSize = 56.dp

/**
 * Écran générique « une pochette, un titre, une liste de morceaux ».
 * Sert pour les genres, les dossiers et tout regroupement ponctuel.
 *
 * [meta] remplace la ligne de statistiques par défaut (n titres · durée) quand l'appelant
 * veut y glisser d'autres informations (année d'un album…). [onSubtitleClick] rend le
 * sous-titre actionnable (ex. ouvrir l'artiste depuis un album).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    meta: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    subtitleClickLabel: String? = null,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val stats = remember(songs, meta) {
        meta ?: "${songs.size.pluralSongs()} · ${songs.sumOf { it.durationMs }.asLongDuration()}"
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DetailTopBar(
                title = title,
                onBack = onBack,
                listState = listState,
                scrollBehavior = scrollBehavior,
                actions = { topBarActions() },
            )
        },
    ) { padding ->
        when {
            songs.isEmpty() && !library.hasScanned -> {
                DetailLoading(Modifier.padding(padding))
                return@Scaffold
            }
            songs.isEmpty() -> {
                EmptyState(
                    title = "Rien ici",
                    subtitle = "Cette sélection ne contient aucun titre.",
                    modifier = Modifier.padding(padding),
                    actionLabel = "Retour",
                    onAction = onBack,
                )
                return@Scaffold
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Space.xl),
        ) {
            item(key = "header", contentType = "header") {
                DetailHeader(
                    artworkModel = artworkModel,
                    title = title,
                    subtitle = subtitle,
                    stats = stats,
                    onPlay = { viewModel.play(songs) },
                    onShuffle = { viewModel.shufflePlay(songs) },
                    onSubtitleClick = onSubtitleClick,
                    subtitleClickLabel = subtitleClickLabel,
                    extras = headerExtras,
                )
            }

            // Clé « id#occurrence » : un titre présent deux fois dans une playlist donnait
            // deux clés identiques, et Compose plantait à l'ouverture de la liste.
            items(songs.withOccurrenceKeys(), key = { it.first }, contentType = { "song" }) { (_, song) ->
                val isCurrent = currentSongId == song.id
                SongRow(
                    song = song,
                    onClick = { viewModel.playSong(song, songs) },
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    isFavorite = song.id in favorites,
                    showArtwork = !showTrackNumbers,
                    trackNumber = song.track.takeIf { showTrackNumbers },
                    onLongClick = { menuSong = song },
                    onMenuClick = { menuSong = song },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = Space.sm)
                        .currentSongSemantics(isCurrent, isPlaying),
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

/** Annonce le titre en cours aux lecteurs d'écran : la couleur seule ne suffit pas. */
private fun Modifier.currentSongSemantics(isCurrent: Boolean, isPlaying: Boolean): Modifier =
    if (!isCurrent) this
    else semantics { stateDescription = if (isPlaying) "En cours de lecture" else "En pause" }

/**
 * Barre du haut des écrans de détail.
 *
 * Au repos, le titre est déjà affiché en grand dans l'en-tête : le répéter dans la barre
 * ferait doublon. Il n'apparaît (en fondu) qu'une fois l'en-tête sorti de l'écran.
 * [listState] nul = pas d'en-tête (chargement, introuvable) : le titre est toujours visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    listState: LazyListState?,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    val showTitle by remember(listState) {
        derivedStateOf { listState == null || listState.firstVisibleItemIndex > 0 }
    }
    TopAppBar(
        colors = synxioTopBarColors(),
        title = {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(Motion.standardOrSnap(reduceMotion, Motion.SHORT)),
                exit = fadeOut(Motion.standardOrSnap(reduceMotion, Motion.SHORT)),
            ) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Écran sans contenu (chargement ou élément introuvable).
 *
 * Ces écrans n'ont pas de barre basse : sans la barre du haut et son bouton retour,
 * l'utilisateur resterait coincé sur un message.
 */
@Composable
private fun DetailMessageScreen(
    topBarTitle: String,
    onBack: () -> Unit,
    loading: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { DetailTopBar(title = if (loading) "" else topBarTitle, onBack = onBack, listState = null) },
    ) { padding ->
        if (loading) {
            DetailLoading(Modifier.padding(padding))
        } else {
            EmptyState(
                title = title,
                subtitle = subtitle,
                icon = icon,
                modifier = Modifier.padding(padding),
                actionLabel = "Retour",
                onAction = onBack,
            )
        }
    }
}

/** Indicateur discret pendant que la bibliothèque finit son premier scan. */
@Composable
private fun DetailLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "Chargement de la bibliothèque" },
        )
    }
}

@Composable
private fun DetailHeader(
    artworkModel: Any?,
    title: String,
    subtitle: String,
    stats: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSubtitleClick: (() -> Unit)? = null,
    subtitleClickLabel: String? = null,
    extras: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.sm, bottom = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (artworkModel is List<*>) {
            ArtworkMosaic(
                models = artworkModel,
                modifier = Modifier.size(HeroArtworkSize),
                shape = SynxioShape.artworkLg,
            )
        } else {
            Artwork(
                model = artworkModel,
                modifier = Modifier.size(HeroArtworkSize),
                shape = SynxioShape.artworkLg,
            )
        }

        Spacer(Modifier.height(Space.xl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onSubtitleClick != null) {
                    Modifier
                        .clip(SynxioShape.pill)
                        .clickable(onClickLabel = subtitleClickLabel, role = Role.Button, onClick = onSubtitleClick)
                        .heightIn(min = Space.touchTarget)
                        .padding(horizontal = Space.md)
                        .wrapContentHeight(Alignment.CenterVertically)
                } else Modifier,
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stats,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.lg))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Button(
                onClick = onPlay,
                shape = SynxioShape.pill,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Space.touchTarget),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Lecture", style = MaterialTheme.typography.labelLarge)
            }
            FilledTonalButton(
                onClick = onShuffle,
                shape = SynxioShape.pill,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Space.touchTarget),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Aléatoire", style = MaterialTheme.typography.labelLarge)
            }
        }
        extras()
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
        DetailMessageScreen(
            topBarTitle = "Album",
            onBack = onBack,
            loading = !library.hasScanned && albumId != null,
            title = "Album introuvable",
            subtitle = "Il a peut-être été supprimé depuis le dernier scan.",
            icon = Icons.Rounded.Album,
        )
        return
    }

    val meta = remember(album) {
        listOfNotNull(
            album.year.takeIf { it > 0 }?.toString(),
            album.songCount.pluralSongs(),
            album.durationMs.asLongDuration(),
        ).joinToString(" · ")
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
        meta = meta,
        onSubtitleClick = { onOpenArtist(album.artist) },
        subtitleClickLabel = "Voir l'artiste",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    viewModel: AppViewModel,
    artistName: String,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val artist = remember(artistName, library) { viewModel.artistByName(artistName) }
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    if (artist == null) {
        DetailMessageScreen(
            topBarTitle = artistName.ifBlank { "Artiste" },
            onBack = onBack,
            loading = !library.hasScanned,
            title = "Artiste introuvable",
            subtitle = "Il a peut-être été supprimé depuis le dernier scan.",
            icon = Icons.Rounded.Person,
        )
        return
    }

    val topSongs = remember(artist) { artist.songs.sortedByDescending { it.dateAddedSec }.take(5) }
    val meta = remember(artist) {
        "${artist.albumCount.pluralAlbums()} · ${artist.songCount.pluralSongs()} · ${artist.durationMs.asLongDuration()}"
    }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DetailTopBar(
                title = artist.name,
                onBack = onBack,
                listState = listState,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Space.xl),
        ) {
            item(key = "header", contentType = "header") {
                DetailHeader(
                    artworkModel = artist.artworkUri,
                    title = artist.name,
                    subtitle = "",
                    stats = meta,
                    onPlay = { viewModel.play(artist.songs) },
                    onShuffle = { viewModel.shufflePlay(artist.songs) },
                )
            }

            item(key = "recent-title", contentType = "section") {
                SectionHeader("Titres récents", Modifier.padding(top = Space.sm))
            }
            items(topSongs, key = { "top-${it.id}" }, contentType = { "song" }) { song ->
                val isCurrent = currentSongId == song.id
                SongRow(
                    song = song,
                    onClick = { viewModel.playSong(song, artist.songs) },
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    isFavorite = song.id in favorites,
                    modifier = Modifier
                        .padding(horizontal = Space.sm)
                        .currentSongSemantics(isCurrent, isPlaying),
                    onLongClick = { menuSong = song },
                    onMenuClick = { menuSong = song },
                )
            }

            item(key = "albums-title", contentType = "section") {
                SectionHeader("Albums", Modifier.padding(top = Space.xl))
            }
            items(artist.albums, key = { "album-${it.id}" }, contentType = { "album" }) { album ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Ouvrir l'album", role = Role.Button) { onOpenAlbum(album.id) }
                        .padding(horizontal = Space.gutter, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(album.artworkUri, Modifier.size(AlbumThumbSize), SynxioShape.artworkSm)
                    Spacer(Modifier.width(Space.lg))
                    Column(Modifier.weight(1f)) {
                        Text(
                            album.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(
                                album.year.takeIf { it > 0 }?.toString(),
                                album.songCount.pluralSongs(),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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

/** Distingue « pas encore reçu » de « reçu, mais la playlist n'existe pas ». */
private class Loaded<T>(val value: T)

@Composable
fun PlaylistDetailScreen(
    viewModel: AppViewModel,
    playlistId: Long,
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val state by produceState<Loaded<Playlist?>?>(initialValue = null, playlistId) {
        viewModel.observePlaylist(playlistId).collect { value = Loaded(it) }
    }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }

    val current = state?.value
    if (current == null) {
        DetailMessageScreen(
            topBarTitle = "Playlist",
            onBack = onBack,
            loading = state == null,
            title = "Playlist introuvable",
            subtitle = "Elle a peut-être été supprimée.",
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
        )
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
                    if (current.songs.size > 1) {
                        DropdownMenuItem(
                            text = { Text("Réorganiser") },
                            leadingIcon = { Icon(Icons.Rounded.SwapVert, null) },
                            onClick = { reordering = true; menuExpanded = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { renaming = true; menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer la playlist") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { confirmDelete = true; menuExpanded = false },
                    )
                }
            }
        },
    )

    if (reordering) {
        fr.synxio.player.ui.components.ReorderSongsSheet(
            title = "Réorganiser",
            songs = current.songs,
            onMove = { from, to -> viewModel.moveInPlaylist(current.id, from, to) },
            onDismiss = { reordering = false },
        )
    }

    if (renaming) {
        RenamePlaylistDialog(
            initialName = current.name,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                viewModel.renamePlaylist(current.id, name)
                viewModel.showMessage("Playlist renommée")
                renaming = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("Supprimer « ${current.name} » ?") },
            text = { Text("La playlist sera supprimée. Les titres restent dans ta bibliothèque.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Le ViewModel affiche lui-même la confirmation de suppression.
                    viewModel.deletePlaylist(current)
                    onBack()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            },
        )
    }
}

/** Renommage avec validation en ligne : un nom vide est refusé sur place, pas en silence. */
@Composable
private fun RenamePlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var showError by remember { mutableStateOf(false) }
    val isBlank = name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer la playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; showError = false },
                singleLine = true,
                label = { Text("Nom") },
                isError = showError && isBlank,
                supportingText = if (showError && isBlank) {
                    { Text("Le nom ne peut pas être vide.") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (isBlank) showError = true else onConfirm(name.trim())
            }) { Text("Renommer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/** Associe à chaque titre une clé unique, même quand il apparaît plusieurs fois. */
private fun List<fr.synxio.player.data.model.Song>.withOccurrenceKeys(): List<Pair<String, fr.synxio.player.data.model.Song>> {
    val seen = HashMap<Long, Int>()
    return map { song ->
        val n = seen.merge(song.id, 1, Int::plus) ?: 1
        "${song.id}#$n" to song
    }
}
