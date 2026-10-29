package fr.synxio.player.ui.screens.nowplaying

import coil3.request.crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.NowPlayingSkin
import fr.synxio.player.data.model.RepeatMode
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.chapterLabel
import fr.synxio.player.data.model.inBookOrder
import fr.synxio.player.data.model.remainingLabel
import fr.synxio.player.playback.AudioOutput
import fr.synxio.player.playback.AudioOutputKind
import fr.synxio.player.playback.openSystemOutputSwitcher
import fr.synxio.player.ui.components.AddToPlaylistHost
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.AudioVisualizer
import fr.synxio.player.ui.components.BrandPlayButton
import fr.synxio.player.ui.components.HeartButton
import fr.synxio.player.ui.components.MarqueeText
import fr.synxio.player.ui.components.SharedKeys
import fr.synxio.player.ui.components.ToggleIconButton
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.components.sharedPlayerElement
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.isDarkSurface
import fr.synxio.player.ui.theme.placeholderBrush
import fr.synxio.player.ui.viewmodel.AppViewModel
import fr.synxio.player.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Lecteur plein écran « Lumen ».
 *
 * Une scène : la pochette floutée éclaire tout l'écran, la pochette nette flotte au
 * centre avec une lueur de sa propre couleur, et les commandes se posent dessous sur
 * l'aurore. Changer de morceau fait pivoter les pochettes comme des disques dans un bac ;
 * la palette de toute l'app glisse vers celle du nouveau morceau.
 */
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
    onTrim: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val rating by viewModel.currentRating.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val audioOutput by viewModel.audioOutput.collectAsStateWithLifecycle()
    val buds by viewModel.buds.collectAsStateWithLifecycle()

    val lyricsViewModel: LyricsViewModel = hiltViewModel()
    val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    // Le titre est figé à l'ouverture du sélecteur : si la piste change pendant qu'on
    // choisit la playlist, c'est bien celle qu'on écoutait qui est ajoutée.
    var playlistSong by remember { mutableStateOf<Song?>(null) }

    val song = state.currentSong
    val audiobooks by viewModel.audiobooks.collectAsStateWithLifecycle()
    val bookPath = song?.takeIf { it.isAudiobook }?.folderPath
    val bookChapters = remember(bookPath, audiobooks) {
        if (bookPath == null) emptyList() else audiobooks.filter { it.folderPath == bookPath }.inBookOrder()
    }

    LaunchedEffect(song?.id, showLyrics) {
        if (showLyrics) lyricsViewModel.load(song)
    }

    val recordAudioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    // Une seule demande par ouverture de l'app : relancer la demande à chaque changement
    // de statut faisait réapparaître la fenêtre système juste après un refus.
    var visualizerAsked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.showVisualizer, recordAudioPermission.status) {
        if (settings.showVisualizer) {
            if (recordAudioPermission.status.isGranted) {
                viewModel.setVisualizerEnabled(true)
            } else if (!visualizerAsked) {
                visualizerAsked = true
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
    val glow = LocalGlowEffects.current

    val scheme = MaterialTheme.colorScheme
    val background = scheme.background
    val art = LocalArtworkColors.current
    val colors = SynxioTheme.colors
    val paletteSpec = tween<Color>(if (reduceMotion) 0 else Motion.PALETTE, easing = Motion.Standard)
    val halo by animateColorAsState(art.vibrant ?: art.seed ?: scheme.primary, paletteSpec, label = "npHalo")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Fond : la pochette, très floutée et agrandie pour couvrir tout l'écran.
        if (song != null) {
            BlurredArtworkBackdrop(
                model = song.artworkUri,
                placeholderSeed = song.artworkUri,
                enabled = settings.blurBackground,
                drifting = state.isPlaying && !reduceMotion,
            )
        }
        // Voile de lisibilité, puis un projecteur de la couleur du morceau derrière la
        // pochette : c'est lui qui donne la profondeur de la scène.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0.30f),
                            0.5f to background.copy(alpha = 0.58f),
                            1f to background.copy(alpha = 0.94f),
                        )
                    )
                    if (glow) {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(halo.copy(alpha = 0.34f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height * 0.36f),
                                radius = size.width * 0.9f,
                            )
                        )
                    }
                }
        )

        if (settings.showVisualizer && recordAudioPermission.status.isGranted) {
            AudioVisualizer(
                fft = fft,
                barColor = colors.brandEnd.copy(alpha = 0.42f),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.32f)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
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
                .systemBarsPadding()
                .padding(bottom = Space.sm),
        ) {
            TopRow(
                context = song.displayAlbum,
                position = if (state.queue.size > 1 && state.queueIndex >= 0) {
                    "${state.queueIndex + 1} / ${state.queue.size}"
                } else null,
                onCollapse = onCollapse,
                onMenu = { showMenu = true },
            )

            // Pochette ou paroles : l'une cède la place à l'autre par un fondu avec un
            // léger recul, au lieu d'un remplacement sec.
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit())
                    } else {
                        (fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.94f)) togetherWith
                            (fadeOut(Motion.exit()) + scaleOut(Motion.exit(), targetScale = 0.96f))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
                label = "npStage",
            ) { lyrics ->
                if (lyrics) {
                    LyricsPane(
                        lyrics = lyricsState.lyrics,
                        loading = lyricsState.loading,
                        positionMs = state.positionMs,
                        onSeek = viewModel::seekTo,
                        modifier = Modifier.fillMaxSize().padding(horizontal = Space.xl),
                        offsetMs = lyricsState.offsetMs,
                        offsetLabel = lyricsState.offsetLabel,
                        emptyMessage = lyricsState.emptyMessage,
                        isTranslating = lyricsState.isTranslating,
                        targetLanguage = lyricsState.targetLanguage,
                        translationNote = lyricsState.translationNote,
                        onTranslate = { lyricsViewModel.translateTo(it) },
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Changement de piste : le nouveau titre monte, l'ancien s'efface vers
                    // le haut — le même sens que le défilement d'une file.
                    AnimatedContent(
                        targetState = song,
                        contentKey = { it.id },
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit())
                            } else {
                                (slideInVertically(Motion.spatial()) { it / 2 } + fadeIn(Motion.enter())) togetherWith
                                    (slideOutVertically(Motion.spatial()) { -it / 2 } + fadeOut(Motion.exit()))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = "npTitle",
                    ) { current ->
                        Column {
                            // Titre : album, artiste : page artiste.
                            MarqueeText(
                                text = current.title,
                                style = MaterialTheme.typography.headlineMedium,
                                animate = state.isPlaying,
                                modifier = Modifier.clickable(onClickLabel = "Ouvrir l'album") {
                                    onCollapse(); onOpenAlbum(current.albumId)
                                },
                            )
                            Spacer(modifier = Modifier.height(Space.xxs))
                            Text(
                                text = current.displayArtist,
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(onClickLabel = "Ouvrir l'artiste") {
                                    onCollapse(); onOpenArtist(current.displayArtist)
                                },
                            )
                        }
                    }

                    HeartButton(
                        isFavorite = song.id in favorites,
                        onToggle = { viewModel.toggleFavorite(song) },
                    )
                }

                // Livre audio : le chapitre seul ne dit pas où l'on en est dans le livre.
                val book = remember(bookChapters, song.id, state.positionMs) {
                    fr.synxio.player.data.model.BookProgress.live(bookChapters, song.id, state.positionMs)
                }
                if (book != null) {
                    Text(
                        text = "${book.chapterLabel()} · ${book.percent} % · ${book.remainingLabel()}",
                        style = MaterialTheme.typography.bodyMedium.merge(fr.synxio.player.ui.theme.TabularNumbers),
                        color = scheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Hors flux et radios : la note est attachée à un titre de la bibliothèque.
                if (song.id > 0) {
                    fr.synxio.player.ui.components.StarRating(
                        rating = rating,
                        onRate = viewModel::rateCurrent,
                        modifier = Modifier.fillMaxWidth().offset(x = (-9).dp),
                    )
                } else {
                    Spacer(Modifier.height(Space.lg))
                }

                SeekBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs.takeIf { it > 0 } ?: song.durationMs,
                    onSeek = viewModel::seekTo,
                )

                Spacer(Modifier.height(Space.sm))

                MainControls(
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    shuffle = state.shuffleEnabled,
                    repeatMode = state.repeatMode,
                    hasNext = state.hasNext,
                    onShuffle = viewModel::toggleShuffle,
                    onRepeat = viewModel::cycleRepeat,
                    onPrevious = viewModel::previous,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                )

                Spacer(Modifier.height(Space.lg))

                ActionBar(
                    lyricsShown = showLyrics,
                    sleepTimerActive = sleepTimer.active,
                    sleepRemainingMs = sleepTimer.remainingMs,
                    queueCount = (state.queue.size - state.queueIndex - 1).coerceAtLeast(0),
                    onLyrics = { showLyrics = !showLyrics },
                    onQueue = { showQueue = true },
                    onSleepTimer = { showSleepTimer = true },
                    onEqualizer = onOpenEqualizer,
                )

                AudioOutputPill(output = audioOutput, buds = buds)
            }
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
            onSaveAsPlaylist = viewModel::saveQueueAsPlaylist,
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
            showName = song?.let { s ->
                when {
                    s.isAudiobook -> s.folderName
                    s.isPodcast -> s.album.ifBlank { s.folderName }
                    else -> null
                }
            },
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
            onAddToPlaylist = { playlistSong = song },
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

    AddToPlaylistHost(viewModel, playlistSong?.let(::listOf)) { playlistSong = null }
}

