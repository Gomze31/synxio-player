package fr.synxio.player.ui.screens

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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.synxio.player.data.model.RadioStation
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.PlayingIndicator
import fr.synxio.player.ui.components.ScreenHeader
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.ToggleIconButton
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Délai sans frappe avant d'interroger l'annuaire radio. */
private const val SEARCH_DEBOUNCE_MS = 350L

/** Logo de station : même gabarit que les vignettes de titres. */
private val StationLogoSize = 56.dp

private enum class RadioCategory(val label: String, val tag: String?) {
    FRANCE("France", null),
    FAVORITES("Favoris", null),
    WORLD("Top monde", null),
    POP("Pop & hits", "pop"),
    ROCK("Rock", "rock"),
    ELECTRO("Électro", "electro"),
    JAZZ("Jazz", "jazz"),
    CLASSICAL("Classique", "classical"),
    NEWS("Infos & talk", "news"),
}

@Composable
fun RadiosScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val topRadios by viewModel.topRadios.collectAsStateWithLifecycle()
    val favoriteRadios by viewModel.favoriteRadios.collectAsStateWithLifecycle()
    val currentSongTitle by viewModel.currentSongTitle.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(RadioCategory.FRANCE) }
    // Incrémenté par « Actualiser » / « Réessayer » pour relancer le chargement.
    var reloadKey by remember { mutableIntStateOf(0) }
    var remoteRadios by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current

    val trimmedQuery = searchQuery.trim()
    val showFavorites = selectedCategory == RadioCategory.FAVORITES && trimmedQuery.isEmpty()
    // Les favoris sont locaux et réactifs : l'étoile qu'on décoche fait disparaître la
    // station tout de suite, sans repasser par le réseau.
    val displayed = if (showFavorites) favoriteRadios else remoteRadios
    val favoriteIds = remember(favoriteRadios) { favoriteRadios.mapTo(HashSet()) { it.stationUuid } }

    // Un seul effet pilote le chargement : changer de catégorie, de requête ou demander
    // un rechargement annule la requête précédente, plus de résultats qui arrivent dans
    // le désordre.
    LaunchedEffect(
        selectedCategory,
        trimmedQuery,
        reloadKey,
        // Seule la catégorie France dépend de la liste préchargée par le ViewModel.
        if (selectedCategory == RadioCategory.FRANCE) topRadios else null,
    ) {
        if (showFavorites) {
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }
        if (trimmedQuery.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)

        val preloaded = trimmedQuery.isEmpty() && selectedCategory == RadioCategory.FRANCE &&
            reloadKey == 0 && topRadios.isNotEmpty()
        if (preloaded) {
            remoteRadios = topRadios
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }

        isLoading = true
        hasError = false
        val result = try {
            when {
                trimmedQuery.isNotEmpty() -> viewModel.searchRadios(trimmedQuery)
                // Requête vide = top France (même source que topRadios, mais rechargeable).
                selectedCategory == RadioCategory.FRANCE -> viewModel.searchRadios("")
                selectedCategory == RadioCategory.WORLD -> viewModel.getTopWorldRadios()
                else -> selectedCategory.tag?.let { viewModel.getRadiosByTag(it) }.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        remoteRadios = result
        // Le dépôt renvoie une liste vide quand aucun miroir ne répond. Pour une catégorie
        // (toujours peuplée côté annuaire), vide veut donc dire « réseau injoignable » ;
        // pour une recherche, c'est plus probablement « aucun nom ne correspond ».
        hasError = result.isEmpty() && trimmedQuery.isEmpty()
        isLoading = false
    }

    fun selectCategory(category: RadioCategory) {
        if (category != selectedCategory) {
            // Liste vidée + chargement annoncé tout de suite : sinon une image de
            // « Aucune station » passerait avant que l'effet ne démarre.
            remoteRadios = emptyList()
            isLoading = category != RadioCategory.FAVORITES
            hasError = false
        }
        selectedCategory = category
        searchQuery = ""
        focusManager.clearFocus()
    }

    val subtitle = when {
        trimmedQuery.isNotEmpty() -> "Recherche dans tout l'annuaire"
        isLoading && displayed.isEmpty() -> "Réglage des fréquences…"
        displayed.isEmpty() -> selectedCategory.label
        else -> "${selectedCategory.label} · ${displayed.size} station${if (displayed.size > 1) "s" else ""}"
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Webradios",
            eyebrow = "En direct",
            subtitle = subtitle,
            actions = {
                IconButton(
                    onClick = { reloadKey++ },
                    enabled = !showFavorites && !isLoading,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser les stations")
                }
            },
        )

        RadioSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {
                keyboard?.hide()
                focusManager.clearFocus()
            },
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            items(RadioCategory.entries, key = { it.name }) { category ->
                // Pendant une recherche, aucune catégorie n'est active : la recherche
                // porte sur tout l'annuaire.
                LumenChip(
                    label = category.label,
                    selected = selectedCategory == category && trimmedQuery.isEmpty(),
                    onClick = { selectCategory(category) },
                    icon = if (category == RadioCategory.FAVORITES) Icons.Rounded.Star else null,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // Squelettes au gabarit des lignes de station : la liste ne saute pas
                // quand les vrais résultats arrivent.
                isLoading && displayed.isEmpty() -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.sm, vertical = Space.xs)
                        .semantics { contentDescription = "Chargement des stations" },
                ) { repeat(8) { SkeletonSongRow() } }

                hasError && displayed.isEmpty() -> EmptyState(
                    title = "Impossible de joindre les serveurs radio",
                    subtitle = "Vérifie ta connexion internet, puis réessaie.",
                    icon = Icons.Rounded.CloudOff,
                    actionLabel = "Réessayer",
                    onAction = { reloadKey++ },
                )

                displayed.isEmpty() && showFavorites -> EmptyState(
                    title = "Aucune radio favorite",
                    subtitle = "Touche l'étoile d'une station pour la retrouver ici en un geste.",
                    icon = Icons.Rounded.StarBorder,
                )

                displayed.isEmpty() -> EmptyState(
                    title = "Aucune station trouvée",
                    subtitle = if (trimmedQuery.isNotEmpty()) {
                        "Rien ne correspond à « $trimmedQuery ». Essaie un autre nom ou une ville."
                    } else {
                        "Cette catégorie est vide pour le moment."
                    },
                    icon = Icons.Rounded.SearchOff,
                )

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Space.sm,
                            end = Space.sm,
                            top = Space.xs,
                            bottom = Space.bottomInset,
                        ),
                    ) {
                        itemsIndexed(
                            displayed,
                            key = { _, it -> it.stationUuid },
                            contentType = { _, _ -> "station" },
                        ) { index, station ->
                            val isCurrent = currentSongTitle == station.name
                            StationRow(
                                station = station,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isFavorite = station.stationUuid in favoriteIds,
                                onPlay = { viewModel.playRadio(station) },
                                onToggleFavorite = { viewModel.toggleRadioFavorite(station) },
                                modifier = itemMotion(reduceMotion)
                                    .enterCascade(index, "station-${station.stationUuid}"),
                            )
                        }
                    }
                    // Rechargement avec une liste déjà affichée : un fil discret à la
                    // signature en haut plutôt que d'effacer ce qu'on lisait.
                    if (isLoading) {
                        LinearProgressIndicator(
                            color = SynxioTheme.colors.brandEnd,
                            trackColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.gutter)
                                .clip(SynxioShape.pill)
                                .align(Alignment.TopCenter)
                                .semantics { contentDescription = "Actualisation des stations" },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Champ de recherche Lumen : pilule de verre, sans cadre Material au repos ; le liseré
 * prend l'accent au focus.
 */
@Composable
private fun RadioSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs)
            .clip(SynxioShape.pill)
            .glass(SynxioShape.pill, strength = 0.6f, border = false),
        placeholder = { Text("Rechercher une station (NRJ, BBC, Jazz…)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Effacer la recherche")
                }
            }
        },
        singleLine = true,
        shape = SynxioShape.pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            unfocusedBorderColor = scheme.onSurface.copy(alpha = 0.10f),
            focusedBorderColor = scheme.primary,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun StationRow(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val brand = SynxioTheme.colors.brandBrush
    Row(
        modifier = modifier
            .pressBounce(0.98f)
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(SynxioShape.artworkMd)
            .then(
                // Station à l'antenne : verre et filet de signature, comme le titre en cours.
                if (isCurrent) Modifier
                    .glass(SynxioShape.artworkMd, strength = 0.7f, border = false)
                    .drawBehind {
                        val bar = 3.dp.toPx()
                        drawRoundRect(
                            brush = brand,
                            topLeft = Offset(0f, size.height * 0.22f),
                            size = Size(bar, size.height * 0.56f),
                            cornerRadius = CornerRadius(bar, bar),
                        )
                    }
                else Modifier
            )
            .clickable(onClickLabel = "Écouter la station", role = Role.Button, onClick = onPlay)
            .semantics { if (isCurrent) stateDescription = "En cours de lecture" }
            .padding(start = Space.md, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(StationLogoSize)
                .clip(SynxioShape.artworkSm)
                .glass(SynxioShape.artworkSm, strength = 0.9f),
            contentAlignment = Alignment.Center,
        ) {
            // L'icône reste dessous : si le logo distant échoue, la vignette n'est pas vide.
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = scheme.primary,
            )
            if (station.favicon.isNotBlank()) {
                AsyncImage(
                    model = station.favicon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(Space.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isCurrent) {
                    Spacer(Modifier.width(Space.sm))
                    PlayingIndicator(isPlaying = isPlaying)
                }
            }
            Spacer(Modifier.height(Space.xxs))

            val subtitle = buildString {
                if (station.codec.isNotBlank()) append(station.codec.uppercase())
                if (station.bitrate > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${station.bitrate} kbps")
                }
                if (station.country.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(station.country)
                }
            }.ifBlank { "Flux audio direct" }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Bascule à rebond : l'étoile s'allume et un point d'état apparaît dessous.
        ToggleIconButton(
            icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            active = isFavorite,
            onClick = onToggleFavorite,
            stateLabel = if (isFavorite) "dans les favoris" else "pas dans les favoris",
        )
    }
}

/**
 * Les stations glissent en place quand la liste change (recherche, favori retiré).
 * Animations réduites : changement net.
 */
private fun LazyItemScope.itemMotion(reduceMotion: Boolean): Modifier =
    if (reduceMotion) {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem(
            fadeInSpec = Motion.enter(Motion.SHORT),
            placementSpec = Motion.spatial(),
            fadeOutSpec = Motion.exit(),
        )
    }
