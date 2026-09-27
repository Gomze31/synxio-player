package fr.synxio.player.ui.screens.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.NowPlayingSkin
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.AudioVisualizer
import fr.synxio.player.ui.components.MarqueeText
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.LyricsViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: AppViewModel,
    onCollapse: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDriveMode: () -> Unit,
    onOpenPartyMode: () -> Unit,
    onTrim: (fr.synxio.player.data.model.Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val artworkColors = LocalArtworkColors.current

    val lyricsViewModel: LyricsViewModel = hiltViewModel()
    val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val song = state.currentSong

    LaunchedEffect(song?.id, showLyrics) {
        if (showLyrics) lyricsViewModel.load(song)
    }

    val recordAudioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(settings.showVisualizer, recordAudioPermission.status) {
        if (settings.showVisualizer) {
            if (recordAudioPermission.status.isGranted) {
                viewModel.setVisualizerEnabled(true)
            } else {
                recordAudioPermission.launchPermissionRequest()
            }
        } else {
            viewModel.setVisualizerEnabled(false)
        }
    }

    val fft by viewModel.fftFlow.collectAsStateWithLifecycle()

    val background = MaterialTheme.colorScheme.background
    val gradient = remember(artworkColors, background) {
        artworkColors.gradient(background)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        if (settings.blurBackground && song != null) {
            val infiniteTransition = rememberInfiniteTransition(label = "fluid")
            val scaleAnim by infiniteTransition.animateFloat(
                initialValue = 1.3f,
                targetValue = 1.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(14000, easing = EaseInOut),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "fluidScale"
            )
            val rotationAnim by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(40000, easing = LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                ),
                label = "fluidRotation"
            )

            Artwork(
                model = song.artworkUri,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleAnim)
                    .rotate(rotationAnim)
                    .blur(80.dp),
                shape = RoundedCornerShape(0.dp),
            )
        }
        
        Box(
            Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(gradient), alpha = 0.65f)
        )

        if (settings.showVisualizer && recordAudioPermission.status.isGranted) {
            AudioVisualizer(
                fft = fft,
                barColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .blur(1.dp)
            )
        }

        if (song == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Aucune lecture en cours", style = MaterialTheme.typography.titleMedium)
            }
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            TopRow(
                onCollapse = onCollapse,
                onMenu = { showMenu = true },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (showLyrics) {
                    LyricsPane(
                        lyrics = lyricsState.lyrics,
                        loading = lyricsState.loading,
                        positionMs = state.positionMs,
                        onSeek = viewModel::seekTo,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        offsetMs = lyricsState.offsetMs,
                        offsetLabel = lyricsState.offsetLabel,
                        emptyMessage = lyricsState.emptyMessage,
                        isTranslating = lyricsState.isTranslating,
                        targetLanguage = lyricsState.targetLanguage,
                        onTranslate = lyricsViewModel::translateTo,
                        onRevertTranslation = lyricsViewModel::revertTranslation,
                        onNudgeOffset = lyricsViewModel::nudgeOffset,
                        onResetOffset = lyricsViewModel::resetOffset,
                    )
                } else {
                    ArtworkStage(
                        skin = settings.nowPlayingSkin,
                        queue = state.queue,
                        queueIndex = state.queueIndex,
                        isPlaying = state.isPlaying,
                        onPageChanged = viewModel::skipToQueueIndex,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Box {
                    Box(modifier = Modifier.blur(40.dp).matchParentSize().background(Color.Black.copy(alpha = 0.15f)))
                    
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            MarqueeText(
                                text = song.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = song.displayArtist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                        
                        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                            val isFavorite = song.id in favorites
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favori",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    SeekBar(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs.takeIf { it > 0 } ?: song.durationMs,
                        onSeek = viewModel::seekTo,
                    )

                    Spacer(Modifier.height(24.dp))

                    MainControls(
                        isPlaying = state.isPlaying,
                        onPrevious = viewModel::previous,
                        onPlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                    )
                }
                } // Close Box
            } // Close Surface
        } // Close Column
    } // Close Box

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.queueIndex,
            isPlaying = state.isPlaying,
            onSelect = viewModel::skipToQueueIndex,
            onRemove = viewModel::removeFromQueue,
            onMove = viewModel::moveInQueue,
            onClear = { viewModel.clearQueue(); showQueue = false },
            onDismiss = { showQueue = false },
        )
    }

    if (showSleepTimer) {
        SleepTimerSheet(
            state = sleepTimer,
            onStart = { minutes, finishTrack -> viewModel.startSleepTimer(minutes, finishTrack) },
            onCancel = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimer = false },
        )
    }

    if (showSpeed) {
        SpeedSheet(
            speed = state.speed,
            pitch = settings.playbackPitch,
            onApply = viewModel::setSpeedAndPitch,
            onDismiss = { showSpeed = false },
        )
    }

    if (showMenu && song != null) {
        NowPlayingMenuSheet(
            song = song,
            onOpenAlbum = { onOpenAlbum(song.albumId) },
            onOpenArtist = { onOpenArtist(song.displayArtist) },
            onEditTags = { onEditTags(song.id) },
            onRefreshLyrics = { lyricsViewModel.load(song, force = true); showLyrics = true },
            onShare = { viewModel.shareSong(song) },
            onOpenDriveMode = onOpenDriveMode,
            onOpenPartyMode = onOpenPartyMode,
            onShowLyrics = { showLyrics = !showLyrics },
            onShowQueue = { showQueue = true },
            onShowSleepTimer = { showSleepTimer = true },
            onShowSpeed = { showSpeed = true },
            onOpenEqualizer = onOpenEqualizer,
            onToggleKaraoke = viewModel::toggleKaraoke,
            onToggleAbLoop = viewModel::cycleAbLoop,
            onTrim = { onTrim(song) },
            onDelete = { viewModel.deleteSong(song) },
            onDismiss = { showMenu = false },
        )
    }
}

