package fr.synxio.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass

/** Vignette d'une ligne réorganisable : un peu plus petite qu'une ligne de liste. */
private val ReorderThumbSize = 44.dp

/**
 * Réorganisation d'une liste de titres (playlist) par glisser-déposer.
 *
 * Chaque déplacement est enregistré au lâcher du doigt : fermer la feuille n'a rien
 * d'autre à valider, « Terminé » ne fait que la refermer.
 *
 * La ligne saisie se soulève (verre, liseré à la signature, ombre, léger agrandissement)
 * et les autres s'écartent en glissant. Pour TalkBack, qui ne sait pas faire glisser,
 * chaque ligne propose aussi « Monter » et « Descendre ».
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
    val colors = SynxioTheme.colors
    val reduce = LocalReduceMotion.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainerLow,
        shape = SynxioShape.sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.xl, end = Space.lg, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ORDRE DE LECTURE", style = EyebrowTextStyle, color = scheme.primary, maxLines = 1)
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "Fais glisser la poignée pour déplacer un titre",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.md))
                BrandButton(text = "Terminé", onClick = onDismiss, icon = Icons.Rounded.Check)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 560.dp),
                contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.sm),
            ) {
                itemsIndexed(reorder.items, key = { _, it -> it.first }) { index, (key, song) ->
                    val dragged = reorder.draggingKey == key
                    val handleTint by animateColorAsState(
                        if (dragged) scheme.primary else scheme.onSurfaceVariant,
                        Motion.standardOrSnap(reduce, Motion.SHORT),
                        label = "handleTint",
                    )
                    val last = reorder.items.lastIndex
                    Row(
                        modifier = Modifier
                            .then(if (dragged) Modifier else Modifier.animateItem(placementSpec = Motion.spatial()))
                            .reorderItem(reorder, key)
                            .fillMaxWidth()
                            .clip(SynxioShape.artworkMd)
                            .then(
                                if (dragged) Modifier
                                    .background(scheme.surfaceContainerHighest)
                                    .glass(SynxioShape.artworkMd, strength = 1f, border = false, tint = colors.brandEnd)
                                    .border(1.5.dp, colors.brandBrush, SynxioShape.artworkMd)
                                else Modifier.background(Color.Transparent)
                            )
                            .semantics {
                                // Alternative au glisser pour le lecteur d'écran.
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction("Monter") { onMove(index, index - 1); true })
                                    if (index < last) add(CustomAccessibilityAction("Descendre") { onMove(index, index + 1); true })
                                }
                            }
                            .padding(start = Space.xs, top = Space.xs, bottom = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                            color = if (dragged) scheme.primary else scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(Space.xxl),
                        )
                        Artwork(song.artworkUri, Modifier.size(ReorderThumbSize), SynxioShape.artworkSm)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            Modifier
                                .size(Space.touchTarget)
                                .reorderHandle(reorder, key),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Déplacer ${song.title}",
                                tint = handleTint,
                            )
                        }
                    }
                }
            }
        }
    }
}
