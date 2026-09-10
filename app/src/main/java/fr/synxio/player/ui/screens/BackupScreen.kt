package fr.synxio.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.R
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.BackupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran de sauvegarde et restauration des données.
 * Permet d'exporter/importer les données de l'application.
 */
@Composable
fun BackupScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val backupViewModel: BackupViewModel = hiltViewModel()
    val context = LocalContext.current
    
    val backupState by backupViewModel.backupState.collectAsStateWithLifecycle()
    val backupInfo by backupViewModel.backupInfo.collectAsStateWithLifecycle()
    
    var showClearDialog by remember { mutableStateOf(false) }
    
    // Launchers pour sélectionner des fichiers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { backupViewModel.exportData(it) }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { backupViewModel.checkBackupFile(it) }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Sauvegarde & Restauration") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            }
        )
        
        // Contenu avec scroll
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            item {
                // Section Exporter
                BackupSectionTitle(
                    title = "Exporter vos données",
                    description = "Sauvegardez vos playlists, favoris, statistiques et paramètres",
                    icon = Icons.Rounded.CloudUpload
                )
            }
            
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                BackupActionCard(
                    title = "Exporter toutes les données",
                    description = "Exporte les playlists, favoris, historique et paramètres dans un fichier",
                    icon = Icons.Rounded.Backup,
                    onClick = { exportLauncher.launch(backupViewModel.getDefaultBackupFilename()) },
                    enabled = true
                )
            }
            
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                BackupActionCard(
                    title = "Exporter une playlist (M3U8)",
                    description = "Exporte une playlist au format M3U8 compatible avec d'autres lecteurs",
                    icon = Icons.Rounded.FormatListBulleted,
                    onClick = { /* TODO: Implement playlist export */ },
                    enabled = viewModel.playlists.value.isNotEmpty()
                )
            }
            
            item { Spacer(Modifier.height(24.dp)) }
            
            item {
                // Section Importer
                BackupSectionTitle(
                    title = "Importer vos données",
                    description = "Restaurez vos données depuis un fichier de sauvegarde",
                    icon = Icons.Rounded.CloudDownload
                )
            }
            
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                BackupActionCard(
                    title = "Importer depuis un fichier",
                    description = "Importe les playlists, favoris, historique et paramètres",
                    icon = Icons.Rounded.Restore,
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = true
                )
            }
            
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                BackupActionCard(
                    title = "Importer une playlist (M3U8)",
                    description = "Importe une playlist depuis un fichier M3U8",
                    icon = Icons.Rounded.InsertDriveFile,
                    onClick = { /* TODO: Implement playlist import */ },
                    enabled = true
                )
            }
            
            item { Spacer(Modifier.height(24.dp)) }
            
            item {
                // Section Dangereuse
                BackupSectionTitle(
                    title = "Actions dangereuses",
                    description = "Ces actions peuvent supprimer vos données",
                    icon = Icons.Rounded.Warning,
                    warning = true
                )
            }
            
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                BackupActionCard(
                    title = "Effacer toutes les données locales",
                    description = "Supprime toutes vos playlists, favoris, historique et paramètres. IRRÉVERSIBLE",
                    icon = Icons.Rounded.Clear,
                    onClick = { showClearDialog = true },
                    enabled = true,
                    destructive = true
                )
            }
            
            item { Spacer(Modifier.height(24.dp)) }
            
            item {
                // Section Informations
                BackupInfoSection()
            }
        }
    }
    
    // Gestion des états
    when (val state = backupState) {
        is BackupViewModel.BackupState.Exporting -> {
            // Afficher un indicateur de chargement
        }
        is BackupViewModel.BackupState.ExportSuccess -> {
            LaunchedEffect(state) {
                // Afficher un message de succès
            }
        }
        is BackupViewModel.BackupState.ExportFailed -> {
            LaunchedEffect(state) {
                // Afficher un message d'erreur
            }
        }
        is BackupViewModel.BackupState.Importing -> {
            // Afficher un indicateur de chargement
        }
        is BackupViewModel.BackupState.ImportSuccess -> {
            LaunchedEffect(state) {
                // Afficher un message de succès
                backupViewModel.resetState()
            }
        }
        is BackupViewModel.BackupState.ImportFailed -> {
            LaunchedEffect(state) {
                // Afficher un message d'erreur
            }
        }
        is BackupViewModel.BackupState.Checking -> {
            // Afficher un indicateur de vérification
        }
        is BackupViewModel.BackupState.ValidFile -> {
            // Le fichier est valide, on peut maintenant l'importer
            // Le Uri est déjà disponible dans backupInfo
            if (backupInfo != null) {
                LaunchedEffect(state) {
                    backupViewModel.importData(backupInfo!!.uri)
                }
            }
        }
        is BackupViewModel.BackupState.InvalidFile -> {
            LaunchedEffect(state) {
                // Afficher un message que le fichier n'est pas valide
            }
        }
        is BackupViewModel.BackupState.ClearSuccess -> {
            LaunchedEffect(state) {
                // Afficher un message de succès
                backupViewModel.resetState()
            }
        }
        else -> {}
    }
    
    // Dialog de confirmation d'effacement
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Effacer toutes les données") },
            text = { 
                Column {
                    Text("Êtes-vous sûr de vouloir supprimer toutes vos données ?")
                    Spacer(Modifier.height(8.dp))
                    Text("Cela inclut :")
                    Spacer(Modifier.height(4.dp))
                    Text("• Toutes vos playlists")
                    Text("• Tous vos favoris")
                    Text("• Tout votre historique")
                    Text("• Toutes vos statistiques")
                    Text("• Vos paramètres")
                    Spacer(Modifier.height(8.dp))
                    Text("Cette action ne peut pas être annulée !")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backupViewModel.clearAllData()
                        showClearDialog = false
                    }
                ) {
                    Text("EFFACER TOUT", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false }
                ) {
                    Text("Annuler")
                }
            }
        )
    }
    
    // Afficher les infos du backup si disponible
    backupInfo?.let { info ->
        BackupInfoDialog(
            info = info,
            onDismiss = { backupViewModel.resetState() },
            onConfirmImport = { uri ->
                backupViewModel.importData(uri)
            }
        )
    }
}

