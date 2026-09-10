package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.PlaylistRow
import fr.synxio.player.ui.components.SynxioDialog
import fr.synxio.player.ui.viewmodel.AppViewModel

@Composable
fun PlaylistsScreen(
    viewModel: AppViewModel,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }

    // Import M3U : le sélecteur système évite d'avoir à demander l'accès complet au stockage.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importPlaylist) }

    var exportTarget by remember { mutableStateOf<Playlist?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val target = exportTarget
        if (uri != null && target != null) viewModel.exportPlaylist(target, uri)
        exportTarget = null
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Playlists") },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Importer un M3U")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Nouvelle") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            item {
                PlaylistRow(
                    playlist = Playlist(
                        id = -1L,
                        name = "Favoris",
                        createdAt = 0,
                        updatedAt = 0,
                        songs = favorites,
                    ),
                    onClick = { viewModel.play(favorites) },
                )
            }

            if (playlists.isEmpty()) {
                item {
                    EmptyState(
                        title = "Aucune playlist",
                        subtitle = "Crée ta première playlist ou importe un fichier M3U existant.",
                        icon = Icons.Rounded.PlaylistPlay,
                        modifier = Modifier.height(320.dp),
                    )
                }
            }

            items(playlists, key = { it.id }) { playlist ->
                Box {
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        onMenuClick = { menuFor = playlist },
                    )
                    DropdownMenu(
                        expanded = menuFor?.id == playlist.id,
                        onDismissRequest = { menuFor = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lire") },
                            onClick = { viewModel.play(playlist.songs); menuFor = null },
                        )
                        DropdownMenuItem(
                            text = { Text("Ajouter à la file") },
                            onClick = { viewModel.addToQueue(playlist.songs); menuFor = null },
                        )
                        DropdownMenuItem(
                            text = { Text("Exporter en M3U") },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                            onClick = {
                                exportTarget = playlist
                                exportLauncher.launch("${playlist.name}.m3u8")
                                menuFor = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer") },
                            onClick = { viewModel.deletePlaylist(playlist); menuFor = null },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        SynxioDialog(onDismiss = { creating = false }) {
            Text("Nouvelle playlist", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nom") },
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { creating = false }) { Text("Annuler") }
                Button(onClick = {
                    viewModel.createPlaylist(name.ifBlank { "Nouvelle playlist" })
                    creating = false
                }) { Text("Créer") }
            }
        }
    }
}
