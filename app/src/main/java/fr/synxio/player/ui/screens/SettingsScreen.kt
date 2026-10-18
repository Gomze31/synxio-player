package fr.synxio.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.BuildConfig
import fr.synxio.player.core.util.asFileSize
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.AccentSource
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.LibraryTab
import fr.synxio.player.data.model.NowPlayingSkin
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.data.model.TextSize
import fr.synxio.player.data.model.ThemeColor
import fr.synxio.player.data.model.ThemeMode
import fr.synxio.player.data.repo.AnalysisProgress
import fr.synxio.player.data.repo.DiscordWebhookUrl
import fr.synxio.player.data.repo.LoudnessProgress
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.ScreenHeader
import fr.synxio.player.ui.components.SynxioDialog
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.ThemePreset
import fr.synxio.player.ui.theme.contrastingOn
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.isDarkSurface
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.DiscordViewModel
import fr.synxio.player.ui.viewmodel.SettingsViewModel
import fr.synxio.player.ui.viewmodel.UpdateViewModel

// Fonctions utilitaires pour convertir String en enum
private fun String.toThemeColor(): ThemeColor = ThemeColor.entries.firstOrNull { it.value == this } ?: ThemeColor.DEFAULT
private fun String.toTextSize(): TextSize = TextSize.entries.firstOrNull { it.name == this } ?: TextSize.NORMAL
@Suppress("unused")
private fun String.toAlbumSort(): AlbumSort = AlbumSort.entries.firstOrNull { it.name == this } ?: AlbumSort.TITLE
@Suppress("unused")
private fun String.toSongSort(): SongSort = SongSort.entries.firstOrNull { it.name == this } ?: SongSort.TITLE

/**
 * Catégories de sous-menus des paramètres.
 */
enum class SettingsSubMenu(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    APPEARANCE(
        "Apparence & Thèmes",
        "Thèmes, couleurs, style du lecteur et affichage",
        Icons.Rounded.Palette,
    ),
    PLAYBACK(
        "Lecture & Audio",
        "Fondu, gapless, silences, reprise casque et rembobinage",
        Icons.Rounded.PlayCircle,
    ),
    VOLUME(
        "Volume & Normalisation",
        "Normalisation du volume et limiteur",
        Icons.AutoMirrored.Rounded.VolumeUp,
    ),
    LIBRARY(
        "Bibliothèque & Fichiers",
        "Durée minimale, onglet par défaut, scan et doublons",
        Icons.Rounded.Folder,
    ),
    EQUALIZER(
        "Égaliseur & Effets Sonores",
        "Égaliseur système, bass boost, spatialisation et gain",
        Icons.Rounded.GraphicEq,
    ),
    LYRICS_AND_SOCIAL(
        "Paroles & Connexions",
        "Paroles LRCLIB, Last.fm scrobble et Discord Presence",
        Icons.Rounded.Language,
    ),
    STATS_AND_HISTORY(
        "Statistiques & Historique",
        "Top écoutes, habitudes et journal de lecture",
        Icons.Rounded.History,
    ),
    BACKUP_AND_RESTORE(
        "Sauvegarde & Données",
        "Exporter, importer et réinitialiser les données",
        Icons.Rounded.Backup,
    ),
    SYSTEM_AND_ABOUT(
        "Système & À propos",
        "Mises à jour, purge du cache et version",
        Icons.Rounded.Info,
    ),
}

/**
 * Regroupement des sous-menus sur le hub : neuf entrées d'affilée se lisaient comme une
 * liste de courses. Rangées par intention (voir, écouter, ranger, partager, garder),
 * elles se parcourent d'un coup d'œil.
 */
private val HubGroups: List<Pair<String, List<SettingsSubMenu>>> = listOf(
    "Apparence" to listOf(SettingsSubMenu.APPEARANCE),
    "Écoute" to listOf(SettingsSubMenu.PLAYBACK, SettingsSubMenu.VOLUME, SettingsSubMenu.EQUALIZER),
    "Bibliothèque" to listOf(SettingsSubMenu.LIBRARY, SettingsSubMenu.STATS_AND_HISTORY),
    "Connexions" to listOf(SettingsSubMenu.LYRICS_AND_SOCIAL),
    "Données et système" to listOf(SettingsSubMenu.BACKUP_AND_RESTORE, SettingsSubMenu.SYSTEM_AND_ABOUT),
)

private fun SettingsSubMenu.getSummary(
    settings: fr.synxio.player.core.prefs.Settings,
    library: fr.synxio.player.data.repo.Library,
): String = when (this) {
    SettingsSubMenu.APPEARANCE ->
        "${settings.themeMode.label} • ${ThemePreset.fromName(settings.themePreset).label} • ${settings.nowPlayingSkin.label}"
    SettingsSubMenu.PLAYBACK ->
        "Gapless ${if (settings.gaplessEnabled) "Actif" else "Désactivé"} • Fondu ${settings.crossfadeMs / 1000}s • ${if (settings.autoRewindSec > 0) "Rembobinage ${settings.autoRewindSec}s" else "Direct"}"
    SettingsSubMenu.VOLUME ->
        "Normalisation ${if (settings.normalizeVolume) "active (${settings.normalizeTargetDbfs.toInt()} dBFS)" else "désactivée"} • Limite ${if (settings.maxVolumeLimit) "${(settings.maxVolumeValue * 100).toInt()} %" else "aucune"}"
    SettingsSubMenu.LIBRARY ->
        "Min. ${settings.minDurationSec}s • Onglet ${settings.defaultTab.label} • ${library.albums.size} albums"
    SettingsSubMenu.EQUALIZER ->
        "Égaliseur ${if (settings.equalizerEnabled) "Actif" else "Désactivé"} • Bass Boost ${settings.bassBoost / 10}%"
    SettingsSubMenu.LYRICS_AND_SOCIAL ->
        "Paroles ${if (settings.lyricsOnlineEnabled) "LRCLIB" else "Locale"} • ${if (settings.scrobbleEnabled) "Last.fm connecté" else "Last.fm inactif"}"
    SettingsSubMenu.STATS_AND_HISTORY ->
        "Top écoutes et journal de lecture"
    SettingsSubMenu.BACKUP_AND_RESTORE ->
        "Export et import JSON des données"
    SettingsSubMenu.SYSTEM_AND_ABOUT ->
        "Synxio v${BuildConfig.VERSION_NAME} • Mises à jour & Cache"
}

