package fr.synxio.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.asFileSize
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song

/** Actions proposées sur un titre, appelées depuis les listes et le lecteur. */
data class SongActions(
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onOpenAlbum: (() -> Unit)? = null,
    val onOpenArtist: (() -> Unit)? = null,
    val onEditTags: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onRemoveFromPlaylist: (() -> Unit)? = null,
)

@Composable
fun SongOptionsSheet(
    song: Song,
    isFavorite: Boolean,
    actions: SongActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDetails by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.artworkUri, Modifier.size(56.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        text = "${song.displayArtist} · ${song.displayAlbum}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SheetAction(Icons.Rounded.SkipNext, "Lire juste après") {
                actions.onPlayNext(); onDismiss()
            }
            SheetAction(Icons.Rounded.QueueMusic, "Ajouter à la file") {
                actions.onAddToQueue(); onDismiss()
            }
            SheetAction(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            ) { actions.onToggleFavorite(); onDismiss() }
            SheetAction(Icons.Rounded.PlaylistAdd, "Ajouter à une playlist") {
                actions.onAddToPlaylist(); onDismiss()
            }

            actions.onRemoveFromPlaylist?.let {
                SheetAction(Icons.Rounded.PlaylistAdd, "Retirer de cette playlist") {
                    it(); onDismiss()
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            actions.onOpenAlbum?.let {
                SheetAction(Icons.Rounded.Album, "Aller à l'album") { it(); onDismiss() }
            }
            actions.onOpenArtist?.let {
                SheetAction(Icons.Rounded.Person, "Aller à l'artiste") { it(); onDismiss() }
            }
            actions.onEditTags?.let {
                SheetAction(Icons.Rounded.Edit, "Modifier les tags") { it(); onDismiss() }
            }
            actions.onShare?.let {
                SheetAction(Icons.Rounded.Share, "Partager le fichier") { it(); onDismiss() }
            }
            SheetAction(Icons.Rounded.Info, "Détails du fichier") { showDetails = true }
        }
    }

    if (showDetails) {
        SongDetailsDialog(song) { showDetails = false }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Détails") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DetailLine("Titre", song.title)
                DetailLine("Artiste", song.displayArtist)
                DetailLine("Album", song.displayAlbum)
                song.albumArtist?.takeIf { it.isNotBlank() }?.let { DetailLine("Artiste d'album", it) }
                song.genre?.takeIf { it.isNotBlank() }?.let { DetailLine("Genre", it) }
                if (song.year > 0) DetailLine("Année", song.year.toString())
                if (song.track > 0) DetailLine("Piste", "${song.disc}-${song.track}")
                DetailLine("Durée", song.durationMs.asDuration())
                DetailLine("Format", song.extension.ifBlank { song.mimeType })
                DetailLine("Débit", "~${song.approxBitrateKbps} kb/s")
                DetailLine("Taille", song.sizeBytes.asFileSize())
                DetailLine("Chemin", song.path)
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Choix d'une playlist existante, avec création à la volée. */
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Ajouter à une playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            SheetAction(Icons.Rounded.PlaylistAdd, "Nouvelle playlist…") { creating = true }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onSelect(playlist); onDismiss() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nouvelle playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onCreate(name.ifBlank { "Nouvelle playlist" }); creating = false; onDismiss() }
                ) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Annuler") } },
        )
    }
}

/** Petit conteneur pour les dialogues personnalisés (édition rapide). */
@Composable
fun SynxioDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
