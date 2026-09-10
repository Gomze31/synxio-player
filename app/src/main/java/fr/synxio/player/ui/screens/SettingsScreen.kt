package fr.synxio.player.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.SystemSecurityUpdate
import androidx.compose.material.icons.rounded.SmartDisplay
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.BuildConfig
import fr.synxio.player.R
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.AccentSource
import fr.synxio.player.data.model.AlbumSort
import fr.synxio.player.data.model.LibraryTab
import fr.synxio.player.data.model.NowPlayingSkin
import fr.synxio.player.data.model.PlaylistSort
import fr.synxio.player.data.model.SongSort
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.model.TextSize
import fr.synxio.player.data.model.ThemeColor
import fr.synxio.player.data.model.ThemeMode
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.theme.ThemePreset
import fr.synxio.player.ui.viewmodel.SettingsViewModel

// Fonctions utilitaires pour convertir String en enum
private fun String.toThemeColor(): ThemeColor = ThemeColor.entries.firstOrNull { it.value == this } ?: ThemeColor.DEFAULT
private fun String.toTextSize(): TextSize = TextSize.entries.firstOrNull { it.name == this } ?: TextSize.NORMAL
private fun String.toPlaylistSort(): PlaylistSort = PlaylistSort.entries.firstOrNull { it.name == this } ?: PlaylistSort.NAME
private fun String.toAlbumSort(): AlbumSort = AlbumSort.entries.firstOrNull { it.name == this } ?: AlbumSort.TITLE
private fun String.toSongSort(): SongSort = SongSort.entries.firstOrNull { it.name == this } ?: SongSort.TITLE
private fun String.toStatsPeriod(): StatsPeriod = StatsPeriod.entries.firstOrNull { it.name == this } ?: StatsPeriod.ALL_TIME

