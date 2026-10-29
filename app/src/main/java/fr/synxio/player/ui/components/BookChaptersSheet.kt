package fr.synxio.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.BookChapter
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.TabularNumbers

/** Largeur de la colonne des numéros : les titres restent alignés jusqu'à 999. */
private val ChapterNumberWidth = 32.dp

/**
 * Chapitres d'un livre, chapitre en cours en tête de lecture. Toucher un chapitre y saute.
 * Début dans le livre et durée en chiffres tabulaires, pour que les colonnes ne dansent pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookChaptersSheet(
    chapters: List<BookChapter>,
    currentIndex: Int,
    onSelect: (BookChapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        dragHandle = { SheetHandle() },
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Chapitres",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(horizontal = Space.xl, vertical = Space.sm)
                    .semantics { heading() },
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = Space.sm, end = Space.sm, bottom = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                itemsIndexed(chapters, key = { i, c -> "${c.songId}-${c.startInFileMs}-$i" }) { index, chapter ->
                    val isCurrent = index == currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touchTarget)
                            .clip(SynxioShape.cardSm)
                            .then(
                                if (isCurrent) Modifier.background(scheme.primary.copy(alpha = 0.12f)) else Modifier
                            )
                            .clickable(onClickLabel = "Aller à ce chapitre", role = Role.Button) {
                                onSelect(chapter)
                            }
                            .padding(horizontal = Space.md, vertical = Space.sm)
                            .semantics { selected = isCurrent },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                            color = if (isCurrent) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.width(ChapterNumberWidth),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrent) scheme.primary else scheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${chapter.startInBookMs.asDuration()} · ${chapter.durationMs.asDuration()}",
                                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