/** Bouton rond en verre de la barre du haut (réduire, menu). */
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp = 26.dp,
) {
    Box(
        Modifier
            .pressBounce(0.9f)
            .size(44.dp)
            .clip(CircleShape)
            .glass(CircleShape, strength = 0.6f)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun TopRow(context: String, position: String?, onCollapse: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.sm, start = Space.lg, end = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.Rounded.ExpandMore, "Réduire le lecteur", onCollapse, iconSize = 30.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (position != null) "EN LECTURE · $position" else "EN LECTURE",
                style = EyebrowTextStyle.merge(TabularNumbers),
                // Neutre et non accent : posé sur la pochette floutée, l'accent tiré de la
                // même pochette se fondait dans le fond (contraste mesuré bien sous 4,5:1).
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                maxLines = 1,
            )
            if (context.isNotBlank()) {
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = context,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GlassIconButton(Icons.Rounded.MoreVert, "Plus d'options", onMenu)
    }
}

/**
 * Pochette floutée en fond. `Modifier.blur` n'existe qu'à partir d'Android 12 : avant,
 * l'image est simplement décodée en très petit puis agrandie, ce qui la floute aussi.
 */
@Composable
private fun BlurredArtworkBackdrop(model: Any?, placeholderSeed: Any?, enabled: Boolean, drifting: Boolean) {
    if (!enabled) return
    // Dérive lente pendant l'écoute : le fond vit avec la musique, sans jamais attirer
    // l'œil. Arrêtée en pause et quand les animations sont réduites.
    val drift = rememberInfiniteTransition(label = "drift")
    val phase by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = EaseInOut), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "driftPhase",
    )
    val amount by animateFloatAsState(if (drifting) 1f else 0f, tween(Motion.LONG * 3), label = "driftAmount")
    val context = androidx.compose.ui.platform.LocalContext.current
    val request = remember(model) {
        coil3.request.ImageRequest.Builder(context)
            .data(model)
            .size(64)
            .crossfade(Motion.LONG)
            .build()
    }
    val dark = isDarkSurface
    Box(
        Modifier
            .fillMaxSize()
            .background(placeholderBrush(placeholderSeed, dark), alpha = 0.6f)
    ) {
        coil3.compose.AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1.25f + 0.12f * phase * amount
                    scaleX = s
                    scaleY = s
                    translationX = (phase - 0.5f) * size.width * 0.06f * amount
                    rotationZ = (phase - 0.5f) * 6f * amount
                }
                .blur(60.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle),
        )
    }
}

