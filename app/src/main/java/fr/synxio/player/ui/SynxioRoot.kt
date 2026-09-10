package fr.synxio.player.ui

import android.Manifest
import android.os.Build
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.synxio.player.ui.components.MiniPlayer
import fr.synxio.player.ui.screens.AlbumDetailScreen
import fr.synxio.player.ui.screens.ArtistDetailScreen
import fr.synxio.player.ui.screens.AdvancedSearchScreen
import fr.synxio.player.ui.screens.AboutScreen
import fr.synxio.player.ui.screens.BackupScreen
import fr.synxio.player.ui.screens.EqualizerScreen
import fr.synxio.player.ui.screens.HistoryScreen
import fr.synxio.player.ui.screens.HomeScreen
import fr.synxio.player.ui.screens.LibraryScreen
import fr.synxio.player.ui.screens.PermissionScreen
import fr.synxio.player.ui.screens.PlaylistDetailScreen
import fr.synxio.player.ui.screens.PlaylistsScreen
import fr.synxio.player.ui.screens.RepairScreen
import fr.synxio.player.ui.screens.SearchScreen
import fr.synxio.player.ui.screens.SettingsScreen
import fr.synxio.player.ui.screens.SongListScreen
import fr.synxio.player.ui.screens.StatsScreen
import fr.synxio.player.ui.screens.TagEditorScreen
import fr.synxio.player.ui.screens.nowplaying.NowPlayingScreen
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Destinations de la barre de navigation basse. */
/**
 * Libellés courts volontairement : à cinq onglets, "Bibliothèque" et "Recherche"
 * passent à la ligne sur un écran étroit.
 */
enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Accueil", Icons.Rounded.Home),
    LIBRARY("library", "Musique", Icons.Rounded.LibraryMusic),
    PLAYLISTS("playlists", "Listes", Icons.Rounded.PlaylistPlay),
    SEARCH("search", "Chercher", Icons.Rounded.Search),
    SETTINGS("settings", "Réglages", Icons.Rounded.Settings),
}

object Routes {
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistName}"
    const val GENRE = "genre/{genreName}"
    const val FOLDER = "folder/{folderPath}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val EQUALIZER = "equalizer"
    const val REPAIR = "repair"
    const val TAGS = "tags/{songId}"
    const val STATS = "stats"
    const val HISTORY = "history"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val ADVANCED_SEARCH = "advanced_search"

    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist/${name.encode()}"
    fun genre(name: String) = "genre/${name.encode()}"
    fun folder(path: String) = "folder/${path.encode()}"
    fun playlist(id: Long) = "playlist/$id"
    fun tags(songId: Long) = "tags/$songId"

    // Base64 URL-safe plutôt qu'URL-encoding : les noms d'artistes et les chemins de
    // dossiers contiennent des "/" et des "+" que Navigation décode déjà une fois,
    // ce qui casserait un double encodage classique.
    private val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    private fun String.encode(): String =
        Base64.encodeToString(toByteArray(Charsets.UTF_8), FLAGS)

    fun decode(value: String): String =
        runCatching { String(Base64.decode(value, FLAGS), Charsets.UTF_8) }.getOrDefault(value)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SynxioRoot(viewModel: AppViewModel, openPlayerOnStart: Boolean) {
    val permission = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    LaunchedEffect(permission.status.isGranted) {
        if (permission.status.isGranted) viewModel.onPermissionGranted()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (permission.status.isGranted) {
            MainScaffold(viewModel, openPlayerOnStart)
        } else {
            PermissionScreen(onRequest = { permission.launchPermissionRequest() })
        }
    }
}

