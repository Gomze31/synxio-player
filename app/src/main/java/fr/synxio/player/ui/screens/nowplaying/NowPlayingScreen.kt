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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.NowPlayingSkin
import fr.synxio.player.data.model.RepeatMode
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.MarqueeText
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import androidx.compose.ui.text.font.FontWeight
import fr.synxio.player.playback.AbLoopState
import fr.synxio.player.ui.viewmodel.LyricsViewModel

/**
 * Plein écran « Lecture en cours ».
 *
 * Le fond reprend les couleurs de la pochette, l'artwork se feuillette au doigt
 * comme la file d'attente, et tout le reste est accessible en un geste depuis les
 * volets du bas (paroles, file, minuterie, vitesse).
 */
@Composable
fun NowPlayingScreen(
    viewModel: AppViewModel,
    onCollapse: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDriveMode: () -> Unit,
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

    val background = MaterialTheme.colorScheme.background
    val gradient = remember(artworkColors, background) {
        artworkColors.gradient(background)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Fond : pochette floutée + dégradé, pour que l'écran « prenne » la couleur du disque.
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
                .background(Brush.verticalGradient(gradient))
        )

        if (song == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Aucune lecture en cours", style = MaterialTheme.typography.titleMedium)
            }
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            TopRow(
                queueLabel = if (state.queueIndex >= 0)
                    "${state.queueIndex + 1} / ${state.queue.size}" else "",
                onCollapse = onCollapse,
                onMenu = { showMenu = true },
            )

            Spacer(Modifier.height(8.dp))

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
                        modifier = Modifier.fillMaxSize(),
                        offsetMs = lyricsState.offsetMs,
                        offsetLabel = lyricsState.offsetLabel,
                        emptyMessage = lyricsState.emptyMessage,
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

            Spacer(Modifier.height(16.dp))

            // Titre + favori
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                    val isFavorite = song.id in favorites
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs.takeIf { it > 0 } ?: song.durationMs,
                onSeek = viewModel::seekTo,
            )

            Spacer(Modifier.height(8.dp))

            MainControls(
                isPlaying = state.isPlaying,
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode,
                onShuffle = viewModel::toggleShuffle,
                onPrevious = viewModel::previous,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onRepeat = viewModel::cycleRepeat,
            )

            Spacer(Modifier.height(8.dp))

            SecondaryControls(
                lyricsActive = showLyrics,
                sleepTimerActive = sleepTimer.active,
                speed = state.speed,
                loopState = state.loopState,
                onLyrics = { showLyrics = !showLyrics },
                onQueue = { showQueue = true },
                onSleepTimer = { showSleepTimer = true },
                onSpeed = { showSpeed = true },
                onEqualizer = onOpenEqualizer,
                onAbLoop = viewModel::cycleAbLoop,
            )

            Spacer(Modifier.height(16.dp))
        }
    }

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

    // Les feuilles vivent hors du Box : le `return@Box` du cas « rien en lecture »
    // ne les couvre pas, d'où le test explicite.
    if (showMenu && song != null) {
        NowPlayingMenuSheet(
            song = song,
            onOpenAlbum = { onOpenAlbum(song.albumId) },
            onOpenArtist = { onOpenArtist(song.displayArtist) },
            onEditTags = { onEditTags(song.id) },
            onRefreshLyrics = { lyricsViewModel.load(song, force = true); showLyrics = true },
            onShare = { viewModel.shareSong(song) },
            onOpenDriveMode = onOpenDriveMode,
            onDismiss = { showMenu = false },
        )
    }
}

@Composable
private fun TopRow(queueLabel: String, onCollapse: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Réduire le lecteur")
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = queueLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'options")
        }
    }
}

