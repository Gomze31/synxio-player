package fr.synxio.player.ui

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.synxio.player.data.repo.SmartPlaylistId
import fr.synxio.player.ui.components.MiniPlayer
import fr.synxio.player.ui.screens.DuplicatesScreen
import fr.synxio.player.ui.screens.RecentsScreen
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
import fr.synxio.player.ui.screens.AudiobooksScreen
import fr.synxio.player.ui.screens.RepairScreen
import fr.synxio.player.ui.screens.RulePlaylistEditorScreen
import fr.synxio.player.ui.screens.SearchScreen
import fr.synxio.player.ui.screens.SettingsScreen
import fr.synxio.player.ui.screens.SongListScreen
import fr.synxio.player.ui.screens.StatsScreen
import fr.synxio.player.ui.screens.TagEditorScreen
import fr.synxio.player.ui.screens.nowplaying.NowPlayingScreen
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.UpdateViewModel

/** Destinations de la barre de navigation basse. */
/**
 * Libellés courts volontairement : à cinq onglets, "Bibliothèque" et "Recherche"
 * passent à la ligne sur un écran étroit.
 */
enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Accueil", Icons.Rounded.Home),
    LIBRARY("library", "Musique", Icons.Rounded.LibraryMusic),
    AUDIOBOOKS("audiobooks", "Livres audio", Icons.Rounded.MenuBook),
    SEARCH("search", "Recherche", Icons.Rounded.Search),
    RADIOS("radios", "Radios", Icons.Rounded.Radio),
    SETTINGS("settings", "Paramètres", Icons.Rounded.Settings),
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
    const val DUPLICATES = "duplicates"
    const val RECENTS = "recents"
    const val COMING_SOON = "coming_soon"
    const val DRIVE_MODE = "drive_mode"
    const val SMART = "smart/{smartId}"
    const val RULE = "rule/{ruleId}"
    const val RULE_EDITOR = "rule_editor/{ruleId}"
    const val AUDIOBOOKS = "audiobooks"
    const val PARTY_MODE = "party_mode"

    fun album(id: Long) = "album/$id"
    fun smart(id: SmartPlaylistId) = "smart/${id.name}"
    fun rule(id: Long) = "rule/$id"
    fun ruleEditor(id: Long = -1L) = "rule_editor/$id"
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
    val showBottomBar = shouldShowBottomBar(navController)
    val snackbarHost = remember { SnackbarHostState() }
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    var playerExpanded by remember { mutableStateOf(false) }

    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(openPlayerOnStart) {
        if (openPlayerOnStart) playerExpanded = true
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }

    // Le partage est collecté ici et non dans l'écran du lecteur : la carte doit pouvoir
    // être générée depuis n'importe où, y compris quand le lecteur est replié.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.shareIntents.collect { intent ->
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Partager le titre"))
            }.onFailure { android.util.Log.w("ShareCard", "startActivity a échoué", it) }
        }
    }

    LaunchedEffect(updateState.pendingIntent) {
        updateState.pendingIntent?.let { intent ->
            context.startActivity(intent)
            updateViewModel.consumeIntent()
        }
    }

    LaunchedEffect(updateState.message) {
        updateState.message?.let { msg ->
            snackbarHost.showSnackbar(msg)
            updateViewModel.consumeMessage()
        }
    }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    var dismissUpdate by remember { mutableStateOf(false) }

    if (updateState.hasUpdate && !dismissUpdate && !updateState.downloading) {
        AlertDialog(
            onDismissRequest = { dismissUpdate = true },
            title = { Text("Mise à jour disponible") },
            text = { Text("La version ${updateState.available?.versionName} est disponible !\n\n${updateState.available?.notes}") },
            confirmButton = {
                TextButton(onClick = { updateViewModel.downloadAndInstall() }) {
                    Text("Mettre à jour")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissUpdate = true }) {
                    Text("Plus tard")
                }
            }
        )
    }

    if (updateState.downloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Téléchargement...") },
            text = { 
                Column {
                    Text("Téléchargement de la mise à jour en cours.")
                    Spacer(Modifier.padding(8.dp))
                    LinearProgressIndicator(
                        progress = { updateState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { updateViewModel.cancel() }) {
                    Text("Annuler")
                }
            }
        )
    }

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

                    if (showBottomBar) {
                        // `NavigationBar` absorbe déjà l'encoche de navigation système.
                        BottomBar(navController)
                    } else {
                        // Sans elle, plus rien n'absorbe cette encoche : le mini-lecteur
                        // descendait sous la barre système, qui interceptait la moitié
                        // basse de ses boutons. Ils paraissaient affichés mais ne
                        // répondaient qu'au tiers supérieur.
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        )
                    }
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
                onOpenDriveMode = {
                    playerExpanded = false
                    navController.navigate(Routes.DRIVE_MODE)
                },
                onOpenPartyMode = {
                    playerExpanded = false
                    navController.navigate(Routes.PARTY_MODE)
                },
            )
        }
    }
}

/**
 * Les écrans plein écran, qui ont leur propre barre de titre et un bouton retour.
 *
 * Décidé ici plutôt que dans [BottomBar] : le conteneur doit connaître la réponse pour
 * compenser l'encoche de navigation quand la barre est masquée.
 */
