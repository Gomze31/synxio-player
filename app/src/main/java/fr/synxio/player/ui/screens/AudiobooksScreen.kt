package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.Folder
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.FolderRow
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Hauteur maximale de la liste des dossiers dans la boîte de dialogue. */
private val FolderListMaxHeight = 200.dp

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

    var showManageFolders by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val books = remember(audiobooks) {
        audiobooks.groupBy { it.folderPath }
            .map { (path, songs) -> Folder(path, songs.sortedBy { it.track }) }
            .sortedBy { it.name }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Livres audio") },
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
            library.isLoading && books.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Space.sm,
                    end = Space.sm,
                    top = Space.xs,
                    bottom = Space.xl,
                ),
            ) {
                items(books, key = { it.path }, contentType = { "book" }) { folder ->
                    FolderRow(
                        folder = folder,
                        onClick = { onOpenFolder(folder.path) },
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

@Composable
fun ManageAudiobookFoldersDialog(
    folders: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uri.path
            if (path != null && path.contains(":")) {
                val segments = path.split(":")
                if (segments.size == 2) {
                    val volumeId = segments[0].substringAfterLast("/")
                    val folder = segments[1]
                    val realPath = if (volumeId.equals("primary", ignoreCase = true)) {
                        "/storage/emulated/0/$folder"
                    } else {
                        "/storage/$volumeId/$folder"
                    }
                    onAdd(realPath)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
        title = { Text("Dossiers de livres audio") },
        text = {
            Column {
                Text(
                    "Les fichiers de ces dossiers n'apparaissent plus dans ta bibliothèque musicale : ils sont rangés ici.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.lg))

                if (folders.isEmpty()) {
                    Text(
                        "Aucun dossier pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = FolderListMaxHeight)) {
                        items(folders, key = { it }) { folder ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = folder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = Space.sm),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Retirer un dossier ne supprime aucun fichier : ils
                                // retournent simplement dans la bibliothèque musicale.
                                TextButton(onClick = { onRemove(folder) }) {
                                    Text("Retirer")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.lg))
                TextButton(
                    onClick = { launcher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(Space.sm))
                    Text("Ajouter un dossier…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}