/**
 * Écran des réglages.
 *
 * Un hub (en-tête éditorial, sous-menus rangés en groupes de verre) et des sous-pages
 * thématiques. Entrer dans une sous-page glisse vers la gauche, en sortir revient vers
 * la droite : le même axe que la navigation de l'app, à plus petite échelle. Le retour
 * système ramène au hub avant de quitter l'écran.
 */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onOpenEqualizer: () -> Unit,
    onOpenRepair: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAdvancedSearch: () -> Unit,
    // Écran « Prochainement » supprimé : paramètre conservé le temps de retirer l'appel.
    @Suppress("UNUSED_PARAMETER") onNavigateToComingSoon: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val lastFmAvailable = remember { settingsViewModel.lastFmAvailable }
    var currentSubMenu by rememberSaveable { mutableStateOf<SettingsSubMenu?>(null) }
    val reduce = LocalReduceMotion.current

    var showColorSheet by remember { mutableStateOf(false) }
    var showTextSizeSheet by remember { mutableStateOf(false) }

    // Conservé hors de l'animation : revenir au hub retrouve la position de défilement.
    val hubState = rememberLazyListState()

    BackHandler(enabled = currentSubMenu != null) {
        currentSubMenu = null
    }

    AnimatedContent(
        targetState = currentSubMenu,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            when {
                reduce -> fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.XS))
                // Vers une sous-page : elle arrive de la droite, le hub recule à gauche.
                targetState != null ->
                    (slideInHorizontally(Motion.enter()) { it / 6 } + fadeIn(Motion.enter())) togetherWith
                        (slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { -it / 12 } + fadeOut(Motion.exit()))
                // Retour au hub : mouvement miroir.
                else ->
                    (slideInHorizontally(Motion.enter()) { -it / 12 } + fadeIn(Motion.enter())) togetherWith
                        (slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { it / 6 } + fadeOut(Motion.exit()))
            }
        },
        label = "settingsSection",
    ) { menu ->
        if (menu == null) {
            SettingsHub(
                settings = settings,
                library = library,
                listState = hubState,
                onOpenEqualizer = onOpenEqualizer,
                onOpen = { currentSubMenu = it },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.xxl + Space.bottomInset),
            ) {
                item(key = "header") {
                    SubPageHeader(menu = menu, onBack = { currentSubMenu = null })
                }
                when (menu) {
                    // ================================================================
                    // SOUS-MENU : APPARENCE
                    // ================================================================
                    SettingsSubMenu.APPEARANCE -> {
                        group(0, "theme", "Thème") {
                            SegmentedSetting(
                                title = "Mode de thème",
                                icon = Icons.Rounded.Brightness6,
                                // Libellé court dans le segment (« AMOLED »), complet pour TalkBack.
                                options = ThemeMode.entries.map { it.label.substringBefore(" (") },
                                descriptions = ThemeMode.entries.map { it.label },
                                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                                onSelect = { settingsViewModel.setThemeMode(ThemeMode.entries[it]) },
                            )
                            GroupDivider()
                            ThemePresetPicker(
                                selected = ThemePreset.fromName(settings.themePreset),
                                amoled = settings.themeMode == ThemeMode.AMOLED,
                                onSelect = settingsViewModel::setThemePreset,
                            )
                            GroupDivider()
                            ClickableSetting(
                                title = "Couleurs de l'application",
                                subtitle = settings.accentSource.label,
                                icon = Icons.Rounded.ColorLens,
                                onClick = { showColorSheet = true }
                            )
                        }

                        group(1, "player", "Lecteur") {
                            SegmentedSetting(
                                title = "Style du lecteur",
                                icon = Icons.Rounded.PlayCircle,
                                options = NowPlayingSkin.entries.map { it.label },
                                selectedIndex = NowPlayingSkin.entries.indexOf(settings.nowPlayingSkin),
                                onSelect = { settingsViewModel.setSkin(NowPlayingSkin.entries[it]) },
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Fond flouté",
                                subtitle = "Floute la pochette derrière le lecteur plein écran",
                                icon = Icons.Rounded.BlurOn,
                                checked = settings.blurBackground,
                                onCheckedChange = settingsViewModel::setBlurBackground
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Visualiseur audio",
                                subtitle = "Affiche l'indicateur d'ondes sonores dans le lecteur",
                                icon = Icons.Rounded.GraphicEq,
                                checked = settings.showVisualizer,
                                onCheckedChange = settingsViewModel::setShowVisualizer
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Maintenir l'écran allumé",
                                subtitle = "Garde l'écran actif tant que le lecteur plein écran est affiché",
                                icon = Icons.Rounded.SmartDisplay,
                                checked = settings.keepScreenOnNowPlaying,
                                onCheckedChange = settingsViewModel::setKeepScreenOnNowPlaying
                            )
                        }

                        group(2, "display", "Affichage") {
                            SliderSetting(
                                title = "Colonnes de la grille d'albums",
                                subtitle = "Nombre de colonnes dans la vue grille des albums",
                                icon = Icons.Rounded.Album,
                                value = settings.albumGridColumns.toFloat(),
                                valueRange = 2f..4f,
                                steps = 1,
                                display = { "${it.toInt()} colonnes" },
                                onChange = { settingsViewModel.setAlbumColumns(it.toInt()) }
                            )
                            GroupDivider()
                            ChipSetting(
                                title = "Taille du texte",
                                icon = Icons.Rounded.FormatSize,
                                options = TextSize.entries.map { entry -> entry.label },
                                selectedIndex = TextSize.entries.indexOf(settings.textSize.toTextSize()),
                                onSelect = { settingsViewModel.setTextSize(TextSize.entries[it]) }
                            )
                        }

                        // Mouvement et lumière vont ensemble : ce sont les deux réglages
                        // de « confort visuel », et les deux coupent des effets ambiants.
                        group(3, "motion", "Mouvement et lumière") {
                            SwitchSetting(
                                title = "Réduire les animations",
                                subtitle = "Désactive certaines animations pour améliorer les performances",
                                icon = Icons.Rounded.DataSaverOn,
                                checked = settings.reduceAnimations,
                                onCheckedChange = settingsViewModel::setReduceAnimations
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Effets lumineux",
                                subtitle = "Halos, aurore du fond et lueur des pochettes",
                                icon = Icons.Rounded.AutoAwesome,
                                checked = settings.glowEffects,
                                onCheckedChange = { settingsViewModel.setGlowEffects(it) }
                            )
                        }

                        // Le lanceur doit accepter l'épinglage : sinon, on explique le
                        // geste manuel plutôt que de proposer un bouton sans effet.
                        group(4, "widgets", "Widgets") {
                            val context = LocalContext.current
                            ClickableSetting(
                                title = "Ajouter le widget compact",
                                subtitle = "Pochette, titre et commandes sur l'écran d'accueil",
                                icon = Icons.Rounded.Widgets,
                                onClick = {
                                    if (!fr.synxio.player.widget.requestPinWidget(context, large = false)) {
                                        viewModel.showMessage("Ajoute-le par un appui long sur l'écran d'accueil › Widgets › Synxio")
                                    }
                                },
                            )
                            GroupDivider()
                            ClickableSetting(
                                title = "Ajouter le grand widget",
                                subtitle = "Grande pochette, progression et toutes les commandes",
                                icon = Icons.Rounded.Widgets,
                                onClick = {
                                    if (!fr.synxio.player.widget.requestPinWidget(context, large = true)) {
                                        viewModel.showMessage("Ajoute-le par un appui long sur l'écran d'accueil › Widgets › Synxio")
                                    }
                                },
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : LECTURE & AUDIO
                    // ================================================================
                    SettingsSubMenu.PLAYBACK -> {
                        group(0, "transitions", "Enchaînement") {
                            SliderSetting(
                                title = "Fondu entre les morceaux",
                                subtitle = "0 s = enchaînement sans blanc (gapless natif)",
                                icon = Icons.Rounded.LinearScale,
                                value = settings.crossfadeMs / 1000f,
                                valueRange = 0f..12f,
                                steps = 11,
                                display = { if (it < 0.5f) "Désactivé" else "${it.toInt()} s" },
                                onChange = { settingsViewModel.setCrossfade((it * 1000).toInt()) }
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Enchaînement gapless",
                                subtitle = "Aucun silence entre deux pistes d'un même album",
                                icon = Icons.Rounded.Loop,
                                checked = settings.gaplessEnabled,
                                onCheckedChange = settingsViewModel::setGapless
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Ignorer les silences",
                                subtitle = "Saute les blancs en début et fin de piste",
                                icon = Icons.Rounded.Loop,
                                checked = settings.skipSilence,
                                onCheckedChange = settingsViewModel::setSkipSilence
                            )
                        }

                        group(1, "queue", "File d'attente") {
                            SwitchSetting(
                                title = "Auto-DJ",
                                subtitle = "Prolonge la file avec des titres similaires quand elle se termine",
                                icon = Icons.Rounded.Loop,
                                checked = settings.autoDjEnabled,
                                onCheckedChange = settingsViewModel::setAutoDj
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Aléatoire sans les titres zappés",
                                subtitle = "Écarte ceux que tu coupes systématiquement",
                                icon = Icons.Rounded.Shuffle,
                                checked = settings.shuffleSkipsDisliked,
                                onCheckedChange = settingsViewModel::setShuffleSkipsDisliked
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Reprendre la file au démarrage",
                                subtitle = "Retrouve ta file d'attente là où tu l'avais laissée",
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                checked = settings.rememberQueue,
                                onCheckedChange = settingsViewModel::setRememberQueue
                            )
                        }

                        group(2, "resume", "Reprise") {
                            val rewindOptions = listOf("Désactivé", "3 s", "5 s")
                            val rewindDescriptions = listOf("Désactivé", "3 secondes", "5 secondes")
                            val rewindValues = listOf(0, 3, 5)
                            val selectedIndex = rewindValues.indexOf(settings.autoRewindSec).coerceAtLeast(0)
                            SegmentedSetting(
                                title = "Rembobinage automatique à la reprise",
                                icon = Icons.Rounded.Restore,
                                options = rewindOptions,
                                descriptions = rewindDescriptions,
                                selectedIndex = selectedIndex,
                                onSelect = { settingsViewModel.setAutoRewindSec(rewindValues[it]) },
                            )
                            GroupDivider()
                            SwitchSetting(
                                title = "Reprendre au branchement du casque",
                                subtitle = "La lecture repart quand tu connectes des écouteurs, filaires ou Bluetooth",
                                icon = Icons.Rounded.PhonelinkSetup,
                                checked = settings.resumeOnHeadsetConnect,
                                onCheckedChange = settingsViewModel::setResumeOnHeadsetConnect
                            )
                        }

                        group(3, "sleep", "Minuterie") {
                            SwitchSetting(
                                title = "Arrêter après inactivité",
                                subtitle = "Arrête la lecture après une période d'inactivité",
                                icon = Icons.Rounded.Timer,
                                checked = settings.stopAfterInactivityMin > 0,
                                onCheckedChange = {
                                    settingsViewModel.setStopAfterInactivity(if (it) 30 else 0)
                                }
                            )
                            Reveal(visible = settings.stopAfterInactivityMin > 0) {
                                Column {
                                    GroupDivider()
                                    SliderSetting(
                                        title = "Durée d'inactivité",
                                        subtitle = "Temps avant l'arrêt automatique",
                                        icon = Icons.Rounded.Timer,
                                        // Pendant la fermeture, la valeur repasse à 0 : on borne
                                        // pour que le curseur ne saute pas hors de sa plage.
                                        value = settings.stopAfterInactivityMin.toFloat().coerceIn(5f, 120f),
                                        valueRange = 5f..120f,
                                        steps = 22,
                                        display = { "${it.toInt()} min" },
                                        onChange = { settingsViewModel.setStopAfterInactivity(it.toInt()) }
                                    )
                                }
                            }
                        }

                        group(4, "radio", "Radio") {
                            val fingerprints by settingsViewModel.similarityProgress
                                .collectAsStateWithLifecycle()
                            FingerprintSetting(
                                progress = fingerprints,
                                pendingCount = settingsViewModel.pendingFingerprintCount(),
                                onAnalyse = settingsViewModel::analyseFingerprints,
                                onCancel = settingsViewModel::cancelFingerprints,
                                onReset = settingsViewModel::resetFingerprints,
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : VOLUME & NORMALISATION
                    // ================================================================
                    SettingsSubMenu.VOLUME -> {
                        group(0, "normalize", "Normalisation") {
                            val progress by settingsViewModel.loudnessProgress.collectAsStateWithLifecycle()
                            NormalizationSetting(
                                enabled = settings.normalizeVolume,
                                targetDbfs = settings.normalizeTargetDbfs,
                                progress = progress,
                                pendingCount = settingsViewModel.pendingLoudnessCount(),
                                onToggle = settingsViewModel::setNormalizeVolume,
                                onTarget = settingsViewModel::setNormalizeTargetDbfs,
                                onAnalyse = settingsViewModel::analyseLoudness,
                                onCancel = settingsViewModel::cancelLoudnessAnalysis,
                                onReset = settingsViewModel::resetLoudness,
                            )
                        }

                        group(1, "limit", "Protection auditive") {
                            SwitchSetting(
                                title = "Limiter le volume",
                                subtitle = "Empêche de dépasser un volume maximum pour protéger l'audition",
                                icon = Icons.Rounded.Speaker,
                                checked = settings.maxVolumeLimit,
                                onCheckedChange = settingsViewModel::setMaxVolumeLimit
                            )
                            Reveal(visible = settings.maxVolumeLimit) {
                                Column {
                                    GroupDivider()
                                    SliderSetting(
                                        title = "Volume maximum",
                                        subtitle = "Niveau de volume maximum autorisé",
                                        icon = Icons.Rounded.LinearScale,
                                        value = settings.maxVolumeValue,
                                        valueRange = 0.5f..1f,
                                        steps = 5,
                                        display = { "${(it * 100).toInt()}%" },
                                        onChange = { settingsViewModel.setMaxVolumeValue(it) }
                                    )
                                }
                            }
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : BIBLIOTHÈQUE & FICHIERS
                    // ================================================================
                    SettingsSubMenu.LIBRARY -> {
                        group(0, "index", "Indexation") {
                            SliderSetting(
                                title = "Durée minimale d'un titre",
                                subtitle = "Filtre les sonneries et notifications indexées comme musique",
                                icon = Icons.Rounded.Timer,
                                value = settings.minDurationSec.toFloat(),
                                valueRange = 0f..120f,
                                steps = 11,
                                display = { if (it < 1f) "Aucun filtre" else "${it.toInt()} s" },
                                onChange = { settingsViewModel.setMinDuration(it.toInt()) }
                            )
                            GroupDivider()
                            ChipSetting(
                                title = "Onglet par défaut",
                                icon = Icons.Rounded.Tab,
                                options = LibraryTab.entries.map { it.label },
                                selectedIndex = LibraryTab.entries.indexOf(settings.defaultTab),
                                onSelect = { settingsViewModel.setDefaultTab(LibraryTab.entries[it]) }
                            )
                        }

                        group(1, "tools", "Outils") {
                            ClickableSetting(
                                title = "Relancer le scan",
                                subtitle = "${library.songs.size.pluralSongs()} · ${library.totalDurationMs.asLongDuration()}",
                                icon = Icons.Rounded.Sync,
                                onClick = { viewModel.rescan() }
                            )
                            GroupDivider()
                            ClickableSetting(
                                title = "Réparer les tags",
                                subtitle = "Corrige artistes, albums, années et pochettes en masse",
                                icon = Icons.Rounded.AutoFixHigh,
                                onClick = onOpenRepair
                            )
                            GroupDivider()
                            ClickableSetting(
                                title = "Supprimer les fichiers en double",
                                subtitle = "Analyse tes fichiers et libère de l'espace de stockage",
                                icon = Icons.Rounded.ContentCopy,
                                onClick = onOpenDuplicates
                            )
                            GroupDivider()
                            ClickableSetting(
                                title = "Recherche avancée",
                                subtitle = "Filtres par artiste, album, genre, durée, etc.",
                                icon = Icons.Rounded.Search,
                                onClick = onNavigateToAdvancedSearch
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : ÉGALISEUR & EFFETS
                    // ================================================================
                    SettingsSubMenu.EQUALIZER -> {
                        group(0, "eq", "Égaliseur") {
                            ClickableSetting(
                                title = "Ouvrir l'égaliseur complet",
                                subtitle = if (settings.equalizerEnabled) "Actif • Personnaliser les bandes et presets" else "Désactivé • Appuyer pour ouvrir",
                                icon = Icons.Rounded.Equalizer,
                                onClick = onOpenEqualizer
                            )
                            Text(
                                text = "Synxio s'interface directement avec le moteur audio système d'Android pour offrir le meilleur compromis latence et clarté acoustique.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = RowStartIndent, end = Space.lg, bottom = Space.md),
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : PAROLES & CONNEXIONS
                    // ================================================================
                    SettingsSubMenu.LYRICS_AND_SOCIAL -> {
                        group(0, "lyrics", "Paroles") {
                            SwitchSetting(
                                title = "Chercher les paroles en ligne",
                                subtitle = "Utilise LRCLIB quand aucun fichier .lrc n'est trouvé",
                                icon = Icons.Rounded.Language,
                                checked = settings.lyricsOnlineEnabled,
                                onCheckedChange = settingsViewModel::setLyricsOnline
                            )
                        }

                        if (lastFmAvailable) {
                            group(1, "lastfm", "Last.fm") {
                                SwitchSetting(
                                    title = "Scrobbling Last.fm",
                                    subtitle = settings.lastFmUsername.takeIf { it.isNotBlank() }
                                        ?.let { "Connecté en tant que $it" }
                                        ?: "Connecte ton compte pour envoyer tes écoutes",
                                    icon = Icons.Rounded.SurroundSound,
                                    checked = settings.scrobbleEnabled,
                                    onCheckedChange = settingsViewModel::setScrobbleEnabled
                                )
                            }
                        }

                        group(2, "discord", "Discord") { DiscordSection(onMessage = viewModel::showMessage) }
                    }

                    // ================================================================
                    // SOUS-MENU : STATISTIQUES & HISTORIQUE
                    // ================================================================
                    SettingsSubMenu.STATS_AND_HISTORY -> {
                        group(0, "stats", "Ton écoute") {
                            ClickableSetting(
                                title = "Mes statistiques",
                                subtitle = "Top artistes, albums, et plus encore",
                                icon = Icons.Rounded.Star,
                                onClick = onNavigateToStats
                            )
                            GroupDivider()
                            ClickableSetting(
                                title = "Historique de lecture",
                                subtitle = "Voir l'historique des morceaux écoutés",
                                icon = Icons.Rounded.History,
                                onClick = onNavigateToHistory
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : SAUVEGARDE & DONNÉES
                    // ================================================================
                    SettingsSubMenu.BACKUP_AND_RESTORE -> {
                        group(0, "backup", "Données") {
                            ClickableSetting(
                                title = "Sauvegarde et restauration",
                                subtitle = "Exporte et importe tes playlists, favoris et données",
                                icon = Icons.Rounded.Save,
                                onClick = onNavigateToBackup
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : SYSTÈME & À PROPOS
                    // ================================================================
                    SettingsSubMenu.SYSTEM_AND_ABOUT -> {
                        // La section se masque d'elle-même sans dépôt de publication configuré.
                        item(key = "updates") {
                            UpdateSection(
                                onMessage = viewModel::showMessage,
                                modifier = Modifier.enterCascade(0, key = "system-updates"),
                            )
                        }

                        group(1, "maintenance", "Maintenance") {
                            ClickableSetting(
                                title = "Nettoyer les caches",
                                subtitle = "Pochettes d'album décodées et fichiers temporaires",
                                icon = Icons.Rounded.Restore,
                                onClick = {
                                    settingsViewModel.clearAllCaches {
                                        viewModel.showMessage("Caches des pochettes et données temporaires nettoyés !")
                                    }
                                }
                            )
                        }

                        group(2, "about", "À propos") {
                            ClickableSetting(
                                title = "À propos de Synxio",
                                subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) - Lecteur local, sans pub, open source",
                                icon = Icons.Rounded.Info,
                                onClick = onNavigateToAbout
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets

    if (showColorSheet) {
        ColorSettingsSheet(
            currentAccentSource = settings.accentSource,
            currentPrimary = settings.primaryColor.toThemeColor(),
            currentSecondary = settings.secondaryColor.toThemeColor(),
            onDismiss = { showColorSheet = false },
            onAccentChange = settingsViewModel::setAccentSource,
            onPrimaryChange = settingsViewModel::setPrimaryColor,
            onSecondaryChange = settingsViewModel::setSecondaryColor
        )
    }

    if (showTextSizeSheet) {
        TextSizeSettingsSheet(
            currentSize = settings.textSize.toTextSize(),
            onDismiss = { showTextSizeSheet = false },
            onSizeChange = settingsViewModel::setTextSize
        )
    }
}

// ============================================================================
// HUB
// ============================================================================

@Composable
private fun SettingsHub(
    settings: fr.synxio.player.core.prefs.Settings,
    library: fr.synxio.player.data.repo.Library,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenEqualizer: () -> Unit,
    onOpen: (SettingsSubMenu) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.xxl + Space.bottomInset),
    ) {
        item(key = "hub-header") {
            ScreenHeader(
                title = "Réglages",
                eyebrow = "Synxio · v${BuildConfig.VERSION_NAME}",
                subtitle = "${library.songs.size.pluralSongs()} · ${library.albums.size} albums",
                actions = {
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Égaliseur rapide")
                    }
                },
            )
        }
        HubGroups.forEachIndexed { index, (title, menus) ->
            item(key = "hub-$title") {
                SettingsGroup(
                    title = title,
                    modifier = Modifier.enterCascade(index, key = "hub-$title"),
                ) {
                    menus.forEachIndexed { i, menu ->
                        if (i > 0) GroupDivider()
                        SubMenuRow(
                            menu = menu,
                            summary = menu.getSummary(settings, library),
                            onClick = { onOpen(menu) },
                        )
                    }
                }
            }
        }
    }
}

/** Entrée du hub : pastille, titre, résumé de l'état courant, chevron. */
@Composable
private fun SubMenuRow(menu: SettingsSubMenu, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(SynxioShape.cardSm)
            .pressBounce(0.98f)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIconTile(menu.icon)
        Column(Modifier.weight(1f)) {
            Text(menu.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Space.xxs))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.sm))
        // Chevron décoratif : la ligne entière est déjà annoncée comme un bouton.
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** En-tête d'une sous-page : retour, puis titre éditorial et rappel de son contenu. */
@Composable
private fun SubPageHeader(menu: SettingsSubMenu, onBack: () -> Unit) {
    Column {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = Space.xs, top = Space.xs),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour aux paramètres")
        }
        ScreenHeader(
            title = menu.title,
            eyebrow = "Réglages",
            subtitle = menu.subtitle,
            modifier = Modifier.padding(top = 0.dp),
        )
    }
}

// ============================================================================
// BRIQUES PARTAGÉES
// ============================================================================

/** Hauteur minimale d'une ligne de réglage (liste Material à deux lignes). */
private val RowMinHeight = 56.dp

/** Pastille d'icône des lignes : taille et arrondi identiques sur tout l'écran. */
private val IconTileSize = 40.dp
private val IconTileShape = RoundedCornerShape(Radius.sm)

/** Marges d'une ligne de réglage, à l'intérieur d'un groupe de verre. */
private val RowPadding = PaddingValues(horizontal = Space.md, vertical = Space.md)

/** Retrait des contrôles placés sous un titre précédé d'une icône. */
private val IconIndent = IconTileSize + Space.md

/** Retrait total d'un contenu aligné sur le texte des lignes (marge + pastille). */
private val RowStartIndent = Space.md + IconIndent

/** Ajoute un groupe de verre comme élément de liste, avec son apparition en cascade. */
private fun LazyListScope.group(
    index: Int,
    key: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        SettingsGroup(
            title = title,
            modifier = Modifier.enterCascade(index, key = key),
            content = content,
        )
    }
}

/**
 * Groupe de réglages : surtitre en capitales lumineuses, puis une carte de verre qui
 * porte les lignes. Les lignes gèrent leurs propres marges pour que l'effet d'appui
 * remplisse la carte d'un bord à l'autre.
 */
@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.lg)
    ) {
        Text(
            title.uppercase(),
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = Space.xs, bottom = Space.sm)
                .semantics { heading() },
        )
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = Space.xs) {
            Column(content = content)
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = RowStartIndent, end = Space.md),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

/** Apparition d'un réglage dépendant (curseur sous son interrupteur) : il se déplie. */
@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduce) fadeIn(Motion.enter(Motion.XS)) else expandVertically(Motion.spatial()) + fadeIn(Motion.enter()),
        exit = if (reduce) fadeOut(Motion.exit(Motion.XS)) else shrinkVertically(Motion.spatial()) + fadeOut(Motion.exit()),
    ) { content() }
}

/** Pastille d'icône teintée par l'accent, en tête de ligne, suivie de son espacement. */
@Composable
private fun SettingIconTile(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = Modifier
            .size(IconTileSize)
            .clip(IconTileShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint
        )
    }
    Spacer(Modifier.width(Space.md))
}

/** Titre et sous-titre d'une ligne de réglage. */
@Composable
private fun SettingTexts(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Interrupteur Material aux couleurs du thème : piste à l'accent, contour discret au repos. */
@Composable
private fun lumenSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    checkedIconColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun lumenSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
    inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
)

/**
 * Paramètre avec interrupteur.
 *
 * Toute la ligne porte l'état (`toggleable`, rôle Switch) et l'interrupteur n'a pas de
 * gestionnaire propre : un seul arrêt de focus, annoncé « interrupteur, activé ». L'icône
 * de validation dans le pouce redit l'état sans la couleur.
 */
@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(SynxioShape.cardSm)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) SettingIconTile(icon)

        SettingTexts(title, subtitle, Modifier.weight(1f))

        Spacer(Modifier.width(Space.md))

        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = lumenSwitchColors(),
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
        )
    }
}

/**
 * Paramètre cliquable : la ligne entière est un bouton, le chevron dit « ça mène plus
 * loin ».
 */
@Composable
private fun ClickableSetting(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(SynxioShape.cardSm)
            .pressBounce(0.98f)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) SettingIconTile(icon)

        SettingTexts(title, subtitle, Modifier.weight(1f))

        Spacer(Modifier.width(Space.sm))
        // Décoratif : le titre de la ligne dit déjà ce que fait l'appui.
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Paramètre avec curseur.
 */
@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.md, end = Space.md, top = Space.md, bottom = Space.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) SettingIconTile(icon)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Le titre cède la place : sans weight, un libellé long comprimait
                    // la valeur jusqu'à la couper en « 2 / colo / nnes ».
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(Space.md))
                    // La valeur dans une pilule teintée : elle se lit comme un résultat.
                    Text(
                        text = display(value),
                        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(SynxioShape.pill)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = Space.sm, vertical = Space.xxs),
                    )
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
            colors = lumenSliderColors(),
            modifier = Modifier
                .padding(start = if (icon != null) IconIndent else 0.dp)
                // TalkBack lit la valeur formatée (« 30 min ») plutôt qu'un pourcentage brut.
                .semantics { stateDescription = display(value) }
        )
    }
}

