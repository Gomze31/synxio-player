package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.synxio.player.data.model.RadioStation
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RadioCategory(val label: String, val tag: String?) {
    FRANCE("France 🇫🇷", null),
    FAVORITES("Favoris ⭐", null),
    WORLD("Top Monde 🌍", null),
    POP("Pop & Hits", "pop"),
    ROCK("Rock", "rock"),
    ELECTRO("Électro", "electro"),
    JAZZ("Jazz", "jazz"),
    CLASSICAL("Classique", "classical"),
    NEWS("Infos & Talk", "news"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadiosScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val topRadios by viewModel.topRadios.collectAsStateWithLifecycle()
    val favoriteRadios by viewModel.favoriteRadios.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(RadioCategory.FRANCE) }
    var displayedRadios by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun refreshCategory(cat: RadioCategory) {
        selectedCategory = cat
        searchQuery = ""
        focusManager.clearFocus()
        if (cat == RadioCategory.FAVORITES) {
            displayedRadios = favoriteRadios
            isLoading = false
            hasError = false
            return
        }
        if (cat == RadioCategory.FRANCE) {
            displayedRadios = topRadios
            isLoading = topRadios.isEmpty()
            hasError = false
            return
        }
        coroutineScope.launch {
            isLoading = true
            hasError = false
            try {
                val results = if (cat == RadioCategory.WORLD) {
                    viewModel.getTopWorldRadios()
                } else {
                    cat.tag?.let { viewModel.getRadiosByTag(it) } ?: emptyList()
                }
                displayedRadios = results
                hasError = results.isEmpty()
            } catch (e: Exception) {
                hasError = true
            } finally {
                isLoading = false
            }
        }
    }

    // Synchronisation initiale avec les topRadios
    LaunchedEffect(topRadios, selectedCategory) {
        if (selectedCategory == RadioCategory.FRANCE && searchQuery.isBlank()) {
            displayedRadios = topRadios
            isLoading = topRadios.isEmpty()
        } else if (selectedCategory == RadioCategory.FAVORITES && searchQuery.isBlank()) {
            displayedRadios = favoriteRadios
        }
    }

    // Recherche réactive avec debounce
    fun onQueryChanged(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            refreshCategory(selectedCategory)
            return
        }
        searchJob = coroutineScope.launch {
            delay(350)
            isLoading = true
            hasError = false
            try {
                val results = viewModel.searchRadios(newQuery)
                displayedRadios = results
                hasError = false
            } catch (e: Exception) {
                hasError = true
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Webradios",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            actions = {
                IconButton(onClick = { refreshCategory(selectedCategory) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser")
                }
            }
        )

        // Barre de recherche
        OutlinedTextField(
            value = searchQuery,
            onValueChange = ::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Rechercher une station (NRJ, BBC, Jazz…)") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        // Filtres par catégorie
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioCategory.entries.forEach { category ->
                val selected = selectedCategory == category && searchQuery.isBlank()
                FilterChip(
                    selected = selected,
                    onClick = { refreshCategory(category) },
                    label = { Text(category.label) },
                    leadingIcon = if (category == RadioCategory.FAVORITES) {
                        { Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && displayedRadios.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chargement des stations…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (hasError && displayedRadios.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Impossible de joindre les serveurs radio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Vérifiez votre connexion internet puis réessayez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { refreshCategory(selectedCategory) }) {
                            Text("Réessayer")
                        }
                    }
                }
            } else if (displayedRadios.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Radio,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedCategory == RadioCategory.FAVORITES) "Aucune radio favorite pour le moment" else "Aucune station trouvée",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedRadios, key = { it.stationUuid }) { station ->
                        val isPlaying = playerState.currentSong?.title == station.name && playerState.isPlaying
                        val isFav = favoriteRadios.any { it.stationUuid == station.stationUuid }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playRadio(station) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            tonalElevation = if (isPlaying) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (station.favicon.isNotBlank()) {
                                        AsyncImage(
                                            model = station.favicon,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Radio,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.GraphicEq,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = station.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isPlaying) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape,
                                                modifier = Modifier.size(8.dp)
                                            ) {}
                                        }
                                    }

                                    val subtitle = buildString {
                                        if (station.codec.isNotBlank()) append(station.codec.uppercase())
                                        if (station.bitrate > 0) {
                                            if (isNotEmpty()) append(" • ")
                                            append("${station.bitrate} kbps")
                                        }
                                        if (station.country.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(station.country)
                                        }
                                    }.ifBlank { "Flux audio direct" }

                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { viewModel.toggleRadioFavorite(station) }) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Favori",
                                        tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
