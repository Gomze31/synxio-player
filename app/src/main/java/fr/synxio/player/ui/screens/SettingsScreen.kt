package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.BuildConfig
import fr.synxio.player.R
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
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.ThemePreset
import fr.synxio.player.ui.components.SynxioDialog
import fr.synxio.player.ui.viewmodel.DiscordViewModel
import fr.synxio.player.ui.viewmodel.SettingsViewModel
import fr.synxio.player.ui.viewmodel.UpdateViewModel

// Fonctions utilitaires pour convertir String en enum
private fun String.toThemeColor(): ThemeColor = ThemeColor.entries.firstOrNull { it.value == this } ?: ThemeColor.DEFAULT
private fun String.toTextSize(): TextSize = TextSize.entries.firstOrNull { it.name == this } ?: TextSize.NORMAL
private fun String.toAlbumSort(): AlbumSort = AlbumSort.entries.firstOrNull { it.name == this } ?: AlbumSort.TITLE
private fun String.toSongSort(): SongSort = SongSort.entries.firstOrNull { it.name == this } ?: SongSort.TITLE

/**
 * Catégories de sous-menus des paramètres.
 */
enum class SettingsSubMenu(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun SubMenuCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xs),
        shape = SubMenuShape,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(IconTileShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(Space.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(Space.sm))

            // Chevron décoratif : la carte entière est déjà annoncée comme un bouton.
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(180f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * En-tête du hub : identité et état de la bibliothèque, sans raccourcis.
 *
 * Les puces rapides d'avant répétaient mot pour mot la liste des sous-menus juste en
 * dessous : deux chemins vers la même chose, et autant de bruit à lire.
 */
@Composable
private fun SettingsHeroBanner(
    songCount: Int,
    albumCount: Int,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Space.lg),
        shape = SynxioShape.card,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.width(Space.lg))

            Column {
                Text(
                    text = "Synxio Player",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} • ${songCount.pluralSongs()} • $albumCount albums",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Écran des paramètres complet.
 * Menu repensé avec sous-menus thématiques et nouvelles fonctionnalités.
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

    var showColorSheet by remember { mutableStateOf(false) }
    var showTextSizeSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = currentSubMenu != null) {
        currentSubMenu = null
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            colors = synxioTopBarColors(),
            title = {
                Text(
                    text = currentSubMenu?.title ?: "Paramètres",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (currentSubMenu != null) {
                    IconButton(onClick = { currentSubMenu = null }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour aux paramètres"
                        )
                    }
                }
            },
            actions = {
                if (currentSubMenu == null) {
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Égaliseur rapide")
                    }
                }
            }
        )
        
        // Contenu
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.xxl)
        ) {
            if (currentSubMenu == null) {
                // ====================================================================
                // HUB PRINCIPAL : SOUS-MENUS CLIQUABLES
                // ====================================================================
                item {
                    SettingsHeroBanner(
                        songCount = library.songs.size,
                        albumCount = library.albums.size,
                    )
                }

                items(SettingsSubMenu.entries) { subMenu ->
                    SubMenuCard(
                        title = subMenu.title,
                        subtitle = subMenu.getSummary(settings, library),
                        icon = subMenu.icon,
                        onClick = { currentSubMenu = subMenu }
                    )
                }
            } else {
                when (currentSubMenu) {
                    // ================================================================
                    // SOUS-MENU : APPARENCE
                    // ================================================================
                    SettingsSubMenu.APPEARANCE -> {
                        item {
                            ChipSetting(
                                title = "Mode de thème",
                                options = ThemeMode.entries.map { it.label },
                                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                                onSelect = { settingsViewModel.setThemeMode(ThemeMode.entries[it]) }
                            )
                        }
                        
                        item {
                            ChipSetting(
                                title = "Thème visuel",
                                options = ThemePreset.entries.map { it.label },
                                selectedIndex = ThemePreset.entries.indexOf(
                                    ThemePreset.fromName(settings.themePreset)
                                ),
                                onSelect = { settingsViewModel.setThemePreset(ThemePreset.entries[it]) },
                            )
                        }

                        item {
                            Text(
                                text = ThemePreset.fromName(settings.themePreset).description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                            )
                        }

                        item {
                            ClickableSetting(
                                title = "Couleurs de l'application",
                                subtitle = settings.accentSource.label,
                                icon = Icons.Rounded.ColorLens,
                                onClick = { showColorSheet = true }
                            )
                        }

                        item {
                            ChipSetting(
                                title = "Style du lecteur",
                                options = NowPlayingSkin.entries.map { it.label },
                                selectedIndex = NowPlayingSkin.entries.indexOf(settings.nowPlayingSkin),
                                onSelect = { settingsViewModel.setSkin(NowPlayingSkin.entries[it]) }
                            )
                        }
                        
                        item {
                            SwitchSetting(
                                title = "Fond flouté",
                                subtitle = "Floute la pochette derrière le lecteur plein écran",
                                icon = Icons.Rounded.BlurOn,
                                checked = settings.blurBackground,
                                onCheckedChange = settingsViewModel::setBlurBackground
                            )
                        }

                        item {
                            SwitchSetting(
                                title = "Visualiseur audio",
                                subtitle = "Affiche l'indicateur d'ondes sonores dans le lecteur",
                                icon = Icons.Rounded.GraphicEq,
                                checked = settings.showVisualizer,
                                onCheckedChange = settingsViewModel::setShowVisualizer
                            )
                        }

                        item {
                            SwitchSetting(
                                title = "Maintenir l'écran allumé",
                                subtitle = "Garde l'écran actif tant que le lecteur plein écran est affiché",
                                icon = Icons.Rounded.SmartDisplay,
                                checked = settings.keepScreenOnNowPlaying,
                                onCheckedChange = settingsViewModel::setKeepScreenOnNowPlaying
                            )
                        }
                        
                        item {
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
                        }
                        
                        item {
                            ChipSetting(
                                title = "Taille du texte",
                                options = TextSize.entries.map { entry -> entry.label },
                                selectedIndex = TextSize.entries.indexOf(settings.textSize.toTextSize()),
                                onSelect = { settingsViewModel.setTextSize(TextSize.entries[it]) }
                            )
                        }

                        item {
                            SwitchSetting(
                                title = "Réduire les animations",
                                subtitle = "Désactive certaines animations pour améliorer les performances",
                                icon = Icons.Rounded.DataSaverOn,
                                checked = settings.reduceAnimations,
                                onCheckedChange = settingsViewModel::setReduceAnimations
                            )
                        }

                        // Le lanceur doit accepter l'épinglage : sinon, on explique le
                        // geste manuel plutôt que de proposer un bouton sans effet.
                        item {
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
                        }
                        item {
                            val context = LocalContext.current
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
                        item {
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
                        }
                        
                        item {
                            SwitchSetting(
                                title = "Enchaînement gapless",
                                subtitle = "Aucun silence entre deux pistes d'un même album",
                                icon = Icons.Rounded.Loop,
                                checked = settings.gaplessEnabled,
                                onCheckedChange = settingsViewModel::setGapless
                            )
                        }
                        
                        item {
                            SwitchSetting(
                                title = "Ignorer les silences",
                                subtitle = "Saute les blancs en début et fin de piste",
                                icon = Icons.Rounded.Loop,
                                checked = settings.skipSilence,
                                onCheckedChange = settingsViewModel::setSkipSilence
                            )
                        }

                        item {
                            SwitchSetting(
                                title = "Auto-DJ",
                                subtitle = "Prolonge la file avec des titres similaires quand elle se termine",
                                icon = Icons.Rounded.Loop,
                                checked = settings.autoDjEnabled,
                                onCheckedChange = settingsViewModel::setAutoDj
                            )
                        }

                        item {
                            val rewindOptions = listOf("Désactivé", "3 secondes", "5 secondes")
                            val rewindValues = listOf(0, 3, 5)
                            val selectedIndex = rewindValues.indexOf(settings.autoRewindSec).coerceAtLeast(0)
                            ChipSetting(
                                title = "Rembobinage automatique à la reprise",
                                options = rewindOptions,
                                selectedIndex = selectedIndex,
                                onSelect = { settingsViewModel.setAutoRewindSec(rewindValues[it]) },
                                icon = Icons.Rounded.Restore
                            )
                        }

                        item {
                            SwitchSetting(
                                title = "Aléatoire sans les titres zappés",
                                subtitle = "Écarte ceux que tu coupes systématiquement",
                                icon = Icons.Rounded.Shuffle,
                                checked = settings.shuffleSkipsDisliked,
                                onCheckedChange = settingsViewModel::setShuffleSkipsDisliked
                            )
                        }
                        
                        item {
                            SwitchSetting(
                                title = "Reprendre la file au démarrage",
                                subtitle = "Retrouve ta file d'attente là où tu l'avais laissée",
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                checked = settings.rememberQueue,
                                onCheckedChange = settingsViewModel::setRememberQueue
                            )
                        }
                        
                        item {
                            SwitchSetting(
                                title = "Reprendre au branchement du casque",
                                subtitle = "La lecture repart quand tu connectes des écouteurs, filaires ou Bluetooth",
                                icon = Icons.Rounded.PhonelinkSetup,
                                checked = settings.resumeOnHeadsetConnect,
                                onCheckedChange = settingsViewModel::setResumeOnHeadsetConnect
                            )
                        }
                        
                        item {
                            SwitchSetting(
                                title = "Arrêter après inactivité",
                                subtitle = "Arrête la lecture après une période d'inactivité",
                                icon = Icons.Rounded.Timer,
                                checked = settings.stopAfterInactivityMin > 0,
                                onCheckedChange = { 
                                    settingsViewModel.setStopAfterInactivity(if (it) 30 else 0)
                                }
                            )
                        }
                        
                        if (settings.stopAfterInactivityMin > 0) {
                            item {
                                SliderSetting(
                                    title = "Durée d'inactivité",
                                    subtitle = "Temps avant l'arrêt automatique",
                                    icon = Icons.Rounded.Timer,
                                    value = settings.stopAfterInactivityMin.toFloat(),
                                    valueRange = 5f..120f,
                                    steps = 22,
                                    display = { "${it.toInt()} min" },
                                    onChange = { settingsViewModel.setStopAfterInactivity(it.toInt()) }
                                )
                            }
                        }

                        item {
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
                        item {
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

                        item {
                            SwitchSetting(
                                title = "Limiter le volume",
                                subtitle = "Empêche de dépasser un volume maximum pour protéger l'audition",
                                icon = Icons.Rounded.Speaker,
                                checked = settings.maxVolumeLimit,
                                onCheckedChange = settingsViewModel::setMaxVolumeLimit
                            )
                        }
                        
                        if (settings.maxVolumeLimit) {
                            item {
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

                    // ================================================================
                    // SOUS-MENU : BIBLIOTHÈQUE & FICHIERS
                    // ================================================================
                    SettingsSubMenu.LIBRARY -> {
                        item {
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
                        }
                        
                        item {
                            ChipSetting(
                                title = "Onglet par défaut",
                                options = LibraryTab.entries.map { it.label },
                                selectedIndex = LibraryTab.entries.indexOf(settings.defaultTab),
                                onSelect = { settingsViewModel.setDefaultTab(LibraryTab.entries[it]) }
                            )
                        }
                        
                        item {
                            ClickableSetting(
                                title = "Relancer le scan",
                                subtitle = "${library.songs.size.pluralSongs()} · ${library.totalDurationMs.asLongDuration()}",
                                icon = Icons.Rounded.Sync,
                                onClick = { viewModel.rescan() }
                            )
                        }

                        item {
                            ClickableSetting(
                                title = "Réparer les tags",
                                subtitle = "Corrige artistes, albums, années et pochettes en masse",
                                icon = Icons.Rounded.AutoFixHigh,
                                onClick = onOpenRepair
                            )
                        }

                        item {
                            ClickableSetting(
                                title = "Supprimer les fichiers en double",
                                subtitle = "Analyse tes fichiers et libère de l'espace de stockage",
                                icon = Icons.Rounded.ContentCopy,
                                onClick = onOpenDuplicates
                            )
                        }

                        item {
                            ClickableSetting(
                                title = "Recherche avancée",
                                subtitle = "Filtres par artiste, album, genre, durée, etc.",
                                icon = Icons.Rounded.SmartDisplay,
                                onClick = onNavigateToAdvancedSearch
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : ÉGALISEUR & EFFETS
                    // ================================================================
                    SettingsSubMenu.EQUALIZER -> {
                        item {
                            ClickableSetting(
                                title = "Ouvrir l'égaliseur complet",
                                subtitle = if (settings.equalizerEnabled) "Actif • Personnaliser les bandes et presets" else "Désactivé • Appuyer pour ouvrir",
                                icon = Icons.Rounded.Equalizer,
                                onClick = onOpenEqualizer
                            )
                        }

                        item {
                            Text(
                                text = "Synxio s'interface directement avec le moteur audio système d'Android pour offrir le meilleur compromis latence et clarté acoustique.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // ================================================================
                    // SOUS-MENU : PAROLES & CONNEXIONS
                    // ================================================================
                    SettingsSubMenu.LYRICS_AND_SOCIAL -> {
                        item {
                            SwitchSetting(
                                title = "Chercher les paroles en ligne",
                                subtitle = "Utilise LRCLIB quand aucun fichier .lrc n'est trouvé",
                                icon = Icons.Rounded.Language,
                                checked = settings.lyricsOnlineEnabled,
                                onCheckedChange = settingsViewModel::setLyricsOnline
                            )
                        }
                        
                        if (lastFmAvailable) {
                            item {
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

                        item { DiscordSection(onMessage = viewModel::showMessage) }
                    }

                    // ================================================================
                    // SOUS-MENU : STATISTIQUES & HISTORIQUE
                    // ================================================================
                    SettingsSubMenu.STATS_AND_HISTORY -> {
                        item {
                            ClickableSetting(
                                title = "Mes statistiques",
                                subtitle = "Top artistes, albums, et plus encore",
                                icon = Icons.Rounded.Star,
                                onClick = onNavigateToStats
                            )
                        }
                        
                        item {
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
                        item {
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
                        item { UpdateSection(onMessage = viewModel::showMessage) }

                        item {
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

                        item {
                            ClickableSetting(
                                title = "À propos de Synxio",
                                subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) - Lecteur local, sans pub, open source",
                                icon = Icons.Rounded.Info,
                                onClick = onNavigateToAbout
                            )
                        }
                    }

                    null -> Unit
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
// BRIQUES PARTAGÉES
// ============================================================================

/** Hauteur minimale d'une ligne de réglage (liste Material à deux lignes). */
private val RowMinHeight = 56.dp

/** Pastille d'icône des lignes : taille et arrondi identiques sur tout l'écran. */
private val IconTileSize = 36.dp
private val IconTileShape = RoundedCornerShape(Radius.sm)

/** Retrait des contrôles placés sous un titre précédé d'une icône. */
private val IconIndent = IconTileSize + Space.md

private val SubMenuShape = RoundedCornerShape(Radius.md)

/** Marges d'une ligne de réglage. */
private val RowPadding = PaddingValues(horizontal = Space.xl, vertical = Space.md)

/**
 * Pastille d'icône décorative en tête de ligne, suivie de son espacement.
 */
@Composable
private fun SettingIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = Modifier
            .size(IconTileSize)
            .clip(IconTileShape)
            .background(container),
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
            style = MaterialTheme.typography.bodyLarge
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * En-tête de section des paramètres.
 */
@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm)
            // Annoncé comme titre : TalkBack permet de sauter de section en section.
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIconTile(
            icon = icon,
            container = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Paramètre avec interrupteur.
 *
 * Toute la ligne porte l'état (`toggleable`, rôle Switch) et l'interrupteur n'a pas de
 * gestionnaire propre : un seul arrêt de focus, annoncé « interrupteur, activé ».
 */
@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
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
            onCheckedChange = null
        )
    }
}

/**
 * Paramètre cliquable.
 */
@Composable
private fun ClickableSetting(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) SettingIconTile(icon)

        SettingTexts(title, subtitle, Modifier.weight(1f))

        // Décoratif : le titre de la ligne dit déjà ce que fait l'appui.
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
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
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.sm)
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
                    Text(
                        text = display(value),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (subtitle != null) {
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
            modifier = Modifier
                .padding(start = if (icon != null) IconIndent else 0.dp)
                // TalkBack lit la valeur formatée (« 30 min ») plutôt qu'un pourcentage brut.
                .semantics { stateDescription = display(value) }
        )
    }
}

/**
 * Paramètre avec puces de sélection.
 */
@Composable
private fun ChipSetting(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) SettingIconTile(icon)

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(Space.sm))

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (icon != null) IconIndent else 0.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            options.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    label = { Text(label) }
                )
            }
        }
    }
}

/**
 * Sheet de paramètres de couleurs.
 */
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
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Couleurs de l'application",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            
            Spacer(Modifier.height(20.dp))
            
            Text(
                text = "Source de la couleur d'accent",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            
            Spacer(Modifier.height(8.dp))
            
            Column(Modifier.selectableGroup()) {
            AccentSource.entries.forEach { source ->
                // Ligne entière sélectionnable : un seul arrêt de focus, annoncé « bouton radio ».
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowMinHeight)
                        .selectable(
                            selected = source == currentAccentSource,
                            role = Role.RadioButton,
                            onClick = { onAccentChange(source) },
                        )
                        .padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = source == currentAccentSource,
                        onClick = null
                    )
                    Spacer(Modifier.width(Space.md))
                    Column {
                        Text(
                            text = source.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
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

            if (currentAccentSource == AccentSource.CUSTOM) {
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "Couleur principale",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                
                Spacer(Modifier.height(12.dp))
                
                ThemeColor.entries.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { color ->
                            ColorOption(
                                color = color,
                                isSelected = color == currentPrimary,
                                onSelect = { 
                                    onPrimaryChange(color)
                                    onAccentChange(AccentSource.CUSTOM)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "Couleur secondaire",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                
                Spacer(Modifier.height(12.dp))
                
                ThemeColor.entries.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { color ->
                            ColorOption(
                                color = color,
                                isSelected = color == currentSecondary,
                                onSelect = { 
                                    onSecondaryChange(color)
                                    onAccentChange(AccentSource.CUSTOM)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                Spacer(Modifier.height(24.dp))
            }
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Terminé")
            }
        }
    }
}

/**
 * Option de couleur.
 */
@Composable
private fun ColorOption(
    color: ThemeColor,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Space.touchTarget)
            .clip(CircleShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = color.label }
            .background(color.toColor()),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color.toColor()
                )
            }
        }
    }
}

/**
 * Sheet de paramètres de taille de texte.
 */
@Composable
private fun TextSizeSettingsSheet(
    currentSize: TextSize,
    onDismiss: () -> Unit,
    onSizeChange: (TextSize) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Taille du texte",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            
            Spacer(Modifier.height(20.dp))
            
            Text(
                text = "Choisissez la taille qui vous convient",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(16.dp))
            
            Column(Modifier.selectableGroup()) {
            TextSize.entries.forEach { size ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowMinHeight)
                        .selectable(
                            selected = size == currentSize,
                            role = Role.RadioButton,
                            onClick = { onSizeChange(size) },
                        )
                        .padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = size == currentSize,
                        onClick = null
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        text = size.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    // Aperçu de la taille
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Aperçu ${size.label}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * size.scale
                            )
                        )
                    }
                }
            }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Terminé")
            }
        }
    }
}

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

        if (!enabled) return@Column

        when {
            progress.running -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = "Analyse des fichiers · ${progress.done} / ${progress.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) { Text("Arrêter l'analyse") }
                }
            }

            pendingCount > 0 -> {
                ClickableSetting(
                    title = "Analyser $pendingCount titre${if (pendingCount > 1) "s" else ""}",
                    subtitle = "Mesure nécessaire pour aligner leur volume",
                    icon = Icons.Rounded.Equalizer,
                    onClick = onAnalyse,
                )
            }

            else -> {
                ClickableSetting(
                    title = "Tous les titres sont mesurés",
                    subtitle = "Toucher pour tout remesurer",
                    icon = Icons.Rounded.Equalizer,
                    onClick = onReset,
                )
            }
        }

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