/** Titre d'un réglage à choix (puces, segments), précédé de sa pastille. */
@Composable
private fun ChoiceTitle(title: String, icon: ImageVector?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) SettingIconTile(icon)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Paramètre avec puces de sélection (listes longues : onglets, tailles de texte).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSetting(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    icon: ImageVector? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(RowPadding)
    ) {
        ChoiceTitle(title, icon)

        Spacer(Modifier.height(Space.md))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (icon != null) IconIndent else 0.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            options.forEachIndexed { index, label ->
                LumenChip(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/**
 * Paramètre à segments (deux à quatre choix courts) : une piste de verre, et un curseur
 * à la signature qui glisse sous le choix actif avec un petit rebond.
 */
@Composable
private fun SegmentedSetting(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    icon: ImageVector? = null,
    descriptions: List<String> = options,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(RowPadding)
    ) {
        ChoiceTitle(title, icon)
        Spacer(Modifier.height(Space.md))
        SegmentedControl(
            options = options,
            descriptions = descriptions,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun SegmentedControl(
    options: List<String>,
    descriptions: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(Space.touchTarget + 4.dp)
            .clip(SynxioShape.pill)
            .glass(SynxioShape.pill, strength = 0.35f)
            .padding(2.dp)
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * selectedIndex.coerceAtLeast(0),
            animationSpec = if (reduce) snap() else Motion.spatialBouncy(),
            label = "segmentOffset",
        )
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segment)
                    .fillMaxHeight()
                    .glowShadow(SynxioShape.pill, colors.brandEnd, elevation = 8.dp)
                    .clip(SynxioShape.pill)
                    .background(colors.brandBrushHorizontal)
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .selectableGroup()
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val content by animateColorAsState(
                    if (selected) colors.onBrand else scheme.onSurface,
                    Motion.standardOrSnap(reduce, Motion.SHORT),
                    label = "segmentContent",
                )
                val description = descriptions.getOrElse(index) { label }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(SynxioShape.pill)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .semantics { if (description != label) contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Space.xs),
                    )
                }
            }
        }
    }
}

