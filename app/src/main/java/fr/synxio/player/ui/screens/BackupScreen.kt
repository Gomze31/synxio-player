package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import androidx.compose.ui.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.repo.BackupRepository
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.BackupViewModel
import fr.synxio.player.ui.viewmodel.BackupViewModel.BackupState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sauvegarde et restauration : export/import complet (JSON), export/import d'une playlist
 * au format M3U8, et remise à zéro des données locales.
 *
 * Les retours passent par la snackbar de l'app ([AppViewModel.showMessage]) ; une barre de
 * progression sous la barre d'app indique qu'une opération tourne, et les actions sont
 * désactivées pendant ce temps pour éviter un double import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val backupViewModel: BackupViewModel = hiltViewModel()
    val context = LocalContext.current

    val backupState by backupViewModel.backupState.collectAsStateWithLifecycle()
    val backupInfo by backupViewModel.backupInfo.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val busy = backupState is BackupState.Checking || backupState is BackupState.Exporting ||
        backupState is BackupState.Importing || backupState is BackupState.Clearing

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showExportPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    // Playlist choisie le temps que le sélecteur de fichier système réponde. On garde son
    // identifiant (sauvegardé) : l'activité peut être recréée pendant que le sélecteur
    // est ouvert, et l'export ne faisait alors plus rien.
    var playlistToExportId by rememberSaveable { mutableStateOf<Long?>(null) }
    var importedM3uUri by remember { mutableStateOf<Uri?>(null) }
    var importPlaylistName by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(backupViewModel::exportData) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(backupViewModel::checkBackupFile) }

    val exportM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val playlist = playlists.firstOrNull { it.id == playlistToExportId }
        if (uri != null && playlist != null) backupViewModel.exportPlaylistToM3u8(playlist, uri)
        playlistToExportId = null
    }

    val importM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Nom proposé = nom du fichier sans extension ; modifiable avant l'import.
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
            importPlaylistName = displayName.substringBeforeLast('.').ifBlank { "Playlist importée" }
            importedM3uUri = uri
        }
    }

    // Chaque issue d'opération devient un message de la snackbar racine, puis l'état revient au repos.
    LaunchedEffect(backupState) {
        val message = when (val state = backupState) {
            is BackupState.ExportSuccess -> state.message
            is BackupState.ExportFailed -> "Échec de l'export : ${state.message}"
            is BackupState.ImportSuccess -> state.message
            is BackupState.ImportFailed -> "Échec de l'import : ${state.message}"
            is BackupState.InvalidFile -> state.message
            is BackupState.ClearSuccess -> state.message
            is BackupState.ClearFailed -> "Échec de l'effacement : ${state.message}"
            else -> null
        }
        if (message != null) {
            viewModel.showMessage(message)
            backupViewModel.resetState()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    colors = synxioTopBarColors(),
                    title = { Text("Sauvegarde et restauration") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Opération en cours" }
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Space.xxl,
            ),
        ) {
            item(key = "export-title") { SectionHeader("Exporter") }
            item(key = "export-all") {
                BackupAction(
                    title = "Exporter toutes les données",
                    description = "Playlists, favoris, historique, statistiques et réglages dans un fichier JSON",
                    icon = Icons.Rounded.Backup,
                    enabled = !busy,
                    onClick = { exportLauncher.launch(backupViewModel.getDefaultBackupFilename()) },
                )
            }
            item(key = "export-m3u") {
                BackupAction(
                    title = "Exporter une playlist (M3U8)",
                    description = if (playlists.isEmpty()) "Crée d'abord une playlist"
                    else "Lisible par VLC, Poweramp, foobar2000…",
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    enabled = !busy && playlists.isNotEmpty(),
                    onClick = { showExportPlaylistDialog = true },
                )
            }

            item(key = "import-title") { SectionHeader("Importer") }
            item(key = "import-all") {
                BackupAction(
                    title = "Restaurer une sauvegarde",
                    description = "Remplace tes données par celles d'un fichier Synxio",
                    icon = Icons.Rounded.Restore,
                    enabled = !busy,
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                )
            }
            item(key = "import-m3u") {
                BackupAction(
                    title = "Importer une playlist (M3U8)",
                    description = "Retrouve les titres dans ta bibliothèque et crée une playlist",
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    enabled = !busy,
                    onClick = {
                        importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "*/*"))
                    },
                )
            }

            item(key = "danger-title") { SectionHeader("Zone sensible") }
            item(key = "clear") {
                BackupAction(
                    title = "Effacer toutes les données",
                    description = "Supprime playlists, favoris, historique, statistiques et réglages. Tes fichiers audio ne sont pas touchés.",
                    icon = Icons.Rounded.DeleteForever,
                    enabled = !busy,
                    destructive = true,
                    onClick = { showClearDialog = true },
                )
            }

            item(key = "about-title") { SectionHeader("Bon à savoir") }
            item(key = "about") { BackupInfoSection() }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text("Effacer toutes les données ?") },
            text = {
                Text(
                    "Tes playlists, favoris, historique, statistiques et réglages seront supprimés. " +
                        "Tes fichiers audio restent sur l'appareil. Cette action est irréversible : " +
                        "exporte une sauvegarde avant si tu veux pouvoir revenir en arrière."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    backupViewModel.clearAllData()
                }) {
                    Text("Effacer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annuler") }
            },
        )
    }

    // Aperçu d'une sauvegarde valide : l'import n'a lieu qu'après confirmation.
    backupInfo?.let { info ->
        BackupInfoDialog(
            info = info,
            onDismiss = backupViewModel::resetState,
            onConfirmImport = backupViewModel::importData,
        )
    }

    if (showExportPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showExportPlaylistDialog = false },
            title = { Text("Quelle playlist exporter ?") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Space.touchTarget)
                                .clip(SynxioShape.pill)
                                .clickable(onClickLabel = "Exporter", role = Role.Button) {
                                    playlistToExportId = playlist.id
                                    showExportPlaylistDialog = false
                                    exportM3uLauncher.launch("${playlist.name.toSafeFileName()}.m3u8")
                                }
                                .padding(horizontal = Space.sm, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    playlist.songCount.pluralSongs(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportPlaylistDialog = false }) { Text("Annuler") }
            },
        )
    }

    if (importedM3uUri != null) {
        AlertDialog(
            onDismissRequest = { importedM3uUri = null },
            title = { Text("Importer la playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(
                        "Les titres seront cherchés dans ta bibliothèque ; ceux qui manquent seront ignorés.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = importPlaylistName,
                        onValueChange = { importPlaylistName = it },
                        label = { Text("Nom de la playlist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importedM3uUri?.let { backupViewModel.importM3u8File(it, importPlaylistName) }
                        importedM3uUri = null
                    },
                    enabled = importPlaylistName.isNotBlank(),
                ) { Text("Importer") }
            },
            dismissButton = {
                TextButton(onClick = { importedM3uUri = null }) { Text("Annuler") }
            },
        )
    }
}