/** Pochette selon le skin choisi ; le balayage horizontal change de morceau. */
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

    // Le lecteur peut changer de piste tout seul : on recale le pager sur lui.
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
        pageSpacing = 16.dp,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) { page ->
        val song = queue[page]
        val isCurrent = page == pagerState.currentPage
        val scale by animateFloatAsState(if (isCurrent) 1f else 0.86f, label = "artScale")

        Box(Modifier.fillMaxSize(), Alignment.Center) {
            when (skin) {
                NowPlayingSkin.VINYL -> VinylArtwork(
                    model = song.artworkUri,
                    spinning = isPlaying && isCurrent,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .scale(scale),
                )

                NowPlayingSkin.CARD -> Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(0.82f)
                        .scale(scale),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    tonalElevation = 8.dp,
                    shadowElevation = 18.dp,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Artwork(
                            model = song.artworkUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(20.dp),
                        )
                    }
                }

                NowPlayingSkin.MINIMAL -> Artwork(
                    model = song.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                        .scale(scale),
                    shape = RoundedCornerShape(12.dp),
                )

                NowPlayingSkin.IMMERSIVE -> Artwork(
                    model = song.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .scale(scale),
                    shape = RoundedCornerShape(28.dp),
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
        // Ombre sous le disque
        Box(
            Modifier
                .fillMaxSize(0.95f)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .blur(16.dp)
                .offset(y = 12.dp)
        )
        // Disque vinyle avec reflets radiaux
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
            // Sillons (Grooves)
            for (i in 1..7) {
                Box(
                    Modifier
                        .fillMaxSize(0.4f + (0.6f * (i / 7f)))
                        .align(Alignment.Center)
                        .border(0.5.dp, Color.White.copy(alpha = 0.04f), CircleShape)
                )
            }
        }
        // Macaron (Artwork)
        Artwork(
            model = model,
            modifier = Modifier
                .fillMaxSize(0.42f)
                .rotate(if (spinning) angle else rotation),
            shape = CircleShape,
        )
        // Trou central
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
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            ),
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                text = (durationMs * value).toLong().asDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = durationMs.asDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MainControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shuffleTint by animateColorAsState(
        if (shuffleEnabled) scheme.primary else scheme.onSurfaceVariant,
        label = "shuffleTint",
    )
    val repeatTint by animateColorAsState(
        if (repeatMode != RepeatMode.OFF) scheme.primary else scheme.onSurfaceVariant,
        label = "repeatTint",
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, "Lecture aléatoire", tint = shuffleTint)
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                "Titre précédent",
                modifier = Modifier.size(38.dp),
            )
        }

        Surface(
            modifier = Modifier.size(74.dp),
            shape = CircleShape,
            color = scheme.primary,
            shadowElevation = 12.dp,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Titre suivant", modifier = Modifier.size(38.dp))
        }
        IconButton(onClick = onRepeat) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne
                else Icons.Rounded.Repeat,
                contentDescription = "Mode de répétition",
                tint = repeatTint,
            )
        }
    }
}

@Composable
private fun SecondaryControls(
    lyricsActive: Boolean,
    sleepTimerActive: Boolean,
    speed: Float,
    loopState: AbLoopState,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onSleepTimer: () -> Unit,
    onSpeed: () -> Unit,
    onEqualizer: () -> Unit,
    onAbLoop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Répétition A-B : le libellé remplace l'icône, car c'est l'étape du cycle
        // (« A » posé, boucle active) qui compte, pas le symbole.
        IconButton(onClick = onAbLoop) {
            Text(
                text = when (loopState) {
                    AbLoopState.OFF -> "A-B"
                    AbLoopState.START_SET -> "A·"
                    AbLoopState.LOOPING -> "A-B"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = when (loopState) {
                    AbLoopState.OFF -> scheme.onSurfaceVariant
                    else -> scheme.primary
                },
            )
        }
        IconButton(onClick = onLyrics) {
            Icon(
                Icons.Rounded.Lyrics,
                "Paroles",
                tint = if (lyricsActive) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onQueue) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                "File d'attente",
                tint = scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSleepTimer) {
            Icon(
                Icons.Rounded.Bedtime,
                "Minuterie de veille",
                tint = if (sleepTimerActive) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSpeed) {
            Icon(
                Icons.Rounded.Speed,
                "Vitesse de lecture",
                tint = if (speed != 1f) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEqualizer) {
            Icon(Icons.Rounded.Equalizer, "Égaliseur", tint = scheme.onSurfaceVariant)
        }
    }
}