@Composable
private fun shouldShowBottomBar(navController: NavHostController): Boolean {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: return true

    val fullScreen = route.startsWith("album/") ||
            route.startsWith("artist/") ||
            route.startsWith("genre/") ||
            route.startsWith("folder/") ||
            route.startsWith("playlist/") ||
            route.startsWith("smart/") ||
            route.startsWith("rule/") ||
            route.startsWith("rule_editor/") ||
            route.startsWith("tags/") ||
            route == Routes.EQUALIZER ||
            route == Routes.REPAIR ||
            route == Routes.STATS ||
            route == Routes.HISTORY ||
            route == Routes.BACKUP ||
            route == Routes.ABOUT ||
            route == Routes.ADVANCED_SEARCH ||
            route == Routes.DUPLICATES ||
            route == Routes.RECENTS ||
            route == Routes.DRIVE_MODE ||
            route == Routes.PARTY_MODE

    return !fullScreen
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
                onSeeRecents = { navController.navigate(Routes.RECENTS) },
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
                onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                onOpenSmartPlaylist = { navController.navigate(Routes.smart(it)) },
                onOpenRulePlaylist = { navController.navigate(Routes.rule(it)) },
                onCreateRulePlaylist = { navController.navigate(Routes.ruleEditor()) },
                onEditRulePlaylist = { navController.navigate(Routes.ruleEditor(it)) },
            )
        }

        composable(TopLevel.AUDIOBOOKS.route) {
            AudiobooksScreen(
                viewModel = viewModel,
                onOpenFolder = { navController.navigate(Routes.folder(it)) },
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

        composable(TopLevel.RADIOS.route) {
            fr.synxio.player.ui.screens.RadiosScreen(
                viewModel = viewModel,
            )
        }

        composable(TopLevel.SETTINGS.route) {
            SettingsScreen(
                viewModel = viewModel,
                onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
                onOpenRepair = { navController.navigate(Routes.REPAIR) },
                onOpenDuplicates = { navController.navigate(Routes.DUPLICATES) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToBackup = { navController.navigate(Routes.BACKUP) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateToAdvancedSearch = { navController.navigate(Routes.ADVANCED_SEARCH) },
                onNavigateToComingSoon = { navController.navigate(Routes.COMING_SOON) }
            )
        }
        
        composable(Routes.COMING_SOON) {
            fr.synxio.player.ui.screens.ComingSoonScreen(
                onBack = { navController.popBackStack() }
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

        composable(Routes.DUPLICATES) {
            DuplicatesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.RECENTS) {
            RecentsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        composable(Routes.DRIVE_MODE) {
            fr.synxio.player.ui.screens.DriveModeScreen(
                viewModel = viewModel,
                onExit = { navController.popBackStack() }
            )
        }

        composable(Routes.PARTY_MODE) {
            fr.synxio.player.ui.screens.PartyScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SMART) { entry ->
            // L'identifiant vient de l'URL : une valeur inconnue (raccourci obsolète,
            // lien restauré) ne doit pas faire planter la navigation.
            val id = entry.arguments?.getString("smartId")
                ?.let { name -> SmartPlaylistId.entries.firstOrNull { it.name == name } }
            val smart = id?.let(viewModel::smartPlaylist)
            SongListScreen(
                viewModel = viewModel,
                title = smart?.title ?: "Sélection",
                subtitle = smart?.description ?: "Sélection automatique",
                songs = smart?.songs.orEmpty(),
                artworkModel = smart?.artworkUris?.firstOrNull(),
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
            )
        }

        composable(
            route = Routes.RULE,
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("ruleId") ?: -1L
            val rulePlaylists by viewModel.rulePlaylists.collectAsStateWithLifecycle()
            val rule = rulePlaylists.firstOrNull { it.id == id }
            var menuExpanded by remember { mutableStateOf(false) }
            var deleting by remember { mutableStateOf(false) }

            SongListScreen(
                viewModel = viewModel,
                title = rule?.name ?: "Règle",
                subtitle = "Playlist à règles",
                songs = rule?.songs.orEmpty(),
                artworkModel = rule?.songs?.distinctBy { it.albumId }?.take(4)?.map { it.artworkUri },
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                topBarActions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Options de la règle")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Modifier") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(Routes.ruleEditor(id))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer") },
                                onClick = {
                                    menuExpanded = false
                                    deleting = true
                                },
                            )
                        }
                    }
                },
            )

            if (deleting && rule != null) {
                AlertDialog(
                    onDismissRequest = { deleting = false },
                    title = { Text("Supprimer la règle") },
                    text = { Text("Supprimer « ${rule.name} » ? Cette action ne peut pas être annulée.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteRulePlaylist(rule)
                            deleting = false
                            navController.popBackStack()
                        }) { Text("Supprimer") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleting = false }) { Text("Annuler") }
                    },
                )
            }
        }

        composable(
            route = Routes.RULE_EDITOR,
            arguments = listOf(
                navArgument("ruleId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("ruleId") ?: -1L
            RulePlaylistEditorScreen(
                ruleId = id,
                onBack = { navController.popBackStack() },
            )
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