/** Caractères refusés par la plupart des fournisseurs de documents dans un nom de fichier. */
private fun String.toSafeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "playlist" }

/**
 * Action de l'écran : une ligne tonale cliquable. Une action destructive prend la couleur
 * d'erreur sur l'icône et le titre, jamais seulement sur un détail.
 */
@Composable
private fun BackupAction(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = SynxioShape.card,
        color = scheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touchTarget)
                .padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (destructive) scheme.errorContainer else scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (destructive) scheme.onErrorContainer else scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
                        destructive -> scheme.error
                        else -> scheme.onSurface
                    },
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) scheme.onSurfaceVariant else scheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Ce que contient une sauvegarde et où elle va. */
@Composable
private fun BackupInfoSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        InfoItem(Icons.Rounded.MusicNote, "Contenu", "Playlists, favoris, historique, statistiques et réglages. Pas les fichiers audio.")
        InfoItem(Icons.Rounded.Save, "Format", "Fichier JSON propre à Synxio, lisible en texte.")
        InfoItem(Icons.Rounded.Folder, "Emplacement", "Là où tu choisis de l'enregistrer : stockage local, Drive, clé USB…")
    }
}

@Composable
private fun InfoItem(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.md))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Aperçu d'un fichier de sauvegarde avant de remplacer les données. */
@Composable
private fun BackupInfoDialog(
    info: BackupRepository.BackupInfo,
    onDismiss: () -> Unit,
    onConfirmImport: (Uri) -> Unit
) {
    val date = remember(info.exportedAt) {
        info.exportedAt
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restaurer cette sauvegarde ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                InfoRow("Exportée le", date)
                HorizontalDivider()
                InfoRow("Version", "${info.version}")
                HorizontalDivider()
                InfoRow("Playlists", "${info.playlistCount}")
                HorizontalDivider()
                InfoRow("Favoris", "${info.favoriteCount}")
                HorizontalDivider()
                InfoRow("Entrées d'historique", "${info.historyCount}")
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Tes données actuelles seront remplacées par celles du fichier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmImport(info.uri) }) { Text("Restaurer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
