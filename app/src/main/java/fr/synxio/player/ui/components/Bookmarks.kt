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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import fr.synxio.player.data.db.BookmarkEntity
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass

/** Longueur maximale d'une note de signet : une phrase, pas un résumé de chapitre. */
private const val NOTE_MAX_LENGTH = 200

/**
 * Pose d'un signet : l'endroit est déjà fixé ([place]) au moment du toucher, la note est
 * facultative. Valider sans rien écrire pose un signet sans note.
 */
@Composable
fun AddBookmarkDialog(place: String, onConfirm: (note: String) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = scheme.primary) },
        title = { Text("Ajouter un signet", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    place,
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(NOTE_MAX_LENGTH) },
                    label = { Text("Note (facultative)") },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }, shape = SynxioShape.pill) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = SynxioShape.pill) { Text("Annuler") }
        },
    )
}

/** Un signet de la liste d'un livre : l'endroit, la note ; toucher y saute. */
@Composable
fun BookmarkRow(
    bookmark: BookmarkEntity,
    place: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clip(SynxioShape.cardSm)
            .glass(SynxioShape.cardSm, strength = 0.5f)
            .clickable(onClickLabel = "Aller au signet", role = Role.Button, onClick = onOpen)
            .padding(start = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = scheme.primary)
        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
            Text(
                place,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (bookmark.note.isNotBlank()) {
                Text(
                    bookmark.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = "Supprimer le signet")
        }
    }
}