/**
 * Choix du thème visuel : une pastille par preset, coupée en diagonale entre son fond et
 * son accent (dans le mode clair ou sombre courant), pour juger d'un coup d'œil. Le
 * preset choisi grandit légèrement et s'entoure d'un anneau à la signature ; sa
 * description apparaît en fondu dessous.
 */
@Composable
private fun ThemePresetPicker(
    selected: ThemePreset,
    amoled: Boolean,
    onSelect: (ThemePreset) -> Unit,
) {
    val presets = ThemePreset.entries
    val selectedIndex = presets.indexOf(selected)
    val reduce = LocalReduceMotion.current
    // Ouvre la rangée sur le preset actif plutôt que toujours sur le premier.
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.md)
    ) {
        Box(Modifier.padding(horizontal = Space.md)) {
            ChoiceTitle("Thème visuel", Icons.Rounded.Palette)
        }
        Spacer(Modifier.height(Space.sm))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            itemsIndexed(presets, key = { _, p -> p.name }) { _, preset ->
                PresetSwatch(
                    preset = preset,
                    selected = preset == selected,
                    amoled = amoled,
                    onClick = { onSelect(preset) },
                )
            }
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(Motion.enter(if (reduce) Motion.XS else Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.XS))
            },
            label = "presetDescription",
        ) { preset ->
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = RowStartIndent, end = Space.md, top = Space.xs),
            )
        }
    }
}

