package fr.synxio.player.ui

import androidx.compose.ui.graphics.graphicsLayer
import android.Manifest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import fr.synxio.player.ui.components.LocalNavAnimatedScope
import fr.synxio.player.ui.components.LocalPlayerAnimatedScope
import fr.synxio.player.ui.components.LocalSharedTransitionScope
import fr.synxio.player.ui.components.SynxioSnackbar
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glowShadow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.content.Intent
import android.os.Build
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavBackStackEntry
import com.google.accompanist.permissions.shouldShowRationale
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import fr.synxio.player.ui.theme.AmbientBackdrop
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.glass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
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
import fr.synxio.player.ui.screens.RecapScreen
import fr.synxio.player.ui.screens.AlarmsScreen
import fr.synxio.player.ui.screens.TagEditorScreen
import fr.synxio.player.ui.screens.nowplaying.NowPlayingScreen
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.UpdateViewModel

/**
 * Destinations de la barre de navigation basse.
 *
 * Cinq au plus (règle Material) : les livres audio, sixième onglet auparavant, sont
 * accessibles depuis l'en-tête de la bibliothèque. Libellés courts volontairement :
 * "Bibliothèque" passerait à la ligne sur un écran étroit.
 */
enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Accueil", Icons.Rounded.Home),
    LIBRARY("library", "Musique", Icons.Rounded.LibraryMusic),
    SEARCH("search", "Recherche", Icons.Rounded.Search),
    RADIOS("radios", "Radios", Icons.Rounded.Radio),
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
    const val DOWNLOAD = "download"
    const val TAGS = "tags/{songId}"
    const val STATS = "stats"
    const val RECAP = "recap"
    const val ALARMS = "alarms"
    const val HISTORY = "history"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val ADVANCED_SEARCH = "advanced_search"
    const val DUPLICATES = "duplicates"
    const val RECENTS = "recents"
    const val DRIVE_MODE = "drive_mode"
    const val SMART = "smart/{smartId}"
    const val RULE = "rule/{ruleId}"
    const val RULE_EDITOR = "rule_editor/{ruleId}"
    const val AUDIOBOOKS = "audiobooks"
    const val PARTY_MODE = "party_mode"
    const val TRIM = "trim/{songId}"

    fun album(id: Long) = "album/$id"
    fun smart(id: SmartPlaylistId) = "smart/${id.name}"
    fun rule(id: Long) = "rule/$id"
    fun ruleEditor(id: Long = -1L) = "rule_editor/$id"
    fun artist(name: String) = "artist/${name.encode()}"
    fun genre(name: String) = "genre/${name.encode()}"
    fun folder(path: String) = "folder/${path.encode()}"
    fun playlist(id: Long) = "playlist/$id"
    fun tags(songId: Long) = "tags/$songId"
    fun trim(songId: Long) = "trim/$songId"

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
fun SynxioRoot(viewModel: AppViewModel, openPlayerRequest: Long) {
    val permission = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    var permissionAsked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(permission.status.isGranted) {
        if (permission.status.isGranted) viewModel.onPermissionGranted()
    }

    // Fond ambiant commun à toute l'app : les écrans sont transparents par-dessus.
    //
    // Le Surface d'origine fournissait aussi la couleur de texte par défaut. Sans elle,
    // tout texte sans couleur explicite retombait au noir : titre du lecteur et nom
    // d'album illisibles sur fond sombre. On la fournit explicitement.
    // Seul « en lecture » intéresse le fond : la position change quatre fois par seconde
    // et ne doit pas recomposer la racine de l'app.
    val isPlaying by remember(viewModel) {
        viewModel.playerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(drifting = isPlaying)
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        if (permission.status.isGranted) {
            MainScaffold(viewModel, openPlayerRequest)
        } else {
            PermissionScreen(
                // Après « Ne plus demander », la demande système ne s'affiche plus : le
                // bouton renvoie alors vers les réglages de l'app au lieu de ne rien faire.
                permanentlyDenied = permissionAsked && !permission.status.shouldShowRationale,
                onRequest = {
                    permissionAsked = true
                    permission.launchPermissionRequest()
                },
            )
        }
        }
    }
}

