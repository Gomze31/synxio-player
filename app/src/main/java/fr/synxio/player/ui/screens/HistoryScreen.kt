package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArtTrack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.synxio.player.R
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.PlayHistory
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.model.formatDate
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran de l'historique de lecture.
 * Affiche l'historique des morceaux écoutés avec filtres par période.
 */
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onPlaySong: (List<fr.synxio.player.data.model.Song>, Int) -> Unit
) {
    val historyViewModel: HistoryViewModel = hiltViewModel()
    
    val history by historyViewModel.filteredHistory.collectAsStateWithLifecycle()
    val periodFilter by historyViewModel.periodFilter.collectAsStateWithLifecycle()
    val historyCount by historyViewModel.historyCount.collectAsStateWithLifecycle()
    val dailyStats by historyViewModel.dailyStats.collectAsStateWithLifecycle()
    
    var expanded by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PlayHistory?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Historique") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            },
            actions = {
                // Menu de filtre
                Box {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Période")
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        StatsPeriod.entries.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period.label) },
                                onClick = {
                                    historyViewModel.setPeriodFilter(period)
                                    expanded = false
                                },
                                leadingIcon = {
                                    val icon = when (period) {
                                        StatsPeriod.TODAY -> Icons.Rounded.CalendarToday
                                        StatsPeriod.WEEK -> Icons.Rounded.Timer
                                        StatsPeriod.MONTH -> Icons.Rounded.Timer
                                        StatsPeriod.YEAR -> Icons.Rounded.CalendarToday
                                        StatsPeriod.ALL_TIME -> Icons.Rounded.Tune
                                    }
                                    Icon(icon, contentDescription = null)
                                }
                            )
                        }
                        
                        androidx.compose.material3.HorizontalDivider()
                        
                        DropdownMenuItem(
                            text = { Text("Effacer l'historique", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showClearDialog = true
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        )
        
        // Contenu
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            // Section Résumé
            item {
                HistorySummarySection(
                    period = periodFilter,
                    historyCount = historyCount,
                    dailyStats = dailyStats
                )
            }
            
            item { Spacer(Modifier.height(16.dp)) }
            
            // Section Résultat vide
            if (history.isEmpty()) {
                item {
                    EmptyHistorySection()
                }
            } else {
                // Liste de l'historique
                items(history, key = { it.playedAt.toEpochMilli() }) { item ->
                    val date = item.playedAt.formatDate()
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(Date(item.playedAt.toEpochMilli()))
                    
                    // Afficher la date comme en-tête de section
                    if (history.indexOf(item) == 0 || 
                        item.playedAt.formatDate() != history[history.indexOf(item) - 1].playedAt.formatDate()) {
                        StickyDateHeader(date = date)
                    }
                    
                    HistoryItem(
                        history = item,
                        time = time,
                        onPlay = { onPlaySong(listOf(item.song), 0) },
                        onOpenArtist = { onOpenArtist(item.song.artist) },
                        onOpenAlbum = { onOpenAlbum(item.song.albumId) }
                    )
                    
                    // Séparateur
                    if (history.indexOf(item) < history.size - 1) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
    
    // Dialog de confirmation d'effacement
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Effacer l'historique") },
            text = { Text("Êtes-vous sûr de vouloir effacer tout l'historique de lecture ? Cette action ne peut pas être annulée.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyViewModel.clear()
                        showClearDialog = false
                    }
                ) {
                    Text("Effacer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false }
                ) {
                    Text("Annuler")
                }
            }
        )
    }
}

/**
 * Section de résumé de l'historique.
 */
@Composable
private fun HistorySummarySection(
    period: StatsPeriod,
    historyCount: Int,
    dailyStats: Map<String, Int>
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Période sélectionnée
            Text(
                text = "${period.label}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Total des entrées
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.ArtTrack,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$historyCount écoutes",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Graphique de l'activité récente (derniers 7 jours)
            if (dailyStats.isNotEmpty()) {
                val last7Days = dailyStats.entries
                    .sortedByDescending { it.key }
                    .take(7)
                    .reversed()
                
                if (last7Days.size >= 2) {
                    MiniBarChart(
                        stats = last7Days.associate { it.key to it.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )
                }
            }
        }
    }
}

/**
 * Section historique vide.
 */
@Composable
private fun EmptyHistorySection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "Aucune écoute enregistrée",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Commencez à écouter de la musique pour voir votre historique.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * En-tête de date collant.
 */
@Composable
private fun StickyDateHeader(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Élément de l'historique.
 */
@Composable
private fun HistoryItem(
    history: PlayHistory,
    time: String,
    onPlay: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit
) {
    Card(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            if (history.song.artworkUri != null) {
                AsyncImage(
                    model = history.song.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Informations
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = history.song.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                
                Spacer(Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = history.song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                
                Spacer(Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$time - ${history.listenedMs.asDuration()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Bouton de menu
            Box {
                IconButton(
                    onClick = { /* Menu actions */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Plus d'options",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Mini graphique en barres pour l'activité quotidienne.
 */
@Composable
private fun MiniBarChart(stats: Map<String, Int>, modifier: Modifier = Modifier) {
    val maxCount = stats.values.maxOrNull() ?: 1
    val days = stats.keys.toList().reversed()
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            val count = stats[day] ?: 0
            val height = if (maxCount > 0) (count.toFloat() / maxCount) else 0f
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .height(60.dp * height)
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (count > 0 && height > 0.15) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