/**
 * Accès directs : paroles, file d'attente, minuterie, égaliseur.
 *
 * Un panneau de verre les garde à portée de pouce, avec leur état visible (minuterie
 * en cours, nombre de titres à venir).
 */
@Composable
private fun ActionBar(
    lyricsShown: Boolean,
    sleepTimerActive: Boolean,
    sleepRemainingMs: Long,
    queueCount: Int,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onSleepTimer: () -> Unit,
    onEqualizer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SynxioShape.pill)
            .glass(SynxioShape.pill, strength = 0.55f)
            .padding(horizontal = Space.xs, vertical = Space.xxs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionChip(Icons.Rounded.Lyrics, "Paroles", active = lyricsShown, onClick = onLyrics)
        ActionChip(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = if (queueCount > 0) "File · $queueCount" else "File",
            active = false,
            onClick = onQueue,
        )
        ActionChip(
            icon = Icons.Rounded.Bedtime,
            label = if (sleepTimerActive) sleepRemainingMs.asDuration() else "Minuteur",
            active = sleepTimerActive,
            onClick = onSleepTimer,
        )
        ActionChip(Icons.Rounded.GraphicEq, "Égaliseur", active = false, onClick = onEqualizer)
    }
}

/**
 * Sortie audio active, sous la barre d'actions : n'apparaît que lorsque la musique part
 * ailleurs que dans le haut-parleur (casque, Bluetooth, voiture). Un toucher ouvre le
 * sélecteur de sortie du système. Des Galaxy Buds s'affichent par leur modèle, avec
 * leur batterie quand elle est connue.
 */
