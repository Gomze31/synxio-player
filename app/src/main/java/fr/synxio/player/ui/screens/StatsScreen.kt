package fr.synxio.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArtTrack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.synxio.player.R
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran des statistiques d'écoute.
 * Affiche les statistiques de lecture : artistes, albums, morceaux les plus écoutés.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onPlaySong: (List<fr.synxio.player.data.model.Song>, Int) -> Unit
) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val context = LocalContext.current
    
    val statsSummary by statsViewModel.statsSummary.collectAsStateWithLifecycle()
    val selectedPeriod by statsViewModel.selectedPeriod.collectAsStateWithLifecycle()
    val topArtists by statsViewModel.topArtists.collectAsStateWithLifecycle()
    val topAlbums by statsViewModel.topAlbums.collectAsStateWithLifecycle()
    val topGenres by statsViewModel.topGenres.collectAsStateWithLifecycle()
    val totalPlayTimeFormatted by statsViewModel.totalPlayTimeFormatted.collectAsStateWithLifecycle()
    val playedSongsCount by statsViewModel.playedSongsCount.collectAsStateWithLifecycle()
    val unplayedSongsCount by statsViewModel.unplayedSongsCount.collectAsStateWithLifecycle()
    val playedPercentage by statsViewModel.playedPercentage.collectAsStateWithLifecycle()
    val mostPlayedSongs by statsViewModel.getMostPlayedSongs(20).collectAsStateWithLifecycle(emptyList())
    val recentlyPlayedSongs by statsViewModel.getRecentlyPlayedSongs(10).collectAsStateWithLifecycle(emptyList())
    val dailyStats by statsViewModel.dailyStats.collectAsStateWithLifecycle()
    val weekdayStats by statsViewModel.weekdayStats.collectAsStateWithLifecycle()
    
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar avec menu de période
        TopAppBar(
            title = { Text("Statistiques") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            },
            actions = {
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
                                    statsViewModel.setSelectedPeriod(period)
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
                    }
                }
            }
        )
        
        // Contenu des statistiques
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            // Section Résumé
            item {
                StatsSummarySection(
                    statsSummary = statsSummary,
                    totalPlayTimeFormatted = totalPlayTimeFormatted,
                    playedPercentage = playedPercentage,
                    period = selectedPeriod
                )
            }
            
            item { Spacer(Modifier.height(24.dp)) }
            
            // Section Top Artistes
            if (topArtists.isNotEmpty()) {
                item {
                    StatsSectionTitle(
                        title = "Top Artistes",
                        icon = Icons.Rounded.People
                    )
                }
                
                item {
                    val top3 = topArtists.take(3)
                    val maxCount = top3.maxOfOrNull { it.playCount } ?: 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        top3.forEachIndexed { index, artistStats ->
                            val progress = if (maxCount > 0) (artistStats.playCount.toFloat() / maxCount) else 0f
                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                ArtistRankCard(
                                    rank = index + 1,
                                    artist = artistStats.artist,
                                    playCount = artistStats.playCount,
                                    progress = progress,
                                    onClick = { onOpenArtist(artistStats.artist.name) }
                                )
                            }
                        }
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }
            }
            
            // Section Top Albums
            if (topAlbums.isNotEmpty()) {
                item {
                    StatsSectionTitle(
                        title = "Top Albums",
                        icon = Icons.Rounded.Album
                    )
                }
                
                item {
                    val top3 = topAlbums.take(3)
                    val maxCount = top3.maxOfOrNull { it.playCount } ?: 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        top3.forEachIndexed { index, albumStats ->
                            val progress = if (maxCount > 0) (albumStats.playCount.toFloat() / maxCount) else 0f
                            AlbumRankCard(
                                rank = index + 1,
                                album = albumStats.album,
                                playCount = albumStats.playCount,
                                progress = progress,
                                onClick = { onOpenAlbum(albumStats.album.id) }
                            )
                        }
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }
            }
            
            // Section Top Genres
            if (topGenres.isNotEmpty()) {
                item {
                    StatsSectionTitle(
                        title = "Top Genres",
                        icon = Icons.Rounded.MusicNote
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val sortedGenres = topGenres.sortedByDescending { it.second }.take(5)
                        sortedGenres.forEach { (genre, count) ->
                            GenreChip(genre = genre, count = count)
                        }
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }
            }
            
            // Section Activité récente
            if (recentlyPlayedSongs.isNotEmpty()) {
                item {
                    StatsSectionTitle(
                        title = "Récemment écoutés",
                        icon = Icons.Rounded.Timer
                    )
                }
                
                items(recentlyPlayedSongs.take(5)) { song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(recentlyPlayedSongs, recentlyPlayedSongs.indexOf(song)) },
                        
                        
                    )
                    Spacer(Modifier.height(4.dp))
                }
                
                item { Spacer(Modifier.height(16.dp)) }
            }
            
            // Section Morceaux les plus écoutés
            if (mostPlayedSongs.isNotEmpty()) {
                item {
                    StatsSectionTitle(
                        title = "Les plus écoutés",
                        icon = Icons.Rounded.Favorite
                    )
                }
                
                items(mostPlayedSongs.take(5)) { song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(mostPlayedSongs, mostPlayedSongs.indexOf(song)) },
                        
                        
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            
            // Section Graphiques
            item {
                Spacer(Modifier.height(24.dp))
                StatsSectionTitle(
                    title = "Activité par jour",
                    icon = Icons.Rounded.CalendarToday
                )
            }
            
            item {
                WeekdayBarChart(
                    stats = weekdayStats,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
            
            item { Spacer(Modifier.height(16.dp)) }
            
            // Section Résumé global
            item {
                TotalStatsSection(
                    totalSongs = statsSummary.totalSongs,
                    totalPlayTime = totalPlayTimeFormatted,
                    playedSongs = playedSongsCount,
                    unplayedSongs = unplayedSongsCount,
                    playedPercentage = playedPercentage
                )
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/**
 * Section de résumé des statistiques.
 */
@Composable
private fun StatsSummarySection(
    statsSummary: fr.synxio.player.data.model.StatsSummary,
    totalPlayTimeFormatted: String,
    playedPercentage: Float,
    period: StatsPeriod
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
            // Titre de la période
            Text(
                text = when (period) {
                    StatsPeriod.TODAY -> "Aujourd'hui"
                    StatsPeriod.WEEK -> "Cette semaine"
                    StatsPeriod.MONTH -> "Ce mois"
                    StatsPeriod.YEAR -> "Cette année"
                    StatsPeriod.ALL_TIME -> "Tout le temps"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Statistiques principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = statsSummary.totalSongs.toString(),
                    label = "Morceaux",
                    icon = Icons.Rounded.Audiotrack
                )
                
                StatItem(
                    value = statsSummary.totalPlayCount.toString(),
                    label = "Lectures",
                    icon = Icons.Rounded.ArtTrack
                )
                
                StatItem(
                    value = totalPlayTimeFormatted,
                    label = "Temps",
                    icon = Icons.Rounded.Timer
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Barre de progression des morceaux écoutés
            if (playedPercentage > 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Morceaux écoutés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { playedPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${playedPercentage.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Section de statistiques globales.
 */
@Composable
private fun TotalStatsSection(
    totalSongs: Int,
    totalPlayTime: String,
    playedSongs: Int,
    unplayedSongs: Int,
    playedPercentage: Float
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
            Text(
                text = "Résumé global",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Donut chart pour morceaux écoutés/non écoutés
            DonutChart(
                played = playedSongs,
                unplayed = unplayedSongs,
                modifier = Modifier.size(150.dp)
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = playedSongs.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Écoutés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = unplayedSongs.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Non écoutés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "Temps total : $totalPlayTime",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Carte de classement d'artiste.
 */
@Composable
private fun ArtistRankCard(
    rank: Int,
    artist: fr.synxio.player.data.model.Artist,
    playCount: Int,
    progress: Float,
    onClick: () -> Unit
) {
    val color = when (rank) {
        1 -> Color(0xFFFFD700) // Or
        2 -> Color(0xFFC0C0C0) // Argent
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badge de rang
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Artwork ou icône
            if (artist.artworkUri != null) {
                AsyncImage(
                    model = artist.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.People,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Nom de l'artiste
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Nombre de lectures
            Text(
                text = "$playCount lectures",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Barre de progression
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        }
    }
}

/**
 * Carte de classement d'album.
 */
@Composable
private fun AlbumRankCard(
    rank: Int,
    album: fr.synxio.player.data.model.Album,
    playCount: Int,
    progress: Float,
    onClick: () -> Unit
) {
    val color = when (rank) {
        1 -> Color(0xFFFFD700) // Or
        2 -> Color(0xFFC0C0C0) // Argent
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badge de rang
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Artwork
            if (album.artworkUri != null) {
                AsyncImage(
                    model = album.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Titre de l'album
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Nombre de lectures
            Text(
                text = "$playCount lectures",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Barre de progression
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        }
    }
}

/**
 * Puce de genre.
 */
@Composable
private fun GenreChip(genre: String, count: Int) {
    ElevatedCard(
        modifier = Modifier
            .padding(4.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Éléments de statistique.
 */
@Composable
private fun StatItem(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Titre de section des statistiques.
 */
@Composable
private fun StatsSectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Graphique en barres pour les jours de la semaine.
 */
@Composable
private fun WeekdayBarChart(stats: Map<String, Int>, modifier: Modifier = Modifier) {
    val maxCount = stats.values.maxOrNull() ?: 1
    val weekdays = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekdays.forEach { day ->
                val count = stats[day] ?: 0
                val height = if (maxCount > 0) (count.toFloat() / maxCount) else 0f
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Barre
                    Box(
                        modifier = Modifier
                            .height(150.dp * height)
                            .fillMaxWidth(0.7f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Label du jour
                    Text(
                        text = day.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Graphique Donut pour les morceaux écoutés/non écoutés.
 */
@Composable
private fun DonutChart(played: Int, unplayed: Int, modifier: Modifier = Modifier) {
    val total = played + unplayed
    val playedPercentage = if (total > 0) played.toFloat() / total else 0f
    val unplayedPercentage = if (total > 0) unplayed.toFloat() / total else 0f
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Cercle de fond
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(surfaceContainerColor)
        )
        
        // Cercle pour les morceaux écoutés
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2
                val centerY = canvasHeight / 2
                val radius = canvasWidth / 2 * 0.8f
                
                // Dessiner l'arc pour les morceaux écoutés
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = playedPercentage * 360,
                    useCenter = false,
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius)
                )
                
                // Dessiner l'arc pour les morceaux non écoutés
                drawArc(
                    color = onSurfaceVariantColor,
                    startAngle = -90f + playedPercentage * 360,
                    sweepAngle = unplayedPercentage * 360,
                    useCenter = false,
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius)
                )
            }
        }
        
        // Texte central
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(playedPercentage * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Écoutés",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