@Composable
private fun PresetSwatch(
    preset: ThemePreset,
    selected: Boolean,
    amoled: Boolean,
    onClick: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val dark = isDarkSurface
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    // Couleurs réelles du preset dans le mode courant : ce qu'on verrait en le choisissant.
    val preview = remember(preset, dark, amoled) { preset.scheme(dark, amoled) }
    val split = remember(preview) {
        Brush.linearGradient(
            0f to preview.background,
            0.5f to preview.background,
            0.5f to preview.primary,
            1f to preview.primary,
        )
    }
    val ring by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) snap() else Motion.spatialBouncy(),
        label = "presetRing",
    )
    val labelColor by animateColorAsState(
        if (selected) scheme.primary else scheme.onSurfaceVariant,
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "presetLabel",
    )
    val brand = colors.brandBrush
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(SynxioShape.cardSm)
            .pressBounce(0.92f)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .drawBehind {
                    if (ring > 0f) {
                        val w = 2.5.dp.toPx()
                        drawCircle(
                            brush = brand,
                            radius = size.minDimension / 2f - w / 2f,
                            style = Stroke(width = w),
                            alpha = ring.coerceIn(0f, 1f),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        // Le preset choisi s'avance un peu : l'anneau ne porte pas seul l'état.
                        val s = 0.9f + 0.1f * ring
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(split)
                    .border(1.dp, scheme.onSurface.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = preview.primary.contrastingOn(),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(preview.primary)
                            .padding(2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            preset.label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Bloc d'avancement d'une analyse : libellé, barre à la signature, action d'arrêt.
 */
@Composable
private fun ProgressBlock(label: String, fraction: Float, actionLabel: String, onAction: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val shown by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        Motion.standardOrSnap(reduce, Motion.MEDIUM),
        label = "analysisProgress",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = RowStartIndent, end = Space.md, top = Space.md, bottom = Space.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.sm))
        BrandProgressBar(progress = shown, height = 6.dp)
        Spacer(Modifier.height(Space.xs))
        TextButton(onClick = onAction, modifier = Modifier.heightIn(min = Space.touchTarget)) { Text(actionLabel) }
    }
}

/**
 * Sheet de paramètres de couleurs.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ColorSettingsSheet(
    currentAccentSource: AccentSource,
    currentPrimary: ThemeColor,
    currentSecondary: ThemeColor,
    onDismiss: () -> Unit,
    onAccentChange: (AccentSource) -> Unit,
    onPrimaryChange: (ThemeColor) -> Unit,
    onSecondaryChange: (ThemeColor) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = SynxioShape.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.xl)
        ) {
            Text("APPARENCE", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = "Couleurs de l'application",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(Modifier.height(Space.lg))

            SheetSubtitle("Source de la couleur d'accent")

            GlassCard(Modifier.fillMaxWidth(), contentPadding = Space.xs) {
                Column(Modifier.selectableGroup()) {
                    AccentSource.entries.forEach { source ->
                        RadioRow(
                            selected = source == currentAccentSource,
                            onClick = { onAccentChange(source) },
                        ) {
                            Column {
                                Text(
                                    text = source.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (source == AccentSource.CUSTOM) {
                                    Text(
                                        text = "Personnalise les couleurs manuellement",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Reveal(visible = currentAccentSource == AccentSource.CUSTOM) {
                Column {
                    Spacer(Modifier.height(Space.xl))
                    SheetSubtitle("Couleur principale")
                    ColorGrid(
                        selected = currentPrimary,
                        onSelect = {
                            onPrimaryChange(it)
                            onAccentChange(AccentSource.CUSTOM)
                        },
                    )
                    Spacer(Modifier.height(Space.lg))
                    SheetSubtitle("Couleur secondaire")
                    ColorGrid(
                        selected = currentSecondary,
                        onSelect = {
                            onSecondaryChange(it)
                            onAccentChange(AccentSource.CUSTOM)
                        },
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))

            BrandButton(
                text = "Terminé",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SheetSubtitle(text: String) {
    Text(
        text = text.uppercase(),
        style = EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = Space.xs, bottom = Space.sm)
            .semantics { heading() },
    )
}

/** Ligne de bouton radio : la ligne entière est sélectionnable, un seul arrêt de focus. */
@Composable
private fun RadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(SynxioShape.cardSm)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.width(Space.md))
        content()
    }
}

