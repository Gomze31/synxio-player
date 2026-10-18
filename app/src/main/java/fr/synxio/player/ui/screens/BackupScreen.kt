package fr.synxio.player.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.repo.BackupRepository
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.GlassCard
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
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.BackupViewModel
import fr.synxio.player.ui.viewmodel.BackupViewModel.BackupState

/**
 * Sauvegarde et restauration : export/import complet (JSON), export/import d'une playlist
 * au format M3U8, et remise à zéro des données locales.
 *
 * L'export complet est l'action principale (bouton à la signature) ; le reste se range en
 * groupes de verre. Les retours passent par la snackbar de l'app
 * ([AppViewModel.showMessage]) ; une barre à la signature sous la barre d'app indique
 * qu'une opération tourne, et les actions sont désactivées pendant ce temps pour éviter
 * un double import.
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
                    title = { Text("Sauvegarde", style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                BusyBar(busy)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = padding.calculateTopPadding() + Space.sm,
                bottom = padding.calculateBottomPadding() + Space.xxl + Space.bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "hero") {
                ExportHero(
                    enabled = !busy,
                    onExport = { exportLauncher.launch(backupViewModel.getDefaultBackupFilename()) },
                    modifier = Modifier.enterCascade(0, key = "backup-hero"),
                )
            }

            item(key = "export-title") { BackupGroupTitle("Playlists") }
            item(key = "playlists") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .enterCascade(1, key = "backup-playlists"),
                    contentPadding = Space.xs,
                ) {
                    Column {
                        BackupAction(
                            title = "Exporter une playlist (M3U8)",
                            description = if (playlists.isEmpty()) "Crée d'abord une playlist"
                            else "Lisible par VLC, Poweramp, foobar2000…",
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            enabled = !busy && playlists.isNotEmpty(),
                            onClick = { showExportPlaylistDialog = true },
                        )
                        BackupDivider()
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
                }
            }

            item(key = "import-title") { BackupGroupTitle("Restaurer") }
            item(key = "import") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .enterCascade(2, key = "backup-import"),
                    contentPadding = Space.xs,
                ) {
                    BackupAction(
                        title = "Restaurer une sauvegarde",
                        description = "Remplace tes données par celles d'un fichier Synxio",
                        icon = Icons.Rounded.Restore,
                        enabled = !busy,
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                    )
                }
            }

            item(key = "danger-title") { BackupGroupTitle("Zone sensible", danger = true) }
            item(key = "clear") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .enterCascade(3, key = "backup-clear"),
                    contentPadding = Space.xs,
                ) {
                    BackupAction(
                        title = "Effacer toutes les données",
                        description = "Supprime playlists, favoris, historique, statistiques et réglages. Tes fichiers audio ne sont pas touchés.",
                        icon = Icons.Rounded.DeleteForever,
                        enabled = !busy,
                        destructive = true,
                        onClick = { showClearDialog = true },
                    )
                }
            }

            item(key = "about-title") { BackupGroupTitle("Bon à savoir") }
            item(key = "about") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .enterCascade(4, key = "backup-about"),
                ) { BackupInfoSection() }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Effacer toutes les données ?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Tes playlists, favoris, historique, statistiques et réglages seront supprimés. " +
                        "Tes fichiers audio restent sur l'appareil. Cette action est irréversible : " +
                        "exporte une sauvegarde avant si tu veux pouvoir revenir en arrière."
                )
            },
            confirmButton = {
                // Bouton plein couleur d'erreur : le geste irréversible ne se confond pas
                // avec « Annuler ».
                Button(
                    onClick = {
                        showClearDialog = false
                        backupViewModel.clearAllData()
                    },
                    shape = SynxioShape.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Effacer") }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Quelle playlist exporter ?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistChoice(
                            playlist = playlist,
                            onClick = {
                                playlistToExportId = playlist.id
                                showExportPlaylistDialog = false
                                exportM3uLauncher.launch("${playlist.name.toSafeFileName()}.m3u8")
                            },
                        )
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Importer la playlist", style = MaterialTheme.typography.headlineSmall) },
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
                        shape = SynxioShape.cardSm,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
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
 * Barre d'activité à la signature sous la barre d'app. L'opération n'expose pas
 * d'avancement : un segment lumineux balaie la piste (animation infinie, mais composée
 * seulement pendant l'opération). Animations réduites : piste pleine, immobile.
 */
@Composable
private fun BusyBar(busy: Boolean) {
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible = busy,
        enter = expandVertically(Motion.spatial()) + fadeIn(Motion.enter(Motion.SHORT)),
        exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.exit()),
    ) {
        val barModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs)
            .semantics { contentDescription = "Opération en cours" }
        if (reduce) {
            BrandProgressBar(progress = 1f, height = 4.dp, modifier = barModifier)
        } else {
            val brush = SynxioTheme.colors.brandBrushHorizontal
            val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            val t = rememberInfiniteTransition(label = "backupBusy")
            val x by t.animateFloat(
                initialValue = -0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
                label = "backupBusyX",
            )
            Box(
                barModifier
                    .height(4.dp)
                    .clip(SynxioShape.pill)
                    .background(track)
                    .drawBehind {
                        val w = size.width * 0.4f
                        drawRect(brush, topLeft = Offset(size.width * x, 0f), size = Size(w, size.height))
                    }
            )
        }
    }
}

/**
 * En-tête de l'écran : ce que contient une sauvegarde, et l'action principale.
 */
@Composable
private fun ExportHero(enabled: Boolean, onExport: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SynxioTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = Space.xl) {
        Column {
            Box(
                Modifier
                    .size(56.dp)
                    .glowShadow(shape, colors.brandEnd, elevation = 18.dp)
                    .clip(shape)
                    .background(colors.brandBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Backup, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(Space.lg))
            Text("EXPORT COMPLET", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                "Tout ton Synxio dans un fichier",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "Playlists, favoris, historique, statistiques et réglages dans un fichier JSON, à garder où tu veux.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.xl))
            BrandButton(
                text = "Exporter toutes les données",
                icon = Icons.Rounded.Save,
                enabled = enabled,
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BackupGroupTitle(title: String, danger: Boolean = false) {
    Text(
        title.uppercase(),
        style = EyebrowTextStyle,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.xs, top = Space.lg, bottom = Space.xxs)
            .semantics { heading() },
    )
}

@Composable
private fun BackupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = Space.md),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

/**
 * Action de l'écran : une ligne dans un groupe de verre. Une action destructive prend la
 * couleur d'erreur sur l'icône et le titre, jamais seulement sur un détail.
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
    val accent = if (destructive) scheme.error else scheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clip(SynxioShape.cardSm)
            .then(if (enabled) Modifier.pressBounce(0.98f) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accent)
        }
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (destructive) scheme.error else scheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Space.sm))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
        )
    }
}

/** Ligne de choix d'une playlist dans le dialogue d'export. */
@Composable
private fun PlaylistChoice(playlist: Playlist, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clip(SynxioShape.cardSm)
            .pressBounce(0.98f)
            .clickable(onClickLabel = "Exporter", role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                playlist.songCount.pluralSongs(),
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Ce que contient une sauvegarde et où elle va. */
@Composable
private fun BackupInfoSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(Icons.Rounded.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Restaurer cette sauvegarde ?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                InfoRow("Exportée le", date)
                InfoDivider()
                InfoRow("Version", "${info.version}")
                InfoDivider()
                InfoRow("Playlists", "${info.playlistCount}")
                InfoDivider()
                InfoRow("Favoris", "${info.favoriteCount}")
                InfoDivider()
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
private fun InfoDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.md))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
