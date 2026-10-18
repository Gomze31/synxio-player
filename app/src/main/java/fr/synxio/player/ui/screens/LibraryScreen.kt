package fr.synxio.player.ui.screens

import androidx.compose.ui.graphics.Color
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.FolderRow
import fr.synxio.player.ui.components.GenreRow
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/** Taille de l'icône dans la pastille ronde des états vides/explicatifs. */
private val StateIconSize = 48.dp

/** Diamètre de la pastille ronde qui porte l'icône d'un état ou d'une ambiance. */
private val StateBadgeSize = 88.dp
private val MoodBadgeSize = 48.dp

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
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(settings.defaultTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        // En-tête : l'écran n'avait pas de titre, on ne savait pas où l'on était après
        // une navigation. Les livres audio y ont leur entrée depuis qu'ils ont quitté
        // la barre de navigation (cinq destinations au plus, règle Material).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.gutter, end = Space.sm, top = Space.md, bottom = Space.xs),
        ) {
            Text(
                "Bibliothèque",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = onOpenAudiobooks) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Livres audio")
            }
        }
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = Space.lg,
            // Transparente : le fond ambiant (couleur de la pochette) passe dessous.
            containerColor = Color.Transparent,
            divider = {},
        ) {
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
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    viewModel = viewModel,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenSmartPlaylist = onOpenSmartPlaylist,
                    onOpenRulePlaylist = onOpenRulePlaylist,
                    onCreateRulePlaylist = onCreateRulePlaylist,
                    onEditRulePlaylist = onEditRulePlaylist
                )
            }
        }
    }
}

/**
 * Affiche un indicateur tant que le premier scan n'est pas terminé, sinon l'état vide.
 *
 * Sans cette distinction, chaque onglet annonçait « bibliothèque vide » pendant les
 * premières secondes après le lancement, ce qui faisait croire à un problème.
 */
@Composable
private fun EmptyOrLoading(
    viewModel: AppViewModel,
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    if (!library.hasScanned) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.semantics { contentDescription = "Chargement de la bibliothèque" },
            )
        }
    } else {
        EmptyState(title = title, subtitle = subtitle, icon = icon)
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
    val numbers = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(StateBadgeSize)
                .background(scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(StateIconSize),
                tint = scheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(Space.xl))
        Text(
            "Analyse requise",
            style = MaterialTheme.typography.titleMedium,
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
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Analyse en cours" },
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "$done / $total titres analysés",
                style = numbers,
                color = scheme.onSurfaceVariant,
            )
        } else {
            Text(
                "$analysedCount / $totalSongs titres déjà analysés",
                style = numbers,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.md))
            Button(
                onClick = onAnalyse,
                shape = SynxioShape.pill,
                modifier = Modifier.heightIn(min = Space.touchTarget),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Lancer l'analyse")
            }
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
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Column(Modifier.padding(bottom = Space.sm)) {
                Text(
                    "Choisis ton ambiance",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Un mix jusqu'à 50 titres de ta bibliothèque, choisis selon leur son.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(Mood.entries, key = { it.name }, contentType = { "mood" }) { mood ->
            MoodCard(mood = mood, onClick = { onMood(mood) })
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
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAnalyse) { Text("Compléter") }
                }
            }
        }
    }
}

@Composable
private fun MoodCard(mood: Mood, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Lancer l'ambiance ${mood.label}" },
        shape = SynxioShape.card,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(Space.lg)) {
            Box(
                modifier = Modifier
                    .size(MoodBadgeSize)
                    .background(scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mood.icon, contentDescription = null, tint = scheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(Space.md))
            Text(
                mood.label,
                style = MaterialTheme.typography.titleMedium,
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
        contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.sm),
    ) {
        items(podcasts, key = { it.id }) { song ->
            val isCurrent = currentSongId == song.id
            SongRow(
                song = song,
                onClick = { viewModel.playSong(song, podcasts) },
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                isFavorite = song.id in favorites,
                onMenuClick = { menuSong = song },
                modifier = Modifier.nowPlayingSemantics(isCurrent, isPlaying),
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
        contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.sm),
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

        items(songs, key = { it.id }, contentType = { "song" }) { song ->
            val isSelected = song.id in selected
            val isCurrent = currentSongId == song.id
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
                    .then(
                        if (isSelected) {
                            Modifier.background(MaterialTheme.colorScheme.secondaryContainer, SynxioShape.card)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (selectionMode) {
                            Modifier.semantics { stateDescription = if (isSelected) "Sélectionné" else "Non sélectionné" }
                        } else {
                            Modifier.nowPlayingSemantics(isCurrent, isPlaying)
                        }
                    ),
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
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("Supprimer $count titre${if (count > 1) "s" else ""} ?") },
            text = { Text("Les fichiers seront définitivement supprimés de l'appareil.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }

    // Couche tonale plutôt qu'ombre : la barre reste lisible au-dessus de la liste
    // sans effet de carte flottante.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = SynxioShape.card,
    ) {
        Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Annuler la sélection")
                }
                Text(
                    text = "$count sélectionné${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = if (allSelected) onClear else onSelectAll) {
                    Text(if (allSelected) "Aucun" else "Tout")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
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
                    Icon(Icons.Rounded.Favorite, contentDescription = "Basculer les favoris")
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
}

@Composable
private fun AlbumsTab(viewModel: AppViewModel, onOpenAlbum: (Long) -> Unit) {
    val albums by viewModel.sortedAlbums.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    if (albums.isEmpty()) {
        EmptyOrLoading(
            viewModel = viewModel,
            title = "Aucun album",
            subtitle = "Tes albums apparaîtront ici après le scan.",
            icon = Icons.Rounded.Album,
        )
        return
    }

    // AlbumCard porte déjà sa propre marge interne : gouttière de grille + marge de carte
    // retombent sur la marge latérale d'écran.
    LazyVerticalGrid(
        columns = GridCells.Fixed(settings.albumGridColumns.coerceIn(2, 4)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm),
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
            )
        }

        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
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
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm),
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
            )
        }

        items(artists, key = { it.name }, contentType = { "artist" }) { artist ->
            ArtistCard(artist = artist, onClick = { onOpenArtist(artist.name) })
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
        contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.md),
    ) {
        items(library.genres, key = { it.name }) { genre ->
            GenreRow(genre = genre, onClick = { onOpenGenre(genre.name) })
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
        contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.md),
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

    Column(Modifier.padding(horizontal = Space.md, vertical = Space.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Button(
                onClick = onPlayAll,
                shape = SynxioShape.pill,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Space.touchTarget),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Lecture")
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
                Text("Aléatoire")
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$count élément${if (count > 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                TextButton(onClick = { expanded = true }) {
                    Icon(Icons.Rounded.SortByAlpha, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        sortLabel + if (descending) " ↓" else " ↑",
                        modifier = Modifier.semantics {
                            contentDescription = "Trier : $sortLabel, ordre ${if (descending) "décroissant" else "croissant"}"
                        },
                    )
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