/**
 * Écran des paramètres complet.
 * Menu repensé avec toutes les nouvelles fonctionnalités pour le Play Store.
 */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onOpenEqualizer: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAdvancedSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val lastFmAvailable = remember { settingsViewModel.lastFmAvailable }
    
    var showThemeSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showTextSizeSheet by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Paramètres") },
            actions = {
                IconButton(
                    onClick = { /* Accès rapide */ }
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Paramètres rapides")
                }
            }
        )
        
        // Contenu
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ========================================================================
            // SECTION : APPARENCE
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Apparence",
                    icon = Icons.Rounded.Palette
                )
            }
            
            item {
                ClickableSetting(
                    title = "Thème",
                    subtitle = "${settings.themeMode.label} - ${settings.accentSource.label}",
                    icon = Icons.Rounded.Brightness6,
                    onClick = { showThemeSheet = true }
                )
            }
            
            item {
                ClickableSetting(
                    title = "Couleurs",
                    subtitle = "Personnalise les couleurs de l'application",
                    icon = Icons.Rounded.ColorLens,
                    onClick = { showColorSheet = true }
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
            
            // ========================================================================
            // SECTION : LECTURE
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Lecture",
                    icon = Icons.Rounded.PlayCircle
                )
            }
            
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
                    title = "Reprendre la file au démarrage",
                    subtitle = "Retrouve ta file d'attente là où tu l'avais laissée",
                    icon = Icons.Rounded.QueueMusic,
                    checked = settings.rememberQueue,
                    onCheckedChange = settingsViewModel::setRememberQueue
                )
            }
            
            item {
                SwitchSetting(
                    title = "Reprendre au branchement du casque",
                    subtitle = "La lecture repart quand tu connectes des écouteurs, filaires ou Bluetooth",
                    icon = Icons.Rounded.VolumeUp,
                    checked = settings.resumeOnHeadsetConnect,
                    onCheckedChange = settingsViewModel::setResumeOnHeadsetConnect
                )
            }

            item {
                SwitchSetting(
                    title = "Reprendre après un appel",
                    subtitle = "La lecture reprend automatiquement après un appel téléphonique",
                    icon = Icons.Rounded.PhonelinkSetup,
                    checked = settings.resumeAfterCall,
                    onCheckedChange = settingsViewModel::setResumeAfterCall
                )
            }
            
            item {
                SwitchSetting(
                    title = "Arrêter après inactivité",
                    subtitle = "Arrête la lecture après une période d'inactivité",
                    icon = Icons.Rounded.Timer,
                    checked = settings.stopAfterInactivityMin > 0,
                    onCheckedChange = { enabled ->
                        settingsViewModel.setStopAfterInactivity(if (enabled) 15 else 0)
                    }
                )
            }
            
            if (settings.stopAfterInactivityMin > 0) {
                item {
                    SliderSetting(
                        title = "Durée d'inactivité",
                        subtitle = "Temps avant l'arrêt automatique",
                        icon = Icons.Rounded.LinearScale,
                        value = settings.stopAfterInactivityMin.toFloat(),
                        valueRange = 5f..60f,
                        steps = 11,
                        display = { "${it.toInt()} min" },
                        onChange = { settingsViewModel.setStopAfterInactivity(it.toInt()) }
                    )
                }
            }
            
            item {
                ClickableSetting(
                    title = "Égaliseur",
                    subtitle = if (settings.equalizerEnabled) "Actif" else "Désactivé",
                    icon = Icons.Rounded.Equalizer,
                    onClick = onOpenEqualizer
                )
            }
            
            // ========================================================================
            // SECTION : VOLUME
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Volume",
                    icon = Icons.Rounded.VolumeUp
                )
            }
            
            item {
                SliderSetting(
                    title = "Volume par défaut",
                    subtitle = "Volume de démarrage de l'application",
                    icon = Icons.Rounded.VolumeUp,
                    value = settings.defaultVolume,
                    valueRange = 0f..1f,
                    steps = 10,
                    display = { "${(it * 100).toInt()}%" },
                    onChange = { settingsViewModel.setDefaultVolume(it) }
                )
            }
            
            item {
                SwitchSetting(
                    title = "Limiter le volume",
                    subtitle = "Empêche de dépasser un volume maximum",
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
            
            // ========================================================================
            // SECTION : BIBLIOTHÈQUE
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Bibliothèque",
                    icon = Icons.Rounded.Album
                )
            }
            
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
                ChipSetting(
                    title = "Tri des playlists",
                    options = PlaylistSort.entries.map { entry -> entry.label },
                    selectedIndex = PlaylistSort.entries.indexOf(settings.defaultPlaylistSort.toPlaylistSort()),
                    onSelect = { settingsViewModel.setDefaultPlaylistSort(PlaylistSort.entries[it]) }
                )
            }
            
            item {
                SwitchSetting(
                    title = "Afficher les albums vides",
                    subtitle = "Affiche les albums même s'ils ne contiennent aucun morceau",
                    icon = Icons.Rounded.Folder,
                    checked = settings.showEmptyAlbums,
                    onCheckedChange = settingsViewModel::setShowEmptyAlbums
                )
            }
            
            item {
                SwitchSetting(
                    title = "Grouper les albums par artiste",
                    subtitle = "Regroupe les albums par artiste dans la bibliothèque",
                    icon = Icons.Rounded.Person,
                    checked = settings.groupAlbumsByArtist,
                    onCheckedChange = settingsViewModel::setGroupAlbumsByArtist
                )
            }
            
            item {
                SwitchSetting(
                    title = "Cacher les doublons",
                    subtitle = "Masque les morceaux en double dans la bibliothèque",
                    icon = Icons.Rounded.DataSaverOn,
                    checked = settings.hideDuplicates,
                    onCheckedChange = settingsViewModel::setHideDuplicates
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
                SwitchSetting(
                    title = "Scan automatique",
                    subtitle = "Démarre automatiquement le scan au démarrage de l'application",
                    icon = Icons.Rounded.SystemSecurityUpdate,
                    checked = settings.autoScan,
                    onCheckedChange = settingsViewModel::setAutoScan
                )
            }
            
            if (settings.autoScan) {
                item {
                    SwitchSetting(
                        title = "Scan en arrière-plan",
                        subtitle = "Permet de continuer le scan même quand l'application est en arrière-plan",
                        icon = Icons.Rounded.DataSaverOn,
                        checked = settings.scanInBackground,
                        onCheckedChange = settingsViewModel::setScanInBackground
                    )
                }
            }
            
            // ========================================================================
            // SECTION : PAROLES ET SCROBBLING
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Paroles et Scrobbling",
                    icon = Icons.Rounded.Language
                )
            }
            
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
            
            // ========================================================================
            // SECTION : RECHERCHE
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Recherche",
                    icon = Icons.Rounded.Search
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
            
            item {
                SwitchSetting(
                    title = "Autoriser les doublons dans la recherche",
                    subtitle = "Affiche les morceaux en double dans les résultats de recherche",
                    icon = Icons.Rounded.FilterList,
                    checked = settings.allowDuplicateSongs,
                    onCheckedChange = settingsViewModel::setAllowDuplicateSongs
                )
            }
            
            // ========================================================================
            // SECTION : STATISTIQUES ET HISTORIQUE
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Statistiques et Historique",
                    icon = Icons.Rounded.History
                )
            }
            
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
            
            item {
                ChipSetting(
                    title = "Période par défaut pour les stats",
                    options = StatsPeriod.entries.map { entry -> entry.label },
                    selectedIndex = StatsPeriod.entries.indexOf(settings.defaultStatsPeriod.toStatsPeriod()),
                    onSelect = { settingsViewModel.setDefaultStatsPeriod(StatsPeriod.entries[it]) }
                )
            }
            
            // ========================================================================
            // SECTION : SAUVEGARDE
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Sauvegarde",
                    icon = Icons.Rounded.Backup
                )
            }
            
            item {
                ClickableSetting(
                    title = "Sauvegarde et restauration",
                    subtitle = "Exporte et importe tes données",
                    icon = Icons.Rounded.Save,
                    onClick = onNavigateToBackup
                )
            }
            
            // ========================================================================
            // SECTION : PERFORMANCES
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "Performances",
                    icon = Icons.Rounded.Speed
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
            
            item {
                SwitchSetting(
                    title = "Mode développeur",
                    subtitle = "Active des options de débogage avancées",
                    icon = Icons.Rounded.DeveloperMode,
                    checked = settings.developerMode,
                    onCheckedChange = settingsViewModel::setDeveloperMode
                )
            }
            
            // ========================================================================
            // SECTION : À PROPOS
            // ========================================================================
            item { 
                SettingsSectionHeader(
                    title = "À propos",
                    icon = Icons.Rounded.Info
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
    }
    
    // Modal Bottom Sheets
    if (showThemeSheet) {
        ThemeSettingsSheet(
            currentThemeMode = settings.themeMode,
            currentAccentSource = settings.accentSource,
            onDismiss = { showThemeSheet = false },
            onThemeChange = settingsViewModel::setThemeMode,
            onAccentChange = settingsViewModel::setAccentSource
        )
    }
    
    if (showColorSheet) {
        ColorSettingsSheet(
            currentPrimary = settings.primaryColor.toThemeColor(),
            currentSecondary = settings.secondaryColor.toThemeColor(),
            onDismiss = { showColorSheet = false },
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

/**
 * En-tête de section des paramètres.
 */
@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
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
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
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
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
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
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
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
        
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = "Accéder",
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
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            
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
                    Spacer(Modifier.width(12.dp))
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
            modifier = Modifier.padding(start = if (icon != null) 48.dp else 0.dp)
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
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (icon != null) 48.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
 * Sheet de paramètres de thème.
 */
@Composable
private fun ThemeSettingsSheet(
    currentThemeMode: ThemeMode,
    currentAccentSource: AccentSource,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentSource) -> Unit
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
                text = "Paramètres de thème",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(20.dp))
            
            Text(
                text = "Mode de thème",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(8.dp))
            
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(mode) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == currentThemeMode,
                        onClick = { onThemeChange(mode) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = "Source de la couleur d'accent",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(8.dp))
            
            AccentSource.entries.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAccentChange(source) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = source == currentAccentSource,
                        onClick = { onAccentChange(source) }
                    )
                    Spacer(Modifier.width(8.dp))
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
 * Sheet de paramètres de couleurs.
 */
@Composable
private fun ColorSettingsSheet(
    currentPrimary: ThemeColor,
    currentSecondary: ThemeColor,
    onDismiss: () -> Unit,
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
                text = "Personnalisation des couleurs",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(20.dp))
            
            Text(
                text = "Couleur principale",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
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
                            onSelect = { onPrimaryChange(color) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = "Couleur secondaire",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
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
                            onSelect = { onSecondaryChange(color) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onSelect)
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
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(20.dp))
            
            Text(
                text = "Choisissez la taille qui vous convient",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(16.dp))
            
            TextSize.entries.forEach { size ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSizeChange(size) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = size == currentSize,
                        onClick = { onSizeChange(size) }
                    )
                    Spacer(Modifier.width(8.dp))
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