@Composable
private fun ColorGrid(selected: ThemeColor, onSelect: (ThemeColor) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        ThemeColor.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { color ->
                    ColorOption(
                        color = color,
                        isSelected = color == selected,
                        onSelect = { onSelect(color) }
                    )
                }
            }
        }
    }
}

/**
 * Option de couleur : un disque plein ; choisi, il s'entoure d'un anneau à la signature
 * et porte une coche lisible sur sa propre couleur.
 */
@Composable
private fun ColorOption(
    color: ThemeColor,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val reduce = LocalReduceMotion.current
    val fill = remember(color) { color.toColor() }
    val brand = SynxioTheme.colors.brandBrush
    val ring by animateFloatAsState(
        if (isSelected) 1f else 0f,
        if (reduce) snap() else Motion.spatialBouncy(),
        label = "colorRing",
    )
    Box(
        modifier = Modifier
            .size(Space.touchTarget + 8.dp)
            .clip(CircleShape)
            .pressBounce(0.9f)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = color.label }
            .drawBehind {
                if (ring > 0f) {
                    val w = 2.5.dp.toPx()
                    drawCircle(brand, radius = size.minDimension / 2f - w / 2f, style = Stroke(w), alpha = ring.coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(Space.touchTarget - 6.dp)
                .graphicsLayer {
                    val s = 0.9f + 0.1f * ring
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = fill.contrastingOn(),
                )
            }
        }
    }
}