/**
 * Mises à jour depuis les releases GitHub.
 *
 * La section ne s'affiche que si un dépôt de publication est configuré à la compilation :
 * sans lui, proposer « Rechercher une mise à jour » serait un bouton qui ne peut pas
 * aboutir.
 */
@Composable
private fun UpdateSection(onMessage: (String) -> Unit) {
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

    Column {
        val release = state.available
        when {
            state.downloading -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = "Téléchargement · ${(state.progress * 100).toInt()} %",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = viewModel::cancel) { Text("Annuler") }
                }
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

        SwitchSetting(
            title = "Annoncer sur Discord",
            subtitle = "Publie chaque morceau dans un salon via un webhook",
            icon = Icons.Rounded.Forum,
            checked = state.enabled,
            onCheckedChange = viewModel::setEnabled,
        )

        ClickableSetting(
            title = if (state.webhookUrl.isBlank()) "Configurer le webhook"
            else "Webhook configuré",
            subtitle = if (state.webhookUrl.isBlank())
                "Salon Discord → Paramètres → Intégrations → Webhooks"
            else state.maskedUrl,
            icon = Icons.Rounded.Link,
            onClick = { editing = true },
        )

        if (state.webhookUrl.isNotBlank()) {
            ClickableSetting(
                title = if (state.testing) "Envoi…" else "Envoyer un message de test",
                subtitle = "Vérifie que Synxio peut écrire dans le salon",
                icon = Icons.AutoMirrored.Rounded.Send,
                onClick = viewModel::sendTest,
            )
        }
    }

    if (editing) {
        var draft by remember { mutableStateOf(state.webhookUrl) }
        SynxioDialog(onDismiss = { editing = false }) {
            Text(
                "Webhook Discord",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Dans Discord : clic droit sur le salon → Modifier le salon → " +
                    "Intégrations → Webhooks → Nouveau webhook → Copier l'URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            // Champ vide = suppression du webhook, donc accepté ; sinon l'URL doit être un
            // vrai webhook Discord, sans quoi on n'enregistre rien.
            val invalid = draft.isNotBlank() && !DiscordWebhookUrl.isValid(draft)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("URL du webhook") },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    { Text("Adresse attendue : https://discord.com/api/webhooks/…") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Cette URL permet d'écrire dans le salon : garde-la pour toi.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editing = false }) { Text("Annuler") }
                Button(
                    enabled = !invalid,
                    onClick = {
                        viewModel.setUrl(draft.trim())
                        editing = false
                    },
                ) { Text("Enregistrer") }
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
    Column {
        when {
            progress.running -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = "Écoute de ta bibliothèque · ${progress.done} / ${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) { Text("Arrêter") }
                }
            }

            pendingCount > 0 -> {
                ClickableSetting(
                    title = "Analyser le son de $pendingCount titre${if (pendingCount > 1) "s" else ""}",
                    subtitle = "Nécessaire pour proposer des morceaux qui se ressemblent",
                    icon = Icons.Rounded.Radio,
                    onClick = onAnalyse,
                )
            }

            else -> {
                ClickableSetting(
                    title = "Tous les titres sont analysés",
                    subtitle = "« Lancer une radio » est disponible dans le menu d'un titre",
                    icon = Icons.Rounded.Radio,
                    onClick = onReset,
                )
            }
        }
    }
}
