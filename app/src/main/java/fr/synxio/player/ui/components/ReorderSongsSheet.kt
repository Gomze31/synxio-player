package fr.synxio.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape

/**
 * Réorganisation d'une liste de titres (playlist) par glisser-déposer.
 *
 * Chaque déplacement est enregistré au lâcher du doigt : fermer la feuille n'a rien
 * d'autre à valider, « Terminé » ne fait que la refermer.
 */
@Composable
fun ReorderSongsSheet(
    title: String,
    songs: List<Song>,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // « id#occurrence » : un même titre peut figurer deux fois dans une playlist.
    val keyed = remember(songs) {
        val seen = HashMap<Long, Int>()
        songs.map { song ->
            val n = seen.getOrDefault(song.id, 0)
            seen[song.id] = n + 1
            "${song.id}#$n" to song
        }
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, keyed, keyOf = { it.first }, onCommit = onMove)
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.xl, end = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Fais glisser la poignée pour déplacer un titre",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Terminé") }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 560.dp),
                contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.sm),
            ) {
                items(reorder.items, key = { it.first }) { (key, song) ->
                    val dragged = reorder.draggingKey == key
                    Row(
                        modifier = Modifier
                            .then(if (dragged) Modifier else Modifier.animateItem())
                            .reorderItem(reorder, key)
                            .fillMaxWidth()
                            .clip(SynxioShape.artworkMd)
                            .background(if (dragged) scheme.surfaceContainerHighest else scheme.surfaceContainerLow.copy(alpha = 0f))
                            .padding(start = Space.sm, top = Space.xs, bottom = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(song.artworkUri, Modifier.size(44.dp), SynxioShape.artworkSm)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Déplacer ${song.title}",
                            tint = if (dragged) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(Space.touchTarget)
                                .reorderHandle(reorder, key)
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