/**
 * Sheet de paramètres de taille de texte.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TextSizeSettingsSheet(
    currentSize: TextSize,
    onDismiss: () -> Unit,
    onSizeChange: (TextSize) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = SynxioShape.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.xl)
        ) {
            Text("AFFICHAGE", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = "Taille du texte",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(Modifier.height(Space.sm))

            Text(
                text = "Choisissez la taille qui vous convient",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Space.lg))

            GlassCard(Modifier.fillMaxWidth(), contentPadding = Space.xs) {
                Column(Modifier.selectableGroup()) {
                    TextSize.entries.forEach { size ->
                        RadioRow(
                            selected = size == currentSize,
                            onClick = { onSizeChange(size) },
                        ) {
                            Text(
                                text = size.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = Space.sm)
                            )
                            // Aperçu de la taille
                            Text(
                                text = "Aperçu ${size.label}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * size.scale
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))

            BrandButton(
                text = "Terminé",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** État de l'analyse d'un lot de titres, pour animer le passage d'un bloc à l'autre. */
private enum class AnalysisStage { RUNNING, PENDING, DONE }

/**
 * Normalisation du volume : interrupteur, niveau cible et avancement de l'analyse.
 *
 * Regroupés dans un seul bloc plutôt qu'en trois réglages distincts : ils n'ont aucun
 * sens séparément, et l'analyse n'est pas une option mais la condition pour que
 * l'interrupteur produise un effet.
 */
@Composable
private fun NormalizationSetting(
    enabled: Boolean,
    targetDbfs: Float,
    progress: LoudnessProgress,
    pendingCount: Int,
    onToggle: (Boolean) -> Unit,
    onTarget: (Float) -> Unit,
    onAnalyse: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
) {
    Column {
        SwitchSetting(
            title = "Normaliser le volume",
            subtitle = "Aligne le niveau sonore des morceaux entre eux",
            icon = Icons.Rounded.GraphicEq,
            checked = enabled,
            onCheckedChange = onToggle,
        )

        Reveal(visible = enabled) {
            Column {
                GroupDivider()
                val stage = when {
                    progress.running -> AnalysisStage.RUNNING
                    pendingCount > 0 -> AnalysisStage.PENDING
                    else -> AnalysisStage.DONE
                }
                AnalysisCrossfade(stage) { s ->
                    when (s) {
                        AnalysisStage.RUNNING -> ProgressBlock(
                            label = "Analyse des fichiers · ${progress.done} / ${progress.total}",
                            fraction = progress.fraction,
                            actionLabel = "Arrêter l'analyse",
                            onAction = onCancel,
                        )
                        AnalysisStage.PENDING -> ClickableSetting(
                            title = "Analyser $pendingCount titre${if (pendingCount > 1) "s" else ""}",
                            subtitle = "Mesure nécessaire pour aligner leur volume",
                            icon = Icons.Rounded.Equalizer,
                            onClick = onAnalyse,
                        )
                        AnalysisStage.DONE -> ClickableSetting(
                            title = "Tous les titres sont mesurés",
                            subtitle = "Toucher pour tout remesurer",
                            icon = Icons.Rounded.Equalizer,
                            onClick = onReset,
                        )
                    }
                }
                GroupDivider()
                SliderSetting(
                    title = "Niveau cible",
                    subtitle = "Plus bas = plus de morceaux alignés, volume général plus faible",
                    value = targetDbfs,
                    valueRange = -24f..-6f,
                    steps = 17,
                    display = { "${it.toInt()} dB" },
                    onChange = onTarget,
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                )
            }
        }
    }
}

/** Fondu enchaîné entre les états d'une analyse (en cours, à faire, faite). */
@Composable
private fun AnalysisCrossfade(stage: AnalysisStage, content: @Composable (AnalysisStage) -> Unit) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            fadeIn(Motion.enter(if (reduce) Motion.XS else Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.XS))
        },
        label = "analysisStage",
    ) { content(it) }
}