/**
 * Titre de section pour la sauvegarde.
 */
@Composable
private fun BackupSectionTitle(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    warning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (warning) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (warning) MaterialTheme.colorScheme.onErrorContainer
                      else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Carte d'action de sauvegarde.
 */
@Composable
private fun BackupActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    ElevatedCard(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (destructive) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer
                          else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (destructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (enabled) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Accéder",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Section d'informations sur la sauvegarde.
 */
@Composable
private fun BackupInfoSection() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "À propos de la sauvegarde",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            InfoItem(
                icon = Icons.Rounded.MusicNote,
                title = "Données sauvegardées",
                description = "Playlists, favoris, historique de lecture, statistiques, paramètres"
            )
            
            Spacer(Modifier.height(8.dp))
            
            InfoItem(
                icon = Icons.Rounded.Save,
                title = "Format",
                description = "Fichier JSON compressé compatible avec Synxio Player"
            )
            
            Spacer(Modifier.height(8.dp))
            
            InfoItem(
                icon = Icons.Rounded.Folder,
                title = "Emplacement",
                description = "Les fichiers sont sauvegardés dans le stockage local de votre appareil"
            )
            
            Spacer(Modifier.height(8.dp))
            
            InfoItem(
                icon = Icons.Rounded.Palette,
                title = "Personnalisation",
                description = "Les couleurs et thème sont inclus dans la sauvegarde"
            )
        }
    }
}

/**
 * Élément d'information.
 */
@Composable
private fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dialog d'informations sur le backup.
 */
@Composable
private fun BackupInfoDialog(
    info: fr.synxio.player.data.repo.BackupRepository.BackupInfo,
    onDismiss: () -> Unit,
    onConfirmImport: (Uri) -> Unit
) {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        .format(Date(info.exportedAt))
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fichier de sauvegarde détecté") },
        text = {
            Column {
                Text("Un fichier de sauvegarde Synxio valide a été détecté.")
                Spacer(Modifier.height(16.dp))
                
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        InfoRow(
                            label = "Date d'export",
                            value = date
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        InfoRow(
                            label = "Version",
                            value = "${info.version}"
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        InfoRow(
                            label = "Nombre de playlists",
                            value = "${info.playlistCount}"
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        InfoRow(
                            label = "Nombre de favoris",
                            value = "${info.favoriteCount}"
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        InfoRow(
                            label = "Nombre d'entrées d'historique",
                            value = "${info.historyCount}"
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text("Souhaitez-vous importer ce fichier ?")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmImport(info.uri) }
            ) {
                Text("Importer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Annuler")
            }
        }
    )
}

/**
 * Ligne d'information pour le dialog.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
    }
}
