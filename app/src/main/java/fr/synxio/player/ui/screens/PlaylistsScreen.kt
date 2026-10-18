package fr.synxio.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.PlaylistRow
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Vignette des lignes de collection : même taille que [PlaylistRow]. */
private val RowThumbSize = 52.dp
private val RowMinHeight = 64.dp

/** Mosaïque de la carte Favoris, plus grande que les lignes : c'est la vedette. */
private val FavoritesArtSize = 76.dp

/** Disque de lecture de la carte Favoris. */
private val FavoritesPlaySize = 48.dp

private val InlineBadgeSize = 64.dp

/**
 * Onglet Playlists de la bibliothèque.
 *
 * Trois familles, trois sections dans un ordre stable : les playlists de l'utilisateur
 * (dont Favoris, en vedette), ses playlists à règles, puis les sélections calculées par
 * Synxio. Chaque section garde son en-tête même vide, avec un état vide qui dit quoi
 * faire : on sait toujours où se trouvera ce qu'on crée.
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
    val scheme = MaterialTheme.colorScheme

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
        contentPadding = PaddingValues(start = Space.sm, end = Space.sm, top = Space.xs, bottom = Space.bottomInset),
    ) {
        // Création et import en tête : ce sont les deux façons d'obtenir une playlist,
        // elles doivent se voir sans chercher (l'ancien bouton flottant masquait des lignes).
        item(key = "actions", contentType = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                // Libellé court : « Nouvelle playlist » était tronqué sur un écran de 6,7".
                BrandButton(
                    text = "Créer",
                    onClick = { creating = true },
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = "Importer",
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    icon = Icons.Rounded.FileUpload,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // --- Mes playlists -------------------------------------------------------------
        item(key = "mine-title", contentType = "section") {
            SectionHeader("Mes playlists", Modifier.padding(top = Space.xs), eyebrow = "Ta sélection")
        }
        item(key = "favorites", contentType = "favorites") {
            FavoritesCard(
                artwork = favoritesArtwork,
                subtitle = if (favorites.isEmpty()) {
                    "Touche le cœur d'un titre pour l'ajouter ici"
                } else {
                    "${favorites.size.pluralSongs()} · ${favorites.sumOf { it.durationMs }.asDuration()}"
                },
                onClick = {
                    if (favorites.isEmpty()) viewModel.showMessage("Aucun favori pour l'instant")
                    else viewModel.play(favorites)
                },
                modifier = Modifier
                    .animateItem()
                    .enterCascade(0, "favorites"),
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
                    modifier = Modifier.animateItem(),
                )
            }
        }

        itemsIndexed(playlists, key = { _, it -> it.id }, contentType = { _, _ -> "row" }) { index, playlist ->
            Box(
                Modifier
                    .animateItem()
                    .enterCascade(index + 1, "playlist-${playlist.id}")
            ) {
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    onMenuClick = { menuFor = playlist },
                )
                DropdownMenu(
                    expanded = menuFor?.id == playlist.id,
                    onDismissRequest = { menuFor = null },
                    shape = SynxioShape.cardSm,
                    containerColor = scheme.surfaceContainerHigh,
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
                        text = { Text("Supprimer", color = scheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = scheme.error) },
                        onClick = { deletingPlaylist = playlist; menuFor = null },
                    )
                }
            }
        }

        // --- Playlists à règles --------------------------------------------------------
        item(key = "rules-title", contentType = "section") {
            SectionHeader(
                title = "Playlists à règles",
                modifier = Modifier.padding(top = Space.sm),
                actionLabel = "Nouvelle règle",
                onAction = onCreateRulePlaylist,
                eyebrow = "Toujours à jour",
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
                    modifier = Modifier.animateItem(),
                )
            }
        }
        itemsIndexed(rulePlaylists, key = { _, it -> "rule-${it.id}" }, contentType = { _, _ -> "row" }) { index, rule ->
            Box(
                Modifier
                    .animateItem()
                    .enterCascade(index, "rule-${rule.id}")
            ) {
                RulePlaylistRow(
                    playlist = rule,
                    onClick = { onOpenRulePlaylist(rule.id) },
                    onMenuClick = { ruleMenuFor = rule },
                )
                DropdownMenu(
                    expanded = ruleMenuFor?.id == rule.id,
                    onDismissRequest = { ruleMenuFor = null },
                    shape = SynxioShape.cardSm,
                    containerColor = scheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { onEditRulePlaylist(rule.id); ruleMenuFor = null },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = scheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = scheme.error) },
                        onClick = { deletingRule = rule; ruleMenuFor = null },
                    )
                }
            }
        }

        // --- Sélections automatiques ---------------------------------------------------
        if (smartPlaylists.isNotEmpty()) {
            item(key = "smart-title", contentType = "section") {
                SectionHeader("Sélections automatiques", Modifier.padding(top = Space.sm), eyebrow = "Par Synxio")
            }
            itemsIndexed(smartPlaylists, key = { _, it -> "smart-${it.id.name}" }, contentType = { _, _ -> "row" }) { index, smart ->
                SmartPlaylistRow(
                    playlist = smart,
                    onClick = { onOpenSmartPlaylist(smart.id) },
                    modifier = Modifier
                        .animateItem()
                        .enterCascade(index, "smart-${smart.id.name}"),
                )
            }
        }

        item(key = "bottom-space", contentType = "spacer") { Spacer(Modifier.height(Space.xxl)) }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "Nouvelle playlist",
            confirmLabel = "Créer",
            initialName = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                // La confirmation (snackbar) vient du ViewModel.
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

/**
 * Saisie d'un nom de playlist, avec erreur en ligne plutôt qu'un bouton qui ne fait rien.
 * Le champ prend le focus à l'ouverture : on vient pour taper un nom.
 */
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Box(
                Modifier
                    .size(48.dp)
                    .glowShadow(CircleShape, SynxioTheme.colors.brandEnd, elevation = 12.dp)
                    .clip(CircleShape)
                    .background(SynxioTheme.colors.brandBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = SynxioTheme.colors.onBrand,
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
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
                shape = SynxioShape.cardSm,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            BrandButton(
                text = confirmLabel,
                onClick = { if (invalid) showError = true else onConfirm(name.trim()) },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = SynxioShape.pill) { Text("Annuler") } },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = SynxioShape.pill) {
                Text("Supprimer", color = scheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = SynxioShape.pill) { Text("Annuler") } },
    )
}