/**
 * Mises à jour depuis les releases GitHub.
 *
 * La section ne s'affiche que si un dépôt de publication est configuré à la compilation :
 * sans lui, proposer « Rechercher une mise à jour » serait un bouton qui ne peut pas
 * aboutir. Elle porte donc elle-même son groupe, pour ne pas laisser une carte vide.
 */
@Composable
private fun UpdateSection(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    if (!viewModel.isConfigured) return

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Installation et autorisation système passent toutes deux par un intent : c'est
    // Android qui affiche la confirmation finale, jamais l'application.
    LaunchedEffect(state.pendingIntent) {
        state.pendingIntent?.let {
            runCatching { context.startActivity(it) }
            viewModel.consumeIntent()
        }
    }

    // Le message remonte au bandeau global : cet écran n'a pas de `Scaffold`, donc pas
    // d'emplacement correct pour un `SnackbarHost` local.
    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    SettingsGroup(title = "Mises à jour", modifier = modifier) {
        val release = state.available
        when {
            state.downloading -> {
                ProgressBlock(
                    label = "Téléchargement · ${(state.progress * 100).toInt()} %",
                    fraction = state.progress,
                    actionLabel = "Annuler",
                    onAction = viewModel::cancel,
                )
            }

            release != null -> {
                ClickableSetting(
                    title = "Mettre à jour vers ${release.versionName}",
                    subtitle = buildString {
                        append("Publiée le ${release.publishedAt}")
                        if (release.sizeBytes > 0) {
                            append(" · ${release.sizeBytes.asFileSize()}")
                        }
                        if (release.notes.isNotBlank()) {
                            append("\n${release.notes.lineSequence().first()}")
                        }
                    },
                    icon = Icons.Rounded.Backup,
                    onClick = viewModel::downloadAndInstall,
                )
            }

            else -> {
                ClickableSetting(
                    title = "Rechercher une mise à jour",
                    subtitle = if (state.checking) "Vérification…"
                    else "Version installée : ${state.currentVersion}",
                    icon = Icons.Rounded.Sync,
                    onClick = viewModel::checkNow,
                )
            }
        }
    }
}

/**
 * Publication du morceau en cours dans un salon Discord.
 *
 * Le « Écoute… » du profil Discord n'est pas atteignable depuis Android sans automatiser
 * le compte de l'utilisateur, ce que Discord interdit sous peine de suppression. Un
 * webhook est le seul canal officiel accessible à une application tierce.
 */
@Composable
private fun DiscordSection(onMessage: (String) -> Unit) {
    val viewModel: DiscordViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    Column {
        SwitchSetting(
            title = "Statut d'activité Discord",
            subtitle = state.presenceSubtitle,
            icon = Icons.Rounded.Person,
            checked = state.presenceEnabled,
            onCheckedChange = viewModel::setPresenceEnabled,
        )
        GroupDivider()
        SwitchSetting(
            title = "Annoncer sur Discord",
            subtitle = "Publie chaque morceau dans un salon via un webhook",
            icon = Icons.Rounded.Forum,
            checked = state.enabled,
            onCheckedChange = viewModel::setEnabled,
        )
        GroupDivider()
        ClickableSetting(
            title = if (state.webhookUrl.isBlank()) "Configurer le webhook"
            else "Webhook configuré",
            subtitle = if (state.webhookUrl.isBlank())
                "Salon Discord → Paramètres → Intégrations → Webhooks"
            else state.maskedUrl,
            icon = Icons.Rounded.Link,
            onClick = { editing = true },
        )

        Reveal(visible = state.webhookUrl.isNotBlank()) {
            Column {
                GroupDivider()
                ClickableSetting(
                    title = if (state.testing) "Envoi…" else "Envoyer un message de test",
                    subtitle = "Vérifie que Synxio peut écrire dans le salon",
                    icon = Icons.AutoMirrored.Rounded.Send,
                    onClick = viewModel::sendTest,
                )
            }
        }
    }

    if (editing) {
        var draft by remember { mutableStateOf(state.webhookUrl) }
        SynxioDialog(onDismiss = { editing = false }) {
            Text(
                "Webhook Discord",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.md))
            Text(
                text = "Dans Discord : clic droit sur le salon → Modifier le salon → " +
                    "Intégrations → Webhooks → Nouveau webhook → Copier l'URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.lg))
            // Champ vide = suppression du webhook, donc accepté ; sinon l'URL doit être un
            // vrai webhook Discord, sans quoi on n'enregistre rien.
            val invalid = draft.isNotBlank() && !DiscordWebhookUrl.isValid(draft)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("URL du webhook") },
                singleLine = true,
                isError = invalid,
                shape = SynxioShape.cardSm,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                supportingText = if (invalid) {
                    { Text("Adresse attendue : https://discord.com/api/webhooks/…") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = "Cette URL permet d'écrire dans le salon : garde-la pour toi.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { editing = false }) { Text("Annuler") }
                BrandButton(
                    text = "Enregistrer",
                    enabled = !invalid,
                    onClick = {
                        viewModel.setUrl(draft.trim())
                        editing = false
                    },
                )
            }
        }
    }
}

/**
 * Analyse du son pour la fonction « radio ».
 *
 * Présentée comme une action et non comme un interrupteur : il n'y a rien à activer, il
 * y a un travail à faire une fois. Tant qu'il n'est pas fait, « Lancer une radio » ne
 * peut rien proposer.
 */
@Composable
private fun FingerprintSetting(
    progress: AnalysisProgress,
    pendingCount: Int,
    onAnalyse: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
) {
    val stage = when {
        progress.running -> AnalysisStage.RUNNING
        pendingCount > 0 -> AnalysisStage.PENDING
        else -> AnalysisStage.DONE
    }
    AnalysisCrossfade(stage) { s ->
        when (s) {
            AnalysisStage.RUNNING -> ProgressBlock(
                label = "Écoute de ta bibliothèque · ${progress.done} / ${progress.total}",
                fraction = progress.fraction,
                actionLabel = "Arrêter",
                onAction = onCancel,
            )
            AnalysisStage.PENDING -> ClickableSetting(
                title = "Analyser le son de $pendingCount titre${if (pendingCount > 1) "s" else ""}",
                subtitle = "Nécessaire pour proposer des morceaux qui se ressemblent",
                icon = Icons.Rounded.Radio,
                onClick = onAnalyse,
            )
            AnalysisStage.DONE -> ClickableSetting(
                title = "Tous les titres sont analysés",
                subtitle = "« Lancer une radio » est disponible dans le menu d'un titre",
                icon = Icons.Rounded.Radio,
                onClick = onReset,
            )
        }
    }
}