@Composable
private fun MainScaffold(viewModel: AppViewModel, openPlayerOnStart: Boolean) {
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    var playerExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(openPlayerOnStart) {
        if (openPlayerOnStart) playerExpanded = true
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = playerState.currentSong != null && !playerExpanded,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        MiniPlayer(
                            state = playerState,
                            onExpand = { playerExpanded = true },
                            onPlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                        )
                    }
                    BottomBar(navController)
                }
            },
        ) { padding ->
            AppNavHost(
                navController = navController,
                viewModel = viewModel,
                onOpenPlayer = { playerExpanded = true },
                modifier = Modifier.padding(padding),
            )
        }

        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(180)),
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                onCollapse = { playerExpanded = false },
                onOpenAlbum = { id ->
                    playerExpanded = false
                    navController.navigate(Routes.album(id))
                },
                onOpenArtist = { name ->
                    playerExpanded = false
                    navController.navigate(Routes.artist(name))
                },
                onEditTags = { songId ->
                    playerExpanded = false
                    navController.navigate(Routes.tags(songId))
                },
                onOpenEqualizer = {
                    playerExpanded = false
                    navController.navigate(Routes.EQUALIZER)
                },
            )
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Masquer la barre de navigation pour les écrans plein écran
    val hideBottomBar = currentRoute?.startsWith("album/") == true ||
            currentRoute?.startsWith("artist/") == true ||
            currentRoute?.startsWith("genre/") == true ||
            currentRoute?.startsWith("folder/") == true ||
            currentRoute?.startsWith("playlist/") == true ||
            currentRoute == Routes.EQUALIZER ||
            currentRoute == Routes.TAGS ||
            currentRoute == Routes.STATS ||
            currentRoute == Routes.HISTORY ||
            currentRoute == Routes.BACKUP ||
            currentRoute == Routes.ABOUT ||
            currentRoute == Routes.ADVANCED_SEARCH

    if (!hideBottomBar) {
        NavigationBar {
            TopLevel.entries.forEach { destination ->
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = {
                        navController.navigate(destination.route) {
                            // Un seul exemplaire de chaque onglet dans la pile, état conservé.
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = {
                        Text(
                            text = destination.label,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewModel: AppViewModel,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevel.HOME.route,
        modifier = modifier,
    ) {
        composable(TopLevel.HOME.route) {
            HomeScreen(
                viewModel = viewModel,
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenPlayer = onOpenPlayer,
                onSeeAll = { navController.navigate(TopLevel.LIBRARY.route) },
            )
        }

        composable(TopLevel.LIBRARY.route) {
            LibraryScreen(
                viewModel = viewModel,
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenGenre = { navController.navigate(Routes.genre(it)) },
                onOpenFolder = { navController.navigate(Routes.folder(it)) },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        composable(TopLevel.PLAYLISTS.route) {
            PlaylistsScreen(
                viewModel = viewModel,
                onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
            )
        }

        composable(TopLevel.SEARCH.route) {
            SearchScreen(
                viewModel = viewModel,
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenGenre = { navController.navigate(Routes.genre(it)) },
            )
        }

        composable(TopLevel.SETTINGS.route) {
            SettingsScreen(
                viewModel = viewModel,
                onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
                onOpenRepair = { navController.navigate(Routes.REPAIR) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToBackup = { navController.navigate(Routes.BACKUP) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateToAdvancedSearch = { navController.navigate(Routes.ADVANCED_SEARCH) }
            )
        }

        composable(Routes.ALBUM) { entry ->
            val id = entry.arguments?.getString("albumId")?.toLongOrNull()
            AlbumDetailScreen(
                viewModel = viewModel,
                albumId = id,
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        composable(Routes.ARTIST) { entry ->
            val name = entry.arguments?.getString("artistName")?.let(Routes::decode).orEmpty()
            ArtistDetailScreen(
                viewModel = viewModel,
                artistName = name,
                onBack = { navController.popBackStack() },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
            )
        }

        composable(Routes.GENRE) { entry ->
            val name = entry.arguments?.getString("genreName")?.let(Routes::decode).orEmpty()
            val genre = viewModel.genreByName(name)
            SongListScreen(
                viewModel = viewModel,
                title = name,
                subtitle = "Genre",
                songs = genre?.songs.orEmpty(),
                artworkModel = genre?.artworkUris?.firstOrNull(),
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        composable(Routes.FOLDER) { entry ->
            val path = entry.arguments?.getString("folderPath")?.let(Routes::decode).orEmpty()
            val folder = viewModel.folderByPath(path)
            SongListScreen(
                viewModel = viewModel,
                title = folder?.name ?: "Dossier",
                subtitle = path,
                songs = folder?.songs.orEmpty(),
                artworkModel = folder?.songs?.firstOrNull()?.artworkUri,
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        composable(Routes.PLAYLIST) { entry ->
            val id = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: -1L
            PlaylistDetailScreen(
                viewModel = viewModel,
                playlistId = id,
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        composable(Routes.EQUALIZER) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.REPAIR) {
            RepairScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TAGS) { entry ->
            val songId = entry.arguments?.getString("songId")?.toLongOrNull() ?: -1L
            TagEditorScreen(songId = songId, onBack = { navController.popBackStack() })
        }

        // ========================================================================
        // NOUVELLES ROUTES
        // ========================================================================

        composable(Routes.STATS) {
            StatsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onPlaySong = { songs, index ->
                    viewModel.play(songs, index)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onPlaySong = { songs, index ->
                    viewModel.play(songs, index)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.BACKUP) {
            BackupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADVANCED_SEARCH) {
            AdvancedSearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPlaySong = { songs, index ->
                    viewModel.play(songs, index)
                    navController.popBackStack()
                },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) }
            )
        }
    }
}
