package fr.synxio.player.ui.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.synxio.player.ui.viewmodel.SongInsightsViewModel
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.asFileSize
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape

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
    val onStartRadio: (() -> Unit)? = null,
    val onRemoveFromPlaylist: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
)

/**
 * Feuille d'options d'un titre. Les actions sont rangées en trois groupes séparés par
 * un simple espace (lecture, collection, fichier) : pas de traits, la respiration
 * suffit à les distinguer. La suppression, seule action destructrice, ferme la liste
 * en `error`.
 */
@Composable
fun SongOptionsSheet(
    song: Song,
    isFavorite: Boolean,
    actions: SongActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDetails by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Space.md)
        ) {
            Row(
                Modifier.padding(horizontal = Space.xl, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.artworkUri, Modifier.size(56.dp), SynxioShape.artworkSm)
                Spacer(Modifier.width(Space.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "${song.displayArtist} · ${song.displayAlbum}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            GroupSpacer()

            // Lecture
            SheetAction(Icons.Rounded.SkipNext, "Lire juste après") {
                actions.onPlayNext(); onDismiss()
            }
            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Ajouter à la file") {
                actions.onAddToQueue(); onDismiss()
            }
            actions.onStartRadio?.let {
                SheetAction(Icons.Rounded.Radio, "Lancer une radio") { it(); onDismiss() }
            }

            GroupSpacer()

            // Collection
            SheetAction(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            ) { actions.onToggleFavorite(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Ajouter à une playlist") {
                actions.onAddToPlaylist(); onDismiss()
            }
            actions.onRemoveFromPlaylist?.let {
                SheetAction(Icons.Rounded.PlaylistRemove, "Retirer de cette playlist") {
                    it(); onDismiss()
                }
            }
            actions.onOpenAlbum?.let {
                SheetAction(Icons.Rounded.Album, "Aller à l'album") { it(); onDismiss() }
            }
            actions.onOpenArtist?.let {
                SheetAction(Icons.Rounded.Person, "Aller à l'artiste") { it(); onDismiss() }
            }

            GroupSpacer()

            // Fichier
            actions.onEditTags?.let {
                SheetAction(Icons.Rounded.Edit, "Modifier les tags") { it(); onDismiss() }
            }
            actions.onShare?.let {
                SheetAction(Icons.Rounded.Share, "Partager le fichier") { it(); onDismiss() }
            }
            SheetAction(Icons.Rounded.Info, "Détails du fichier") { showDetails = true }
            actions.onDelete?.let { delete ->
                SheetAction(Icons.Rounded.Delete, "Supprimer de l'appareil", destructive = true) {
                    // Android 11+ affiche sa propre confirmation (corbeille système) :
                    // inutile d'en empiler une deuxième. Avant, la suppression est directe
                    // et définitive, d'où notre AlertDialog.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        delete(); onDismiss()
                    } else {
                        confirmDelete = true
                    }
                }
            }
        }
    }

    if (showDetails) {
        SongDetailsDialog(song) { showDetails = false }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce titre ?") },
            text = { Text("« ${song.title} » sera définitivement supprimé de l'appareil.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete?.invoke()
                    onDismiss()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Séparation entre deux groupes d'actions : un espace, pas un trait. */
@Composable
private fun GroupSpacer() = Spacer(Modifier.height(Space.sm))

/** Hauteur d'une ligne d'action de feuille (liste Material à une ligne + icône). */
private val SheetActionHeight = 56.dp

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SheetActionHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) scheme.error else scheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) scheme.error else scheme.onSurface,
        )
    }
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val insightsViewModel: SongInsightsViewModel = hiltViewModel()
    // Recalculé seulement si le morceau change : ce sont des lectures de map en mémoire,
    // mais les refaire à chaque recomposition du dialogue n'aurait aucun intérêt.
    val insights = remember(song.id) { insightsViewModel.insightsFor(song) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Détails") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Les écoutes d'abord : c'est ce qu'on ne peut lire nulle part ailleurs,
                // alors que le reste se retrouve dans n'importe quel explorateur.
                if (insights.hasHistory) {
                    DetailLine(
                        label = "Écoutes",
                        value = buildString {
                            append("${insights.playCount} lecture")
                            if (insights.playCount > 1) append("s")
                            if (insights.skipCount > 0) {
                                append(" · ${insights.skipCount} coupure")
                                if (insights.skipCount > 1) append("s")
                            }
                        },
                    )
                    insights.completionLabel?.let { DetailLine("Écouté en moyenne", it) }
                    insights.lastPlayedLabel?.let { DetailLine("Dernière écoute", it) }
                }
                insights.loudnessLabel?.let { DetailLine("Niveau mesuré", it) }

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
    Column(Modifier.padding(vertical = Space.xs)) {
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = Space.md)
        ) {
            Text(
                text = "Ajouter à une playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = Space.xl, vertical = Space.md)
                    .semantics { heading() },
            )

            SheetAction(Icons.Rounded.Add, "Nouvelle playlist…") { creating = true }
            GroupSpacer()

            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onSelect(playlist); onDismiss() },
                        modifier = Modifier.padding(horizontal = Space.md),
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
            Column(Modifier.padding(Space.xl)) {
                content()
                Spacer(Modifier.height(Space.sm))
            }
        }
    }
}
