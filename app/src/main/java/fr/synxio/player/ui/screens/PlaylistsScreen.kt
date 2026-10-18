package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.repo.RulePlaylist
import fr.synxio.player.data.repo.SmartPlaylist
import fr.synxio.player.data.repo.SmartPlaylistId
import fr.synxio.player.ui.components.ArtworkMosaic
import fr.synxio.player.ui.components.PlaylistRow
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Vignette des lignes de collection : même taille que [PlaylistRow]. */
private val RowThumbSize = 52.dp
private val RowMinHeight = 64.dp
private val InlineBadgeSize = 72.dp
private val InlineIconSize = 48.dp

/**
 * Onglet Playlists de la bibliothèque.
 *
 * Trois familles, trois sections dans un ordre stable : les playlists de l'utilisateur
 * (dont Favoris), ses playlists à règles, puis les sélections calculées par Synxio.
 * Chaque section garde son en-tête même vide, avec un état vide qui dit quoi faire :
 * on sait toujours où se trouvera ce qu'on crée.
 */
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
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }
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

    val favoritesArtwork = remember(favorites) {
        favorites.asSequence().distinctBy { it.albumId }.take(4).map { it.artworkUri }.toList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.sm),
    ) {
        // Création et import en tête : ce sont les deux façons d'obtenir une playlist,
        // elles doivent se voir sans chercher (l'ancien bouton flottant masquait des lignes).
        item(key = "actions", contentType = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                FilledTonalButton(
                    onClick = { creating = true },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    shape = SynxioShape.pill,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Space.touchTarget),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    // Libellé court : « Nouvelle playlist » était tronqué sur un écran de 6,7".
                    Text("Créer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    shape = SynxioShape.pill,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Space.touchTarget),
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Importer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // --- Mes playlists -------------------------------------------------------------
        item(key = "mine-title", contentType = "section") {
            SectionHeader("Mes playlists", Modifier.padding(top = Space.lg))
        }
        item(key = "favorites", contentType = "row") {
            CollectionRow(
                title = "Favoris",
                subtitle = if (favorites.isEmpty()) {
                    "Touche le cœur d'un titre pour l'ajouter ici"
                } else {
                    "${favorites.size.pluralSongs()} · ${favorites.sumOf { it.durationMs }.asDuration()}"
                },
                onClick = {
                    if (favorites.isEmpty()) viewModel.showMessage("Aucun favori pour l'instant")
                    else viewModel.play(favorites)
                },
                onClickLabel = "Lire les favoris",
                leading = {
                    if (favoritesArtwork.isEmpty()) {
                        IconThumb(Icons.Rounded.Favorite)
                    } else {
                        ArtworkMosaic(favoritesArtwork, Modifier.size(RowThumbSize), SynxioShape.artworkSm)
                    }
                },
                trailing = {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Space.md),
                    )
                },
            )
        }

        if (playlists.isEmpty()) {
            item(key = "mine-empty", contentType = "empty") {
                InlineEmptyState(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = "Aucune playlist",
                    text = "Crée ta première playlist ou importe un fichier M3U existant.",
                    actionLabel = "Nouvelle playlist",
                    onAction = { creating = true },
                )
            }
        }

        items(playlists, key = { it.id }, contentType = { "row" }) { playlist ->
            Box(Modifier.animateItem()) {
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
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        enabled = playlist.songs.isNotEmpty(),
                        onClick = { viewModel.play(playlist.songs); menuFor = null },
                    )
                    DropdownMenuItem(
                        text = { Text("Ajouter à la file") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                        enabled = playlist.songs.isNotEmpty(),
                        onClick = { viewModel.addToQueue(playlist.songs); menuFor = null },
                    )
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { renaming = playlist; menuFor = null },
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
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { deletingPlaylist = playlist; menuFor = null },
                    )
                }
            }
        }

        // --- Playlists à règles --------------------------------------------------------
        item(key = "rules-title", contentType = "section") {
            SectionHeader(
                title = "Playlists à règles",
                modifier = Modifier.padding(top = Space.xl),
                actionLabel = "Nouvelle règle",
                onAction = onCreateRulePlaylist,
            )
        }
        if (rulePlaylists.isEmpty()) {
            item(key = "rules-empty", contentType = "empty") {
                InlineEmptyState(
                    icon = Icons.Rounded.FilterAlt,
                    title = "Aucune playlist à règles",
                    text = "Compose une playlist qui se met à jour seule à partir de critères : genre, favoris, date d'ajout…",
                    actionLabel = "Créer une règle",
                    onAction = onCreateRulePlaylist,
                )
            }
        }
        items(rulePlaylists, key = { "rule-${it.id}" }, contentType = { "row" }) { rule ->
            Box(Modifier.animateItem()) {
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

        // --- Sélections automatiques ---------------------------------------------------
        if (smartPlaylists.isNotEmpty()) {
            item(key = "smart-title", contentType = "section") {
                SectionHeader("Sélections automatiques", Modifier.padding(top = Space.xl))
            }
            items(smartPlaylists, key = { "smart-${it.id.name}" }, contentType = { "row" }) { smart ->
                SmartPlaylistRow(
                    playlist = smart,
                    onClick = { onOpenSmartPlaylist(smart.id) },
                )
            }
        }

        item(key = "bottom-space", contentType = "spacer") { Spacer(Modifier.height(Space.xxxl)) }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "Nouvelle playlist",
            confirmLabel = "Créer",
            initialName = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                creating = false
            },
        )
    }

    renaming?.let { playlist ->
        PlaylistNameDialog(
            title = "Renommer la playlist",
            confirmLabel = "Renommer",
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renamePlaylist(playlist.id, name)
                viewModel.showMessage("Playlist renommée")
                renaming = null
            },
        )
    }

    deletingPlaylist?.let { playlist ->
        ConfirmDeleteDialog(
            title = "Supprimer « ${playlist.name} » ?",
            text = "La playlist sera supprimée. Les titres restent dans ta bibliothèque.",
            onDismiss = { deletingPlaylist = null },
            onConfirm = {
                viewModel.deletePlaylist(playlist)
                deletingPlaylist = null
            },
        )
    }

    deletingRule?.let { rule ->
        ConfirmDeleteDialog(
            title = "Supprimer « ${rule.name} » ?",
            text = "La règle sera supprimée. Les titres restent dans ta bibliothèque.",
            onDismiss = { deletingRule = null },
            onConfirm = {
                viewModel.deleteRulePlaylist(rule)
                deletingRule = null
            },
        )
    }
}

