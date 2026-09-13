package fr.synxio.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobooksScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val audiobooks by viewModel.audiobooks.collectAsStateWithLifecycle()
    val audiobookFolders by viewModel.audiobookFolders.collectAsStateWithLifecycle()

    var showManageFolders by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Livres audio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showManageFolders = true }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Gérer les dossiers")
                    }
                }
            )
        }
    ) { padding ->
        if (audiobooks.isEmpty()) {
            EmptyState(
                title = "Aucun livre audio",
                subtitle = "Ajoutez un dossier contenant vos livres audio pour les séparer de votre musique.",
                icon = Icons.Rounded.MenuBook,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(audiobooks, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.playSong(song, audiobooks) },
                        onMenuClick = {}
                    )
                }
            }
        }
    }

    if (showManageFolders) {
        ManageAudiobookFoldersDialog(
            folders = audiobookFolders,
            onAdd = viewModel::addAudiobookFolder,
            onRemove = viewModel::removeAudiobookFolder,
            onDismiss = { showManageFolders = false }
        )
    }
}

@Composable
fun ManageAudiobookFoldersDialog(
    folders: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPath by remember { mutableStateOf("/storage/emulated/0/Audiobooks") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dossiers Livres audio") },
        text = {
            Column {
                Text(
                    "Les musiques dans ces dossiers n'apparaîtront plus dans votre bibliothèque principale, mais seront visibles dans cette section.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.height(150.dp)) {
                    items(folders) { folder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = folder,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { onRemove(folder) }) {
                                Text("Retirer", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    label = { Text("Chemin du dossier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPath.isNotBlank()) {
                        onAdd(newPath.trim())
                        newPath = ""
                    }
                }
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