@Composable
private fun TopRow(onCollapse: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Réduire le lecteur", modifier = Modifier.size(32.dp))
        }
        
        fr.synxio.player.ui.components.CastButton()

        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'options", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ArtworkStage(
    skin: NowPlayingSkin,
    queue: List<fr.synxio.player.data.model.Song>,
    queueIndex: Int,
    isPlaying: Boolean,
    onPageChanged: (Int) -> Unit,
) {
    if (queue.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
        pageCount = { queue.size },
    )

    LaunchedEffect(queueIndex) {
        if (queueIndex >= 0 && queueIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(queueIndex)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != queueIndex) onPageChanged(pagerState.settledPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = 24.dp,
        contentPadding = PaddingValues(horizontal = 32.dp),
    ) { page ->
        val song = queue[page]
        val isCurrent = page == pagerState.currentPage
        val scale by animateFloatAsState(if (isCurrent) 1f else 0.82f, label = "artScale")

        Box(Modifier.fillMaxSize(), Alignment.Center) {
            when (skin) {
                NowPlayingSkin.VINYL -> VinylArtwork(
                    model = song.artworkUri,
                    spinning = isPlaying && isCurrent,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(1f)
                        .scale(scale),
                )

                NowPlayingSkin.CARD -> Surface(
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .aspectRatio(0.9f)
                        .scale(scale),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    tonalElevation = 12.dp,
                    shadowElevation = 24.dp,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Artwork(
                            model = song.artworkUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(24.dp),
                        )
                    }
                }

                NowPlayingSkin.MINIMAL -> Artwork(
                    model = song.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .scale(scale),
                    shape = RoundedCornerShape(16.dp),
                )

                NowPlayingSkin.IMMERSIVE -> Artwork(
                    model = song.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .aspectRatio(1f)
                        .scale(scale),
                    shape = RoundedCornerShape(32.dp),
                )
            }
        }
    }
}

@Composable
private fun VinylArtwork(model: Any?, spinning: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "vinyl")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "vinylAngle",
    )
    val rotation by animateFloatAsState(if (spinning) angle else 0f, label = "vinylRotation")

    Box(modifier, Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize(0.95f)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .blur(24.dp)
                .offset(y = 16.dp)
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.sweepGradient(
                    listOf(
                        Color(0xFF141418),
                        Color(0xFF2C2C35),
                        Color(0xFF0F0F12),
                        Color(0xFF2C2C35),
                        Color(0xFF141418)
                    )
                ))
                .rotate(if (spinning) angle else rotation)
        ) {
            for (i in 1..7) {
                Box(
                    Modifier
                        .fillMaxSize(0.4f + (0.6f * (i / 7f)))
                        .align(Alignment.Center)
                        .border(0.5.dp, Color.White.copy(alpha = 0.04f), CircleShape)
                )
            }
        }
        Artwork(
            model = model,
            modifier = Modifier
                .fillMaxSize(0.42f)
                .rotate(if (spinning) angle else rotation),
            shape = CircleShape,
        )
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
        )
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val value = if (dragging) dragValue else progress.coerceIn(0f, 1f)

    Column {
        Slider(
            value = value,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                onSeek((durationMs * dragValue).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            ),
            modifier = Modifier.height(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                text = (durationMs * value).toLong().asDuration(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = durationMs.asDuration(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MainControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                "Titre précédent",
                modifier = Modifier.size(42.dp),
                tint = scheme.onSurface
            )
        }
        
        Spacer(Modifier.width(24.dp))

        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = scheme.primary,
            shadowElevation = 16.dp,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
        
        Spacer(Modifier.width(24.dp))

        IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
            Icon(
                Icons.Rounded.SkipNext, 
                "Titre suivant", 
                modifier = Modifier.size(42.dp),
                tint = scheme.onSurface
            )
        }
    }
}