/** Saisie d'un nom de playlist, avec erreur en ligne plutôt qu'un bouton qui ne fait rien. */
@Composable
private fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var showError by remember { mutableStateOf(false) }
    val invalid = name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; showError = false },
                singleLine = true,
                label = { Text("Nom") },
                isError = showError && invalid,
                supportingText = if (showError && invalid) {
                    { Text("Donne un nom à ta playlist.") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (invalid) showError = true else onConfirm(name.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * État vide compact, placé dans une section de liste.
 *
 * Même grammaire que l'état vide plein écran (pastille, titre, texte, action) mais sans
 * occuper toute la hauteur : les autres sections doivent rester visibles en dessous.
 */
@Composable
private fun InlineEmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(InlineBadgeSize)
                .background(scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(InlineIconSize))
        }
        Spacer(Modifier.height(Space.md))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Vignette-icône quand une collection n'a pas encore de pochette à montrer. */
@Composable
private fun IconThumb(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(RowThumbSize)
            .clip(SynxioShape.artworkSm)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Gabarit des lignes de collection de cet onglet.
 *
 * Reprend exactement la géométrie de [PlaylistRow] (vignette, typo, marges) pour que
 * Favoris, règles et sélections automatiques s'alignent avec les playlists manuelles.
 */
@Composable
private fun CollectionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onClickLabel: String,
    leading: @Composable () -> Unit,
    subtitleMaxLines: Int = 1,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(SynxioShape.artworkMd)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
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
    CollectionRow(
        title = playlist.title,
        subtitle = "${playlist.songCount.pluralSongs()} · ${playlist.description}",
        subtitleMaxLines = 2,
        onClick = onClick,
        onClickLabel = "Ouvrir la sélection",
        leading = {
            ArtworkMosaic(playlist.artworkUris, Modifier.size(RowThumbSize), SynxioShape.artworkSm)
        },
    )
}

/**
 * Ligne d'une playlist à règles créée par l'utilisateur.
 *
 * Même gabarit que les autres lignes ; seul un bouton d'options s'y ajoute, ces
 * playlists pouvant être modifiées et supprimées.
 */
@Composable
private fun RulePlaylistRow(
    playlist: RulePlaylist,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val artwork = remember(playlist.songs) {
        playlist.songs.asSequence().distinctBy { it.albumId }.take(4).map { it.artworkUri }.toList()
    }
    val criteria = playlist.rules.rules.size
    CollectionRow(
        title = playlist.name,
        subtitle = "${playlist.songCount.pluralSongs()} · $criteria critère${if (criteria > 1) "s" else ""}",
        onClick = onClick,
        onClickLabel = "Ouvrir la playlist à règles",
        leading = {
            if (artwork.isEmpty()) IconThumb(Icons.Rounded.FilterAlt)
            else ArtworkMosaic(artwork, Modifier.size(RowThumbSize), SynxioShape.artworkSm)
        },
        trailing = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options de ${playlist.name}")
            }
        },
    )
}
