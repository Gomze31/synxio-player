package fr.synxio.player.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.media.StorageTreePaths
import fr.synxio.player.data.model.BookProgress
import fr.synxio.player.data.model.Folder
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.chapterLabel
import fr.synxio.player.data.model.remainingLabel
import fr.synxio.player.data.model.statusLabel
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.ScreenHeader
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Hauteur maximale de la liste des dossiers dans la boîte de dialogue. */
private val FolderListMaxHeight = 220.dp

/** Couverture d'un livre dans la liste : plus grande qu'une vignette de titre. */
private val BookCoverSize = 64.dp

/**
 * Livres audio : les morceaux rangés dans les dossiers déclarés comme tels, regroupés
 * par dossier (un dossier = un livre, chapitres dans l'ordre des pistes).
 *
 * @param onBack bouton retour de la barre ; `null` quand l'écran est affiché comme
 * destination racine, sans écran précédent à rejoindre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobooksScreen(
    viewModel: AppViewModel,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val audiobooks by viewModel.audiobooks.collectAsStateWithLifecycle()
    val audiobookFolders by viewModel.audiobookFolders.collectAsStateWithLifecycle()
    val progress by viewModel.bookProgress.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current

    var showManageFolders by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    val books = remember(audiobooks) {
        audiobooks.groupBy { it.folderPath }
            .map { (path, songs) -> Folder(path, songs.sortedBy { it.track }) }
            .sortedBy { it.name }
    }
    val summary = remember(books) {
        val total = books.sumOf { book -> book.songs.sumOf { it.durationMs } }
        val count = if (books.size <= 1) "${books.size} livre" else "${books.size} livres"
        if (books.isEmpty()) null else "$count · ${total.asLongDuration()}"
    }
    // Le grand titre vit dans la liste ; la barre ne le reprend qu'une fois passé dessous.
    val showBarTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val hasList = !(library.isLoading && books.isEmpty()) && books.isNotEmpty()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = {
                    AnimatedVisibility(
                        visible = !hasList || showBarTitle,
                        enter = fadeIn(Motion.standardOrSnap(reduce, Motion.SHORT)),
                        exit = fadeOut(Motion.standardOrSnap(reduce, Motion.XS)),
                    ) {
                        Text("Livres audio", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showManageFolders = true }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Gérer les dossiers de livres audio")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            library.isLoading && books.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Space.sm, vertical = Space.sm)
                    .semantics { contentDescription = "Chargement des livres audio" },
            ) { repeat(6) { SkeletonSongRow() } }

            // Deux vides différents, deux remèdes différents : sans dossier déclaré, il
            // faut en choisir un ; avec des dossiers mais rien dedans, il faut vérifier
            // leur contenu ou relancer le scan.
            books.isEmpty() && audiobookFolders.isEmpty() -> EmptyState(
                title = "Aucun livre audio",
                subtitle = "Choisis le dossier où tu ranges tes livres audio : ils seront rangés ici, à part de ta musique.",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                actionLabel = "Choisir un dossier",
                onAction = { showManageFolders = true },
                modifier = Modifier.padding(padding),
            )

            books.isEmpty() -> EmptyState(
                title = "Dossiers vides",
                subtitle = "Aucun fichier audio trouvé dans tes dossiers de livres audio. Vérifie leur contenu, puis relance un scan.",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                actionLabel = "Relancer le scan",
                onAction = { viewModel.rescan() },
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Space.sm,
                    end = Space.sm,
                    bottom = Space.bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                item(key = "header", contentType = "header") {
                    ScreenHeader(
                        title = "Livres audio",
                        eyebrow = "À écouter",
                        subtitle = summary,
                        // La liste est en retrait de Space.sm : on ramène le titre sur la gouttière.
                        modifier = Modifier.offset(x = -Space.sm),
                    )
                }
                itemsIndexed(books, key = { _, it -> it.path }, contentType = { _, _ -> "book" }) { index, folder ->
                    BookRow(
                        book = folder,
                        progress = progress[folder.path],
                        onClick = { onOpenFolder(folder.path) },
                        modifier = Modifier
                            .animateItem()
                            .enterCascade(index, "book-${folder.path}"),
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
            onDismiss = { showManageFolders = false },
        )
    }
}

/**
 * Un livre : carte de verre avec la couverture du premier chapitre, le titre (nom du
 * dossier), le nombre de chapitres et la durée totale en chiffres tabulaires. Commencé,
 * il montre le chapitre en cours, le temps restant et une barre d'avancement.
 */
