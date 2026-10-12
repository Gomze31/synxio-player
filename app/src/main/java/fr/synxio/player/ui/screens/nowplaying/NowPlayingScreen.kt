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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import fr.synxio.player.data.model.RepeatMode
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.SynxioTheme
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

    // « Garder l'écran allumé » : réglage enregistré mais jamais appliqué jusqu'ici.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOnNowPlaying) {
        view.keepScreenOn = settings.keepScreenOnNowPlaying
        onDispose { view.keepScreenOn = false }
    }
    val reduceMotion = LocalReduceMotion.current

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

            // Animations réduites : le fond reste flou mais immobile.
            Artwork(
                model = song.artworkUri,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(if (reduceMotion) 1.4f else scaleAnim)
                    .rotate(if (reduceMotion) 0f else rotationAnim)
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
                    // Voile sombre léger : l'ancien « verre » floutait une boîte vide, sans effet.
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.15f)))

                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            // Titre → album, artiste → page artiste : les deux raccourcis
                            // attendus d'un lecteur, absents jusqu'ici.
                            MarqueeText(
                                text = song.title,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.clickable(onClickLabel = "Ouvrir l'album") {
                                    onCollapse(); onOpenAlbum(song.albumId)
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = song.displayArtist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(onClickLabel = "Ouvrir l'artiste") {
                                    onCollapse(); onOpenArtist(song.displayArtist)
                                },
                            )
                        }

                        FavoriteButton(
                            isFavorite = song.id in favorites,
                            onToggle = { viewModel.toggleFavorite(song) },
                        )
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
                        isBuffering = state.isBuffering,
                        shuffle = state.shuffleEnabled,
                        repeatMode = state.repeatMode,
                        onShuffle = viewModel::toggleShuffle,
                        onRepeat = viewModel::cycleRepeat,
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
                    spinning = isPlaying && isCurrent && !LocalReduceMotion.current,
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
            modifier = Modifier.semantics { contentDescription = "Position dans le titre" },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                text = (durationMs * value).toLong().asDuration(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = durationMs.asDuration(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Aléatoire · précédent · lecture · suivant · répétition.
 *
 * Aléatoire et répétition existaient côté lecteur mais n'avaient aucun bouton.
 * Le bouton lecture change de forme selon l'état (rond en pause, carré arrondi en
 * lecture) et s'enfonce sous le doigt : un retour immédiat, avant même que l'audio
 * réponde. Un anneau signale la mise en mémoire tampon.
 */
@Composable
private fun MainControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val active = scheme.primary
    val idle = scheme.onSurface.copy(alpha = 0.7f)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onShuffle,
            modifier = Modifier.semantics { stateDescription = if (shuffle) "activée" else "désactivée" },
        ) {
            Icon(Icons.Rounded.Shuffle, "Lecture aléatoire", tint = if (shuffle) active else idle)
        }

        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Titre précédent", modifier = Modifier.size(36.dp), tint = scheme.onSurface)
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (pressed && !reduceMotion) 0.92f else 1f,
            animationSpec = Motion.press(),
            label = "playPress",
        )
        val corner by animateDpAsState(
            targetValue = if (isPlaying) 26.dp else 40.dp,
            animationSpec = Motion.standardOrSnap(reduceMotion),
            label = "playShape",
        )
        Box(contentAlignment = Alignment.Center) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
            }
            Surface(
                onClick = onPlayPause,
                interactionSource = interaction,
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    },
                shape = RoundedCornerShape(corner),
                color = scheme.primary,
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Crossfade(isPlaying, animationSpec = Motion.standardOrSnap(reduceMotion, Motion.SHORT), label = "playIcon") { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Lecture",
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
        }

        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Titre suivant", modifier = Modifier.size(36.dp), tint = scheme.onSurface)
        }

        IconButton(
            onClick = onRepeat,
            modifier = Modifier.semantics {
                stateDescription = when (repeatMode) {
                    RepeatMode.OFF -> "désactivée"
                    RepeatMode.ALL -> "toute la file"
                    RepeatMode.ONE -> "titre en cours"
                }
            },
        ) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Répétition",
                tint = if (repeatMode == RepeatMode.OFF) idle else active,
            )
        }
    }
}

/** Cœur favori : petit rebond à l'activation, état annoncé aux lecteurs d'écran. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isFavorite) {
        if (isFavorite && !reduceMotion) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, Motion.press())
        }
    }
    IconButton(
        onClick = onToggle,
        modifier = Modifier.semantics { stateDescription = if (isFavorite) "dans les favoris" else "pas dans les favoris" },
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (isFavorite) SynxioTheme.colors.favorite else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}