@Composable
private fun AudioOutputPill(output: AudioOutput, buds: fr.synxio.player.buds.BudsState?) {
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // Pendant la sortie, la pastille garde le dernier appareil au lieu de se vider.
    var shown by remember { mutableStateOf(output) }
    if (output.isExternal) shown = output
    val shownBuds = buds?.takeIf { it.name == shown.name }
    val label = shownBuds?.let { b ->
        b.model.displayName + (b.batteryPercent?.let { " · $it %" } ?: "")
    } ?: shown.name

    AnimatedVisibility(
        visible = output.isExternal,
        enter = if (reduce) fadeIn(Motion.enter(Motion.SHORT))
        else fadeIn(Motion.enter()) + expandVertically(Motion.spatial(), expandFrom = Alignment.Top),
        exit = if (reduce) fadeOut(Motion.exit())
        else fadeOut(Motion.exit()) + shrinkVertically(Motion.exit(), shrinkTowards = Alignment.Top),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = Space.sm), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .pressBounce(0.95f)
                    .heightIn(min = 32.dp)
                    .clip(SynxioShape.pill)
                    .glass(SynxioShape.pill, strength = 0.45f)
                    .clickable(role = Role.Button, onClickLabel = "Changer de sortie audio") {
                        openSystemOutputSwitcher(context)
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Lecture sur " + (shownBuds?.let { b ->
                            b.model.displayName + (b.batteryPercent?.let { ", batterie $it %" } ?: "")
                        } ?: shown.name)
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (shownBuds != null) Icons.Rounded.Earbuds else shown.kind.icon(),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
    }
}

private fun AudioOutputKind.icon(): ImageVector = when (this) {
    AudioOutputKind.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    AudioOutputKind.CAR -> Icons.Rounded.DirectionsCar
    AudioOutputKind.WIRED, AudioOutputKind.USB -> Icons.Rounded.Headphones
    AudioOutputKind.HEARING_AID -> Icons.Rounded.Hearing
    AudioOutputKind.HDMI -> Icons.Rounded.Tv
    AudioOutputKind.SPEAKER, AudioOutputKind.OTHER -> Icons.Rounded.Speaker
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val tint by animateColorAsState(
        if (active) scheme.primary else scheme.onSurface.copy(alpha = 0.78f),
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "actionChipTint",
    )
    val underline by animateFloatAsState(if (active) 1f else 0f, Motion.spatialOrSnap(reduce), label = "actionChipLine")
    val brand = SynxioTheme.colors.brandBrushHorizontal
    Column(
        modifier = Modifier
            .pressBounce(0.92f)
            .clip(SynxioShape.pill)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { if (active) stateDescription = "actif" }
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
            color = tint,
            maxLines = 1,
        )
        // Filet de signature sous l'action active.
        Box(
            Modifier
                .padding(top = 3.dp)
                .height(2.dp)
                .width(18.dp * underline)
                .clip(SynxioShape.pill)
                .background(brand)
        )
    }
}