/**
 * Carte vedette des Favoris : verre teinté de la signature, mosaïque des pochettes et
 * disque de lecture lumineux. Un appui lit tout de suite les favoris.
 */
@Composable
private fun FavoritesCard(
    artwork: List<Any?>,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = Space.xs, vertical = Space.xs)
            .pressBounce(0.98f)
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.8f, tint = colors.favorite)
            .clickable(onClickLabel = "Lire les favoris", role = Role.Button, onClick = onClick)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        if (artwork.isEmpty()) {
            Box(
                Modifier
                    .size(FavoritesArtSize)
                    .clip(SynxioShape.artworkMd)
                    .glass(SynxioShape.artworkMd, strength = 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colors.favorite, modifier = Modifier.size(32.dp))
            }
        } else {
            ArtworkMosaic(artwork, Modifier.size(FavoritesArtSize), SynxioShape.artworkMd)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colors.favorite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(Space.xs))
                Text("Favoris", style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            Spacer(Modifier.height(Space.xxs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(FavoritesPlaySize)
                .glowShadow(CircleShape, colors.brandEnd, elevation = 14.dp)
                .clip(CircleShape)
                .background(colors.brandBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onBrand)
        }
    }
}

/**
 * État vide compact, placé dans une section de liste.
 *
 * Même grammaire que l'état vide plein écran (pastille cerclée de la signature, titre,
 * texte, action) mais dans une carte de verre qui n'occupe pas toute la hauteur : les
 * autres sections doivent rester visibles en dessous.
 */
@Composable
private fun InlineEmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    Column(
        modifier = modifier
            .padding(horizontal = Space.md, vertical = Space.xs)
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.5f)
            .padding(horizontal = Space.lg, vertical = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(InlineBadgeSize)
                .clip(CircleShape)
                .glass(CircleShape, strength = 1f, border = false)
                .border(1.5.dp, colors.brandBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Space.md))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.md))
            GlassButton(text = actionLabel, onClick = onAction, icon = Icons.Rounded.Add)
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
            .glass(SynxioShape.artworkSm, strength = 0.8f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Gabarit des lignes de collection de cet onglet.
 *
 * Reprend exactement la géométrie de [PlaylistRow] (vignette, typo, marges, rebond au
 * toucher) pour que règles et sélections automatiques s'alignent avec les playlists.
 */
@Composable
private fun CollectionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onClickLabel: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 1,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .pressBounce(0.98f)
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
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
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
private fun SmartPlaylistRow(playlist: SmartPlaylist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CollectionRow(
        title = playlist.title,
        subtitle = "${playlist.songCount.pluralSongs()} · ${playlist.description}",
        subtitleMaxLines = 2,
        onClick = onClick,
        onClickLabel = "Ouvrir la sélection",
        modifier = modifier,
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
            IconButton(onClick = onMenuClick, modifier = Modifier.size(Space.touchTarget)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options de ${playlist.name}")
            }
        },
    )
}