@Composable
private fun BookRow(
    book: Folder,
    progress: BookProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val chapters = book.songCount
    val duration = remember(book) { book.songs.sumOf { it.durationMs }.asLongDuration() }
    val status = progress?.statusLabel()
    Row(
        modifier
            .pressBounce(0.98f)
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.45f)
            .clickable(onClickLabel = "Ouvrir le livre", role = Role.Button, onClick = onClick)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Artwork(
            model = book.songs.firstOrNull()?.artworkUri,
            modifier = Modifier.size(BookCoverSize),
            shape = SynxioShape.artworkSm,
        )
        Column(Modifier.weight(1f)) {
            Text(
                book.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                status ?: "${if (chapters <= 1) "$chapters chapitre" else "$chapters chapitres"} · $duration",
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null && progress.started) {
                Spacer(Modifier.height(Space.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    BrandProgressBar(progress = progress.fraction, modifier = Modifier.weight(1f))
                    Text(
                        "${progress.percent} %",
                        style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Où en est l'écoute, sous les boutons de l'écran d'un livre : chapitre en cours et sa
 * position, barre d'avancement du livre, temps écouté et temps restant.
 */
@Composable
fun BookProgressCard(progress: BookProgress, chapter: Song?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.45f)
            .padding(Space.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (progress.finished) "Livre terminé" else progress.chapterLabel(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${progress.percent} %",
                style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                color = scheme.primary,
            )
        }
        if (chapter != null && !progress.finished) {
            Spacer(Modifier.height(Space.xxs))
            Text(
                "${chapter.title} · ${progress.chapterPositionMs.asDuration()} / ${chapter.durationMs.asDuration()}",
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Space.sm))
        BrandProgressBar(progress = progress.fraction, height = 4.dp)
        Spacer(Modifier.height(Space.xs))
        Row {
            Text(
                "${progress.elapsedMs.asLongDuration()} d'écoute",
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                progress.remainingLabel(),
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ManageAudiobookFoldersDialog(
    folders: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Un choix refusé l'était sans un mot : le dossier n'apparaissait simplement pas.
    var pickError by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val path = StorageTreePaths.pathOf(uri.authority, documentId)
        when {
            path == null -> pickError =
                "Ce dossier n'est pas sur le stockage du téléphone ou une carte SD. Choisis-le depuis le stockage interne."
            path in folders -> pickError = "Ce dossier est déjà dans la liste."
            else -> {
                pickError = null
                onAdd(path)
            }
        }
    }
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = scheme.primary) },
        title = { Text("Dossiers de livres audio", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "Les fichiers de ces dossiers n'apparaissent plus dans ta bibliothèque musicale : ils sont rangés ici.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.lg))

                if (folders.isEmpty()) {
                    Text(
                        "Aucun dossier pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = FolderListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        items(folders, key = { it }) { folder ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clip(SynxioShape.cardSm)
                                    .glass(SynxioShape.cardSm, strength = 0.5f)
                                    .padding(start = Space.md),
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                )
                                Text(
                                    text = folder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = Space.sm, vertical = Space.sm),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Retirer un dossier ne supprime aucun fichier : ils
                                // retournent simplement dans la bibliothèque musicale.
                                TextButton(onClick = { onRemove(folder) }, shape = SynxioShape.pill) {
                                    Text("Retirer")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.lg))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GlassButton(
                        text = "Ajouter un dossier…",
                        onClick = { launcher.launch(null) },
                        icon = Icons.Rounded.CreateNewFolder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                pickError?.let { error ->
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shape = SynxioShape.pill) { Text("Fermer") }
        },
    )
}