@Composable
private fun ArtworkStage(
    skin: NowPlayingSkin,
    queue: List<Song>,
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

    val reduce = LocalReduceMotion.current
    val art = LocalArtworkColors.current
    val glowColor = art.vibrant ?: art.seed ?: SynxioTheme.colors.brandEnd
    // En pause, la pochette recule un peu ; elle revient au premier plan à la reprise.
    val pauseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.6f, stiffness = 220f),
        label = "artScale",
    )
    val density = LocalDensity.current

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = Space.xl,
        contentPadding = PaddingValues(horizontal = 32.dp),
        key = { queue.getOrNull(it)?.id ?: it.toLong() },
    ) { page ->
        val song = queue[page]
        val isCurrent = page == pagerState.currentPage
        // Transition entre deux morceaux : les pochettes pivotent légèrement sur leur axe
        // et reculent en s'éloignant du centre, comme des disques feuilletés dans un bac.
        // Tout est lu dans graphicsLayer : glisser ne recompose rien.
        val stageModifier = Modifier.graphicsLayer {
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val distance = abs(offset).coerceIn(0f, 1f)
            val s = (1f - 0.18f * distance) * (if (isCurrent) pauseScale else 0.88f)
            scaleX = s
            scaleY = s
            alpha = 1f - 0.45f * distance
            if (!reduce) {
                rotationY = offset * 16f
                cameraDistance = 14f * density.density
            }
        }
        val glowFor: Modifier.(Shape) -> Modifier = { shape ->
            if (isCurrent) this.glowShadow(shape, glowColor, elevation = 34.dp) else this
        }

        Box(Modifier.fillMaxSize(), Alignment.Center) {
            when (skin) {
                NowPlayingSkin.VINYL -> VinylArtwork(
                    model = song.artworkUri,
                    songId = song.id,
                    spinning = isPlaying && isCurrent && !reduce,
                    modifier = stageModifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(1f),
                )

                NowPlayingSkin.CARD -> Box(
                    modifier = stageModifier
                        .fillMaxWidth()
                        .aspectRatio(0.9f)
                        .glowFor(RoundedCornerShape(Radius.xl))
                        .clip(RoundedCornerShape(Radius.xl))
                        .glass(RoundedCornerShape(Radius.xl), strength = 0.9f)
                        .padding(Space.lg),
                ) {
                    Artwork(
                        model = song.artworkUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .then(if (isCurrent) Modifier.sharedPlayerElement(SharedKeys.playerArt(song.id), RoundedCornerShape(Radius.lg)) else Modifier),
                        shape = RoundedCornerShape(Radius.lg),
                    )
                }

                NowPlayingSkin.MINIMAL -> Artwork(
                    model = song.artworkUri,
                    modifier = stageModifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .then(if (isCurrent) Modifier.sharedPlayerElement(SharedKeys.playerArt(song.id), SynxioShape.artworkMd) else Modifier)
                        .glowFor(SynxioShape.artworkMd),
                    shape = SynxioShape.artworkMd,
                )

                NowPlayingSkin.IMMERSIVE -> Artwork(
                    model = song.artworkUri,
                    modifier = stageModifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .then(if (isCurrent) Modifier.sharedPlayerElement(SharedKeys.playerArt(song.id), SynxioShape.artworkLg) else Modifier)
                        .glowFor(SynxioShape.artworkLg),
                    shape = SynxioShape.artworkLg,
                )
            }
        }
    }
}

@Composable
private fun VinylArtwork(model: Any?, songId: Long, spinning: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "vinyl")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "vinylAngle",
    )
    val rotation by animateFloatAsState(if (spinning) angle else 0f, label = "vinylRotation")
    val brand = SynxioTheme.colors.brandBrush

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
                .background(
                    Brush.sweepGradient(
                        listOf(
                            SynxioTheme.colors.vinyl,
                            SynxioTheme.colors.vinylGroove,
                            SynxioTheme.colors.vinyl,
                            SynxioTheme.colors.vinylGroove,
                            SynxioTheme.colors.vinyl,
                        )
                    )
                )
                .border(1.dp, brand, CircleShape)
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
                .sharedPlayerElement(SharedKeys.playerArt(songId), CircleShape)
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