@Composable
private fun MainScaffold(viewModel: AppViewModel, openPlayerRequest: Long) {
    val navController = rememberNavController()
    val showBottomBar = shouldShowBottomBar(navController)
    val snackbarHost = remember { SnackbarHostState() }
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val metadataProgress by viewModel.metadataProgress.collectAsStateWithLifecycle()
    var playerExpanded by remember { mutableStateOf(false) }

    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    // Un jeton plutôt qu'un booléen : la notification peut demander le lecteur plusieurs
    // fois pendant que l'app tourne, chaque demande doit l'ouvrir.
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0L) playerExpanded = true
    }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.actionMessages.collect { message ->
            val result = snackbarHost.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction()
        }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Mise à jour disponible", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("La version ${updateState.available?.versionName} est disponible !\n\n${updateState.available?.notes}") },
            confirmButton = {
                fr.synxio.player.ui.components.BrandButton(
                    text = "Mettre à jour",
                    onClick = { updateViewModel.downloadAndInstall() },
                )
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Téléchargement…", style = MaterialTheme.typography.headlineSmall) },
            text = { 
                Column {
                    Text("Téléchargement de la mise à jour en cours.")
                    Spacer(Modifier.padding(8.dp))
                    fr.synxio.player.ui.components.BrandProgressBar(
                        progress = updateState.progress,
                        height = 6.dp,
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

    // Couche commune des éléments partagés : pochettes des grilles vers les détails, et
    // pochette du mini-lecteur vers le lecteur plein écran.
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHost) { SynxioSnackbar(it) } },
            bottomBar = {
                Column {
                    // Seules les récupérations demandées à la main s'affichent ici : la passe
                    // automatique de la bibliothèque reste discrète, visible dans les réglages.
                    AnimatedVisibility(
                        visible = metadataProgress.manual && !playerExpanded,
                        enter = slideInVertically(Motion.enter()) { it / 2 } + fadeIn(Motion.enter()),
                        exit = slideOutVertically(Motion.exit()) { it / 2 } + fadeOut(Motion.exit()),
                    ) {
                        fr.synxio.player.ui.components.MetadataProgressPill(metadataProgress)
                    }
                    AnimatedVisibility(
                        visible = playerState.currentSong != null && !playerExpanded,
                        enter = slideInVertically(Motion.enter()) { it / 2 } + fadeIn(Motion.enter()),
                        exit = slideOutVertically(Motion.exit()) { it / 2 } + fadeOut(Motion.exit()),
                    ) {
                        CompositionLocalProvider(LocalPlayerAnimatedScope provides this) {
                        MiniPlayer(
                            state = playerState,
                            onExpand = { playerExpanded = true },
                            onPlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                        )
                        }
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
                // Marge haute réservée aux onglets : leurs en-têtes éditoriaux n'ont pas de
                // barre Material. Les écrans secondaires ont une TopAppBar qui absorbe déjà
                // la barre d'état ; ajouter la marge du Scaffold la doublait.
                modifier = Modifier.padding(
                    top = if (showBottomBar) padding.calculateTopPadding() else 0.dp,
                    bottom = padding.calculateBottomPadding(),
                ),
            )
        }

        // Le lecteur monte depuis le mini-lecteur et redescend vers lui : même axe, et la
        // pochette du mini-lecteur grandit jusqu'à sa place (élément partagé) pendant que
        // l'écran glisse. Animations réduites : simple fondu.
        AnimatedVisibility(
            visible = playerExpanded,
            enter = if (reduceMotion) {
                fadeIn(Motion.enter(Motion.SHORT))
            } else {
                slideInVertically(Motion.enter(Motion.LONG)) { it / 3 } + fadeIn(Motion.enter(Motion.MEDIUM))
            },
            exit = if (reduceMotion) {
                fadeOut(Motion.exit())
            } else {
                slideOutVertically(Motion.exit(Motion.MEDIUM)) { it / 3 } + fadeOut(Motion.exit(Motion.MEDIUM))
            },
        ) {
            CompositionLocalProvider(LocalPlayerAnimatedScope provides this) {
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
                onTrim = { song ->
                    playerExpanded = false
                    navController.navigate(Routes.trim(song.id))
                },
            )
            }
        }
    }
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
            route.startsWith("trim/") ||
            route == Routes.EQUALIZER ||
            route == Routes.REPAIR ||
            route == Routes.DOWNLOAD ||
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

/**
 * Barre de navigation flottante en verre.
 *
 * Une pilule translucide posée au-dessus de l'aurore, au lieu de la barre opaque pleine
 * largeur de Material. L'onglet actif porte une capsule à la signature Lumen qui
 * **glisse** d'un onglet à l'autre (ressort spatial) au lieu de clignoter : on voit d'où
 * l'on vient et où l'on va.
 */
@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedIndex = TopLevel.entries.indexOfFirst { it.route == currentRoute }
    val reduceMotion = LocalReduceMotion.current
    val colors = SynxioTheme.colors

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Space.md, end = Space.md, top = Space.xs, bottom = Space.sm)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .glowShadow(SynxioShape.pill, colors.brandEnd.copy(alpha = 0.5f), elevation = 18.dp)
                .clip(SynxioShape.pill)
                .glass(SynxioShape.pill, strength = 1f)
                .padding(horizontal = Space.xs),
        ) {
            val itemWidth = maxWidth / TopLevel.entries.size
            val indicatorWidth = 56.dp
            val target = itemWidth * selectedIndex.coerceAtLeast(0) + (itemWidth - indicatorWidth) / 2
            val offset by animateDpAsState(
                targetValue = target,
                animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.72f, stiffness = 420f),
                label = "navIndicatorOffset",
            )
            val indicatorAlpha by animateFloatAsState(
                if (selectedIndex >= 0) 1f else 0f,
                Motion.standardOrSnap(reduceMotion, Motion.SHORT),
                label = "navIndicatorAlpha",
            )
            // Capsule commune, dessinée sous les icônes : elle voyage, les icônes restent.
            Box(
                Modifier
                    .offset(x = offset, y = 9.dp)
                    .size(width = indicatorWidth, height = 32.dp)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .clip(SynxioShape.pill)
                    .background(colors.brandBrushHorizontal)
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevel.entries.forEach { destination ->
                    NavItem(
                        destination = destination,
                        selected = currentRoute == destination.route,
                        onClick = { navController.navigateToTopLevel(destination.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val iconTint by animateColorAsState(
        if (selected) colors.onBrand else scheme.onSurfaceVariant,
        Motion.standardOrSnap(reduceMotion, Motion.SHORT),
        label = "navIconTint",
    )
    val labelTint by animateColorAsState(
        if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        Motion.standardOrSnap(reduceMotion, Motion.SHORT),
        label = "navLabelTint",
    )
    val iconScale by animateFloatAsState(
        if (selected && !reduceMotion) 1.08f else 1f,
        Motion.spatialBouncy(),
        label = "navIcon",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(SynxioShape.pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(width = 56.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = destination.label,
            maxLines = 1,
            softWrap = false,
            color = labelTint,
            // Espacement nul : sur un écran de 360 dp, une colonne fait ~65 dp et
            // « Recherche » débordait avec l'espacement par défaut des libellés.
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
        )
    }
}

/**
 * `composable` qui fournit aussi sa portée d'animation aux éléments partagés
 * ([LocalNavAnimatedScope]) : une pochette posée avec `sharedNavElement` voyage alors
 * d'un écran à l'autre sans que l'écran ait à connaître la navigation.
 */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}

/** Un seul exemplaire de chaque onglet dans la pile, état conservé. */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewModel: AppViewModel,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val topLevelRoutes = remember { TopLevel.entries.map { it.route }.toSet() }
    fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
        initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes

    // Deux gestes de navigation, deux mouvements :
    //  - entre onglets, un fondu enchaîné (« fade through ») : pas de direction, ce sont
    //    des lieux voisins ;
    //  - vers un détail, un glissement court sur l'axe horizontal (« shared axis ») qui
    //    dit « j'avance », et l'inverse au retour.
    // Le défaut de Navigation était un fondu de 700 ms, deux fois plus lent que le reste.
    NavHost(
        navController = navController,
        startDestination = TopLevel.HOME.route,
        modifier = modifier,
        enterTransition = {
            when {
                reduceMotion -> fadeIn(Motion.enter(Motion.SHORT))
                isTabSwitch() -> fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.97f)
                else -> fadeIn(Motion.enter()) + slideInHorizontally(Motion.enter()) { it / 10 }
            }
        },
        exitTransition = {
            when {
                reduceMotion || isTabSwitch() -> fadeOut(Motion.exit())
                else -> fadeOut(Motion.exit()) + slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { -it / 20 }
            }
        },
        popEnterTransition = {
            when {
                reduceMotion -> fadeIn(Motion.enter(Motion.SHORT))
                isTabSwitch() -> fadeIn(Motion.enter())
                else -> fadeIn(Motion.enter()) + slideInHorizontally(Motion.enter()) { -it / 20 }
            }
        },
        popExitTransition = {
            when {
                reduceMotion || isTabSwitch() -> fadeOut(Motion.exit())
                else -> fadeOut(Motion.exit()) + slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { it / 10 }
            }
        },
    ) {
        screen(TopLevel.HOME.route) {
            HomeScreen(
                viewModel = viewModel,
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenPlayer = onOpenPlayer,
                // Comme un appui sur l'onglet : sans ces options, « Tout voir » empilait une
                // seconde bibliothèque par-dessus celle de la barre de navigation.
                onSeeAll = { navController.navigateToTopLevel(TopLevel.LIBRARY.route) },
                onSeeRecents = { navController.navigate(Routes.RECENTS) },
                onOpenSmartPlaylist = { navController.navigate(Routes.smart(it)) },
            )
        }

        screen(TopLevel.LIBRARY.route) {
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
                onOpenAudiobooks = { navController.navigate(Routes.AUDIOBOOKS) },
            )
        }

        screen(Routes.AUDIOBOOKS) {
            AudiobooksScreen(
                viewModel = viewModel,
                onOpenFolder = { navController.navigate(Routes.folder(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        screen(TopLevel.SEARCH.route) {
            SearchScreen(
                viewModel = viewModel,
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenGenre = { navController.navigate(Routes.genre(it)) },
                onNavigateToDownload = { navController.navigate(Routes.DOWNLOAD) },
            )
        }

        screen(TopLevel.RADIOS.route) {
            fr.synxio.player.ui.screens.RadiosScreen(
                viewModel = viewModel,
            )
        }

        screen(TopLevel.SETTINGS.route) {
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
                onOpenAlarms = { navController.navigate(Routes.ALARMS) },
            )
        }
        

        screen(Routes.ALBUM) { entry ->
            val id = entry.arguments?.getString("albumId")?.toLongOrNull()
            AlbumDetailScreen(
                viewModel = viewModel,
                albumId = id,
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        screen(Routes.ARTIST) { entry ->
            val name = entry.arguments?.getString("artistName")?.let(Routes::decode).orEmpty()
            ArtistDetailScreen(
                viewModel = viewModel,
                artistName = name,
                onBack = { navController.popBackStack() },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
            )
        }

        screen(Routes.GENRE) { entry ->
            val name = entry.arguments?.getString("genreName")?.let(Routes::decode).orEmpty()
            val genre = viewModel.genreByName(name)
            SongListScreen(
                viewModel = viewModel,
                title = name,
                subtitle = "Genre",
                eyebrow = "Genre",
                songs = genre?.songs.orEmpty(),
                artworkModel = genre?.artworkUris?.firstOrNull(),
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        screen(Routes.FOLDER) { entry ->
            val path = entry.arguments?.getString("folderPath")?.let(Routes::decode).orEmpty()
            val folder = viewModel.folderByPath(path)
            val songs = folder?.songs.orEmpty()
            // Un dossier de livre audio est un livre : on y montre où en est l'écoute, et
            // le bouton principal reprend au chapitre en cours plutôt qu'au premier.
            val isBook = songs.isNotEmpty() && songs.all { it.isAudiobook }
            val bookProgress by viewModel.bookProgress.collectAsStateWithLifecycle()
            val progress = if (isBook) bookProgress[path] else null
            val resumable = progress != null && progress.started && !progress.finished
            SongListScreen(
                viewModel = viewModel,
                title = folder?.name ?: "Dossier",
                subtitle = path,
                eyebrow = if (isBook) "Livre audio" else "Dossier",
                songs = songs,
                artworkModel = songs.firstOrNull()?.artworkUri,
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
                showTrackNumbers = isBook,
                playLabel = if (resumable) "Reprendre" else "Lire",
                onPlay = if (isBook) ({ viewModel.resumeBook(songs) }) else null,
                headerExtras = {
                    if (progress != null && progress.started) {
                        fr.synxio.player.ui.screens.BookProgressCard(
                            progress = progress,
                            chapter = songs.firstOrNull { it.id == progress.chapterSongId },
                            modifier = Modifier.padding(top = Space.lg),
                        )
                    }
                },
            )
        }

        screen(Routes.PLAYLIST) { entry ->
            val id = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: -1L
            PlaylistDetailScreen(
                viewModel = viewModel,
                playlistId = id,
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        screen(Routes.EQUALIZER) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }

        screen(Routes.REPAIR) {
            RepairScreen(onBack = { navController.popBackStack() })
        }

        screen(Routes.DOWNLOAD) {
            fr.synxio.player.ui.screens.DownloadScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        screen(Routes.DUPLICATES) {
            DuplicatesScreen(onBack = { navController.popBackStack() })
        }

        screen(Routes.RECENTS) {
            RecentsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
            )
        }

        screen(Routes.DRIVE_MODE) {
            fr.synxio.player.ui.screens.DriveModeScreen(
                viewModel = viewModel,
                onExit = { navController.popBackStack() }
            )
        }

        screen(Routes.PARTY_MODE) {
            fr.synxio.player.ui.screens.PartyScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        screen(Routes.SMART) { entry ->
            // L'identifiant vient de l'URL : une valeur inconnue (raccourci obsolète,
            // lien restauré) ne doit pas faire planter la navigation.
            val id = entry.arguments?.getString("smartId")
                ?.let { name -> SmartPlaylistId.entries.firstOrNull { it.name == name } }
            val smart = id?.let(viewModel::smartPlaylist)
            SongListScreen(
                viewModel = viewModel,
                title = smart?.title ?: "Sélection",
                subtitle = smart?.description ?: "Sélection automatique",
                eyebrow = "Pour toi",
                songs = smart?.songs.orEmpty(),
                artworkModel = smart?.artworkUris?.firstOrNull(),
                onBack = { navController.popBackStack() },
                onEditTags = { navController.navigate(Routes.tags(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
            )
        }

        screen(
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
                eyebrow = "Playlist à règles",
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text("Supprimer la règle", style = MaterialTheme.typography.headlineSmall) },
                    text = { Text("Supprimer « ${rule.name} » ? Cette action ne peut pas être annulée.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteRulePlaylist(rule)
                            deleting = false
                            navController.popBackStack()
                        }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleting = false }) { Text("Annuler") }
                    },
                )
            }
        }

        screen(
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

        screen(Routes.TAGS) { entry ->
            val songId = entry.arguments?.getString("songId")?.toLongOrNull() ?: -1L
            TagEditorScreen(
                songId = songId,
                onBack = { navController.popBackStack() },
                onMessage = viewModel::showMessage,
            )
        }

        // ========================================================================
        // NOUVELLES ROUTES
        // ========================================================================

        screen(Routes.STATS) {
            StatsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                // On reste sur l'écran : le mini-lecteur apparaît et l'on peut continuer à
                // parcourir. Le renvoyer en arrière coupait l'utilisateur dans son élan.
                onPlaySong = { songs, index -> viewModel.play(songs, index) },
                onOpenRecap = { navController.navigate(Routes.RECAP) },
            )
        }

        screen(Routes.ALARMS) {
            AlarmsScreen(onBack = { navController.popBackStack() })
        }

        screen(Routes.RECAP) {
            RecapScreen(
                onBack = { navController.popBackStack() },
                onPlay = { viewModel.play(it) },
            )
        }

        screen(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                // On reste sur l'écran : le mini-lecteur apparaît et l'on peut continuer à
                // parcourir. Le renvoyer en arrière coupait l'utilisateur dans son élan.
                onPlaySong = { songs, index -> viewModel.play(songs, index) }
            )
        }

        screen(Routes.BACKUP) {
            BackupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        screen(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        screen(Routes.ADVANCED_SEARCH) {
            AdvancedSearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPlaySong = { songs, index -> viewModel.play(songs, index) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) }
            )
        }

        screen(Routes.TRIM) { entry ->
            val songId = entry.arguments?.getString("songId")?.toLongOrNull() ?: -1L
            val library by viewModel.library.collectAsStateWithLifecycle()
            val song = library.songs.find { it.id == songId }
            if (song != null) {
                fr.synxio.player.ui.screens.TrimScreen(
                    song = song,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            } else {
                // Naviguer hors composition : un popBackStack direct ici serait rejoué
                // à chaque recomposition.
                androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}
