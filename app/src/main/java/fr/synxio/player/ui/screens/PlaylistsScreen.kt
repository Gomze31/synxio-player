package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.repo.RulePlaylist
import fr.synxio.player.data.repo.SmartPlaylist
import fr.synxio.player.data.repo.SmartPlaylistId
import fr.synxio.player.ui.components.ArtworkMosaic
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.PlaylistRow
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SynxioDialog
import fr.synxio.player.ui.viewmodel.AppViewModel

@Composable
fun PlaylistsTab(
    viewModel: AppViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmartPlaylist: (SmartPlaylistId) -> Unit,
    onOpenRulePlaylist: (Long) -> Unit,
    onCreateRulePlaylist: () -> Unit,
    onEditRulePlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val smartPlaylists by viewModel.smartPlaylists.collectAsStateWithLifecycle()
    val rulePlaylists by viewModel.rulePlaylists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var ruleMenuFor by remember { mutableStateOf<RulePlaylist?>(null) }
    var deletingRule by remember { mutableStateOf<RulePlaylist?>(null) }

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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp, bottom = 80.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Importer", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Importer un M3U", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
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



            if (smartPlaylists.isNotEmpty()) {
                item { SectionHeader("Sélections automatiques") }
                items(smartPlaylists, key = { it.id.name }) { smart ->
                    SmartPlaylistRow(
                        playlist = smart,
                        onClick = { onOpenSmartPlaylist(smart.id) },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { SectionHeader("Mes règles") }
                    IconButton(onClick = onCreateRulePlaylist) {
                        Icon(Icons.Rounded.Add, contentDescription = "Nouvelle règle")
                    }
                }
            }
            if (rulePlaylists.isEmpty()) {
                item {
                    Text(
                        text = "Compose ta propre playlist à partir de critères : genre, favoris, date d'ajout…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            items(rulePlaylists, key = { "rule-${it.id}" }) { rule ->
                Box {
                    RulePlaylistRow(
                        playlist = rule,
                        onClick = { onOpenRulePlaylist(rule.id) },
                        onMenuClick = { ruleMenuFor = rule },
                    )
                    DropdownMenu(
                        expanded = ruleMenuFor?.id == rule.id,
                        onDismissRequest = { ruleMenuFor = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Modifier") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { onEditRulePlaylist(rule.id); ruleMenuFor = null },
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { deletingRule = rule; ruleMenuFor = null },
                        )
                    }
                }
            }

            if (smartPlaylists.isNotEmpty() || rulePlaylists.isNotEmpty()) {
                item { SectionHeader("Tes playlists") }
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

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Nouvelle") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
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

    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            title = { Text("Supprimer la règle") },
            text = { Text("Supprimer « ${rule.name} » ? Cette action ne peut pas être annulée.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRulePlaylist(rule)
                    deletingRule = null
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) { Text("Annuler") }
            },
        )
    }
}

/**
 * Ligne d'une sélection automatique.
 *
 * La description tient lieu de sous-titre plutôt que le nombre de titres : ces listes
 * changent toutes seules, et la règle qui les produit est l'information utile.
 */
@Composable
private fun SmartPlaylistRow(playlist: SmartPlaylist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkMosaic(
            models = playlist.artworkUris,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlist.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = playlist.songCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Ligne d'une playlist à règles créée par l'utilisateur.
 *
 * Même langage visuel que [SmartPlaylistRow] pour rester cohérent avec les sélections
 * automatiques : seul un bouton d'options s'y ajoute, ces playlists pouvant être
 * modifiées et supprimées.
 */
@Composable
private fun RulePlaylistRow(
    playlist: RulePlaylist,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkMosaic(
            models = playlist.songs.distinctBy { it.albumId }.take(4).map { it.artworkUri },
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.rules.rules.size} critère(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = playlist.songCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
        }
    }
}