/**
 * Barre de position : piste fine remplie de la signature, poignée blanche qui s'éclaire
 * et grossit sous le doigt. Pendant le glisser, le temps écoulé passe à l'accent pour
 * montrer qu'on vise une position, pas qu'on la lit.
 */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    // Toucher la durée bascule entre « durée totale » et « temps restant ».
    var showRemaining by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val reduce = LocalReduceMotion.current

    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val value = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    val elapsed = (durationMs * value).toLong()

    val thumbSize by animateDpAsState(if (dragging) 20.dp else 13.dp, Motion.spatialOrSnap(reduce), label = "seekThumb")
    val trackHeight by animateDpAsState(if (dragging) 8.dp else 5.dp, Motion.spatialOrSnap(reduce), label = "seekTrack")
    val elapsedTint by animateColorAsState(
        if (dragging) scheme.primary else scheme.onSurface.copy(alpha = 0.7f),
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "seekTime",
    )

    Column {
        Slider(
            value = value,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                onSeek((durationMs * dragValue).toLong())
                dragging = false
            },
            thumb = {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(thumbSize)
                            .glowShadow(CircleShape, colors.brandEnd, elevation = if (dragging) 14.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            },
            track = { sliderState ->
                val fraction = sliderState.value.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(SynxioShape.pill)
                        .background(scheme.onSurface.copy(alpha = 0.18f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(SynxioShape.pill)
                            .background(colors.brandBrushHorizontal)
                    )
                }
            },
            modifier = Modifier
                .height(30.dp)
                .semantics {
                    contentDescription = "Position dans le titre"
                    stateDescription = "${elapsed.asDuration()} sur ${durationMs.asDuration()}"
                },
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            val timeStyle = MaterialTheme.typography.labelMedium.merge(TabularNumbers)
            Text(text = elapsed.asDuration(), style = timeStyle, color = elapsedTint)
            Text(
                text = if (showRemaining) "-" + (durationMs - elapsed).coerceAtLeast(0).asDuration() else durationMs.asDuration(),
                style = timeStyle,
                color = scheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(SynxioShape.pill)
                    .clickable(onClickLabel = if (showRemaining) "Afficher la durée totale" else "Afficher le temps restant") {
                        showRemaining = !showRemaining
                    }
                    .padding(horizontal = Space.xs),
            )
        }
    }
}

/**
 * Aléatoire · précédent · lecture · suivant · répétition.
 *
 * Le bouton lecture signature se métamorphose (triangle ⇄ barres) ; précédent et suivant
 * donnent un petit coup dans leur direction au toucher ; aléatoire et répétition
 * rebondissent et allument un point quand ils s'activent. Un anneau signale la mise en
 * mémoire tampon.
 */
@Composable
private fun MainControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    hasNext: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIconButton(
            icon = Icons.Rounded.Shuffle,
            contentDescription = "Lecture aléatoire",
            active = shuffle,
            onClick = onShuffle,
            stateLabel = if (shuffle) "activée" else "désactivée",
        )

        SkipButton(Icons.Rounded.SkipPrevious, "Titre précédent", direction = -1, onClick = onPrevious)

        BrandPlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            size = 80.dp,
            isBuffering = isBuffering,
        )

        SkipButton(Icons.Rounded.SkipNext, "Titre suivant", direction = 1, enabled = hasNext, onClick = onNext)

        ToggleIconButton(
            icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = "Répétition",
            active = repeatMode != RepeatMode.OFF,
            onClick = onRepeat,
            stateLabel = when (repeatMode) {
                RepeatMode.OFF -> "désactivée"
                RepeatMode.ALL -> "toute la file"
                RepeatMode.ONE -> "titre en cours"
            },
        )
    }
}

/** Précédent / suivant : l'icône part d'un coup dans sa direction puis revient. */
@Composable
private fun SkipButton(
    icon: ImageVector,
    contentDescription: String,
    direction: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val reduce = LocalReduceMotion.current
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .pressBounce(0.88f)
            .size(60.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription) {
                onClick()
                if (!reduce) {
                    scope.launch {
                        nudge.snapTo(direction * 1f)
                        nudge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer { translationX = nudge.value * with(density) { 9.dp.toPx() } },
        )
    }
}
