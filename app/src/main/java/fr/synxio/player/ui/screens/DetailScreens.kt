package fr.synxio.player.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralAlbums
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.ArtworkMosaic
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SharedKeys
import fr.synxio.player.ui.components.SkeletonSongRow
import fr.synxio.player.ui.components.SongMenuHost
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.sharedNavElement
import fr.synxio.player.ui.components.shimmer
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.isDarkSurface
import fr.synxio.player.ui.theme.placeholderBrush
import fr.synxio.player.ui.theme.rememberArtworkColors
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel

/** Taille de la pochette héroïque des en-têtes de détail. */
private val HeroArtworkSize = 232.dp

/** Largeur d'une carte d'album dans le carrousel d'un artiste. */
private val AlbumCarouselCardWidth = 156.dp

/** Côté de l'image source du fond flouté : minuscule, le flou fait le reste. */
private const val BACKDROP_SOURCE_PX = 64

/**
 * Défilement (en dp) au-delà duquel le grand titre de l'en-tête est passé sous la barre :
 * le titre réapparaît alors dans la barre, en petit.
 */
private val TitleRevealScroll = 300.dp

/**
 * Écran générique « une pochette, un titre, une liste de morceaux ».
 * Sert pour les albums, genres, dossiers, playlists et sélections.
 *
 * [meta] remplace la ligne de statistiques par défaut (n titres · durée) quand l'appelant
 * veut y glisser d'autres informations (année d'un album…). [onSubtitleClick] rend le
 * sous-titre actionnable (ex. ouvrir l'artiste depuis un album).
 *
 * [eyebrow] est le surtitre de l'en-tête (« ALBUM », « PLAYLIST »…). Sans lui, il est
 * déduit du sous-titre pour les appelants qui passent la nature de la liste comme
 * sous-titre (« Genre », « Playlist à règles »…). [heroSharedKey] fait voyager la
 * pochette depuis la carte touchée (même clé des deux côtés).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    viewModel: AppViewModel,
    title: String,
    subtitle: String,
    songs: List<Song>,
    artworkModel: Any?,
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTrackNumbers: Boolean = false,
    onOpenAlbum: ((Long) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    onRemoveSong: ((Song) -> Unit)? = null,
    topBarActions: @Composable () -> Unit = {},
    headerExtras: @Composable () -> Unit = {},
    meta: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    subtitleClickLabel: String? = null,
    eyebrow: String? = null,
    heroSharedKey: Any? = null,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val stats = remember(songs, meta) {
        meta ?: "${songs.size.pluralSongs()} · ${songs.sumOf { it.durationMs }.asLongDuration()}"
    }
    // Les écrans appelés depuis la navigation passent la nature de la liste en sous-titre :
    // elle devient le surtitre, et le sous-titre ne la répète pas.
    val resolvedEyebrow = eyebrow ?: inferEyebrow(subtitle)
    val shownSubtitle = if (eyebrow == null && subtitle.equals(resolvedEyebrow, ignoreCase = true)) "" else subtitle

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DetailTopBar(
                title = title,
                onBack = onBack,
                listState = listState,
                scrollBehavior = scrollBehavior,
                actions = { topBarActions() },
            )
        },
    ) { padding ->
        when {
            songs.isEmpty() && !library.hasScanned -> {
                DetailLoading(Modifier.padding(padding))
                return@Scaffold
            }
            songs.isEmpty() -> {
                EmptyState(
                    title = "Rien ici",
                    subtitle = "Cette sélection ne contient aucun titre.",
                    modifier = Modifier.padding(padding),
                    actionLabel = "Retour",
                    onAction = onBack,
                )
                return@Scaffold
            }
        }

        // Pas de marge haute : l'en-tête et son fond flouté passent sous la barre
        // transparente, qui ne devient verre qu'une fois le contenu défilé dessous.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Space.bottomInset),
        ) {
            item(key = "header", contentType = "header") {
                DetailHero(
                    artworkModel = artworkModel,
                    eyebrow = resolvedEyebrow,
                    title = title,
                    subtitle = shownSubtitle,
                    stats = stats,
                    onPlay = { viewModel.play(songs) },
                    onShuffle = { viewModel.shufflePlay(songs) },
                    topInset = padding.calculateTopPadding(),
                    listState = listState,
                    sharedKey = heroSharedKey,
                    onSubtitleClick = onSubtitleClick,
                    subtitleClickLabel = subtitleClickLabel,
                    extras = headerExtras,
                )
            }

            // Clé « id#occurrence » : un titre présent deux fois dans une playlist donnait
            // deux clés identiques, et Compose plantait à l'ouverture de la liste.
            itemsIndexed(
                songs.withOccurrenceKeys(),
                key = { _, it -> it.first },
                contentType = { _, _ -> "song" },
            ) { index, (key, song) ->
                val isCurrent = currentSongId == song.id
                SongRow(
                    song = song,
                    onClick = { viewModel.playSong(song, songs) },
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    isFavorite = song.id in favorites,
                    showArtwork = !showTrackNumbers,
                    // Sans numéro dans les tags (0), on affiche le rang dans la liste plutôt
                    // qu'un « 0 » qui ressemble à une erreur.
                    trackNumber = (song.track.takeIf { it > 0 } ?: (index + 1)).takeIf { showTrackNumbers },
                    onLongClick = { menuSong = song },
                    onMenuClick = { menuSong = song },
                    modifier = Modifier
                        .animateItem()
                        .enterCascade(index, key)
                        .padding(horizontal = Space.sm)
                        .currentSongSemantics(isCurrent, isPlaying),
                )
            }
        }
    }

    val selected = menuSong
    SongMenuHost(
        viewModel = viewModel,
        song = selected,
        onDismiss = { menuSong = null },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onEditTags = onEditTags,
        onRemoveFromPlaylist = if (onRemoveSong != null && selected != null) {
            { onRemoveSong(selected) }
        } else null,
    )
}

/**
 * Surtitre déduit du sous-titre passé par la navigation : les genres et playlists à
 * règles y mettent leur nature, les dossiers leur chemin, les sélections leur description.
 */
private fun inferEyebrow(subtitle: String): String = when {
    subtitle.equals("Genre", ignoreCase = true) -> "Genre"
    subtitle.equals("Playlist", ignoreCase = true) -> "Playlist"
    subtitle.startsWith("Playlist à règles", ignoreCase = true) -> "Playlist à règles"
    subtitle.contains('/') -> "Dossier"
    else -> "Sélection"
}

/** Annonce le titre en cours aux lecteurs d'écran : la couleur seule ne suffit pas. */
private fun Modifier.currentSongSemantics(isCurrent: Boolean, isPlaying: Boolean): Modifier =
    if (!isCurrent) this
    else semantics { stateDescription = if (isPlaying) "En cours de lecture" else "En pause" }

/**
 * Barre du haut des écrans de détail : transparente sur l'en-tête, verre dès que la liste
 * défile dessous.
 *
 * Au repos, le titre est déjà affiché en grand dans l'en-tête : le répéter dans la barre
 * ferait doublon. Il n'apparaît (fondu et légère montée) qu'une fois le grand titre passé
 * sous la barre. [listState] nul = pas d'en-tête (chargement, introuvable) : le titre est
 * toujours visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    listState: LazyListState?,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    val threshold = with(LocalDensity.current) { TitleRevealScroll.roundToPx() }
    val showTitle by remember(listState, threshold) {
        derivedStateOf {
            listState == null ||
                listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > threshold
        }
    }
    TopAppBar(
        colors = synxioTopBarColors(),
        title = {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(Motion.standardOrSnap(reduceMotion, Motion.SHORT)) +
                    if (reduceMotion) fadeIn(Motion.standardOrSnap(true)) else slideInVertically(Motion.spatial()) { it / 2 },
                exit = fadeOut(Motion.standardOrSnap(reduceMotion, Motion.XS)) +
                    if (reduceMotion) fadeOut(Motion.standardOrSnap(true)) else slideOutVertically(Motion.exit()) { it / 2 },
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Écran sans contenu (chargement ou élément introuvable).
 *
 * Ces écrans n'ont pas de barre basse : sans la barre du haut et son bouton retour,
 * l'utilisateur resterait coincé sur un message.
 */
@Composable
private fun DetailMessageScreen(
    topBarTitle: String,
    onBack: () -> Unit,
    loading: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { DetailTopBar(title = if (loading) "" else topBarTitle, onBack = onBack, listState = null) },
    ) { padding ->
        if (loading) {
            DetailLoading(Modifier.padding(padding))
        } else {
            EmptyState(
                title = title,
                subtitle = subtitle,
                icon = icon,
                modifier = Modifier.padding(padding),
                actionLabel = "Retour",
                onAction = onBack,
            )
        }
    }
}

/**
 * Squelette d'un écran de détail pendant le premier scan : la pochette, le titre et les
 * premières lignes à leur place, pour que l'écran ne saute pas à l'arrivée du contenu.
 */
@Composable
private fun DetailLoading(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter)
            .semantics { contentDescription = "Chargement de la bibliothèque" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.sm))
        Box(Modifier.size(HeroArtworkSize).clip(SynxioShape.artworkLg).shimmer())
        Spacer(Modifier.height(Space.xl))
        Box(Modifier.fillMaxWidth(0.24f).height(10.dp).clip(SynxioShape.pill).shimmer())
        Spacer(Modifier.height(Space.md))
        Box(Modifier.fillMaxWidth(0.66f).height(26.dp).clip(SynxioShape.pill).shimmer())
        Spacer(Modifier.height(Space.sm))
        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(SynxioShape.pill).shimmer())
        Spacer(Modifier.height(Space.xl))
        repeat(5) { SkeletonSongRow() }
    }
}

/** Première image utilisable d'un modèle de pochette (une mosaïque prend sa première case). */
private fun primaryArtwork(model: Any?): Any? = if (model is List<*>) model.firstOrNull() else model

/**
 * Fond de l'en-tête : la pochette, réduite à 64 px puis floutée, qui s'efface vers le bas
 * dans l'aurore de l'application. Calculé une fois (image fixe), il ne coûte rien au
 * défilement. Avant Android 12 le flou n'existe pas, mais l'agrandissement d'une image de
 * 64 px donne déjà une matière douce.
 */
@Composable
private fun HeroBackdrop(model: Any?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isDarkSurface
    val reduce = LocalReduceMotion.current
    val source = primaryArtwork(model)
    val request = remember(source, reduce) {
        source?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(BACKDROP_SOURCE_PX)
                .crossfade(if (reduce) 0 else Motion.LONG)
                .build()
        }
    }
    val placeholder = remember(source, dark) { placeholderBrush(source, dark) }
    Box(
        modifier
            .graphicsLayer {
                // Hors écran : le masque en dégradé doit s'appliquer à toute la couche.
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = if (dark) 0.58f else 0.42f
            }
            .drawWithContent {
                drawContent()
                // Plus discret tout en haut : le bouton retour et les actions de la barre
                // doivent garder leur contraste même sur une pochette très claire.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.6f),
                        0.3f to Color.Black,
                        0.6f to Color.Black.copy(alpha = 0.65f),
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .blur(56.dp, BlurredEdgeTreatment.Rectangle),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(placeholder)
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * En-tête cinématique des écrans de détail.
 *
 * Grande pochette centrale qui éclaire la scène de sa propre couleur (lueur teintée par
 * la palette de la pochette), posée sur son double flouté ; surtitre, titre en display,
 * méta en chiffres tabulaires, puis Lire (signature) et Aléatoire (verre).
 *
 * Au défilement, la pochette recule (descend moins vite que la liste, rétrécit et
 * s'estompe) : la liste passe devant elle comme un rideau. Coupé si les animations sont
 * réduites.
 */
@Composable
private fun DetailHero(
    artworkModel: Any?,
    eyebrow: String,
    title: String,
    subtitle: String,
    stats: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    topInset: Dp,
    listState: LazyListState,
    sharedKey: Any?,
    artworkShape: Shape = SynxioShape.artworkLg,
    onSubtitleClick: (() -> Unit)? = null,
    subtitleClickLabel: String? = null,
    extras: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val palette by rememberArtworkColors(primaryArtwork(artworkModel) as? Uri)
    val glow by animateColorAsState(
        palette.vibrant ?: palette.dominant ?: scheme.primary,
        Motion.standardOrSnap(reduce, Motion.PALETTE),
        label = "heroGlow",
    )
    val cascadeKey = "hero-$title"

    Box(Modifier.fillMaxWidth()) {
        HeroBackdrop(artworkModel, Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter)
                .padding(top = topInset + Space.sm, bottom = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val artModifier = Modifier
                .size(HeroArtworkSize)
                .graphicsLayer {
                    if (reduce || listState.firstVisibleItemIndex != 0) return@graphicsLayer
                    val scrolled = listState.firstVisibleItemScrollOffset.toFloat()
                    val p = (scrolled / size.height).coerceIn(0f, 1f)
                    translationY = scrolled * 0.35f
                    scaleX = 1f - 0.14f * p
                    scaleY = 1f - 0.14f * p
                    alpha = 1f - 0.7f * p
                }
                // Lueur hors de l'élément partagé : elle reste en place pendant que la
                // pochette voyage, et s'allume quand la palette arrive.
                .glowShadow(artworkShape, glow, elevation = 36.dp)
                .then(if (sharedKey != null) Modifier.sharedNavElement(sharedKey, artworkShape) else Modifier)

            if (artworkModel is List<*>) {
                ArtworkMosaic(models = artworkModel, modifier = artModifier, shape = artworkShape)
            } else {
                Artwork(model = artworkModel, modifier = artModifier, shape = artworkShape)
            }

            Spacer(Modifier.height(Space.xl))
            Text(
                text = eyebrow.uppercase(),
                style = EyebrowTextStyle,
                color = scheme.primary,
                maxLines = 1,
                modifier = Modifier.enterCascade(1, "$cascadeKey-eyebrow"),
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .enterCascade(2, "$cascadeKey-title")
                    .semantics { heading() },
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (onSubtitleClick != null) scheme.primary else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .enterCascade(3, "$cascadeKey-subtitle")
                        .then(
                            if (onSubtitleClick != null) {
                                Modifier
                                    .clip(SynxioShape.pill)
                                    .clickable(onClickLabel = subtitleClickLabel, role = Role.Button, onClick = onSubtitleClick)
                                    .heightIn(min = Space.touchTarget)
                                    .padding(horizontal = Space.md)
                                    .wrapContentHeight(Alignment.CenterVertically)
                            } else Modifier
                        ),
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stats,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.enterCascade(3, "$cascadeKey-stats"),
            )

            Spacer(Modifier.height(Space.xl))
            Row(
                Modifier
                    .fillMaxWidth()
                    .enterCascade(4, "$cascadeKey-actions"),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                BrandButton(
                    text = "Lire",
                    onClick = onPlay,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = "Aléatoire",
                    onClick = onShuffle,
                    icon = Icons.Rounded.Shuffle,
                    modifier = Modifier.weight(1f),
                )
            }
            extras()
        }
    }
}

@Composable
fun AlbumDetailScreen(
    viewModel: AppViewModel,
    albumId: Long?,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    // Une récupération d'infos peut renommer l'album, donc changer son identifiant : on le
    // retrouve alors par ses titres, pour ne pas afficher « Album introuvable » à l'écran
    // même où l'on vient de le corriger.
    var knownSongIds by remember(albumId) { mutableStateOf<Set<Long>>(emptySet()) }
    val album = remember(albumId, library) {
        albumId?.let { viewModel.albumById(it) }
            ?: knownSongIds.takeIf { it.isNotEmpty() }?.let { ids ->
                library.albums.maxByOrNull { candidate -> candidate.songs.count { it.id in ids } }
                    ?.takeIf { candidate -> candidate.songs.any { it.id in ids } }
            }
    }
    LaunchedEffect(album) { album?.let { knownSongIds = it.songs.mapTo(HashSet()) { song -> song.id } } }
    val metadataProgress by viewModel.metadataProgress.collectAsStateWithLifecycle()

    if (album == null) {
        DetailMessageScreen(
            topBarTitle = "Album",
            onBack = onBack,
            loading = !library.hasScanned && albumId != null,
            title = "Album introuvable",
            subtitle = "Il a peut-être été supprimé depuis le dernier scan.",
            icon = Icons.Rounded.Album,
        )
        return
    }

    val meta = remember(album) {
        listOfNotNull(
            album.year.takeIf { it > 0 }?.toString(),
            album.songCount.pluralSongs(),
            album.durationMs.asLongDuration(),
        ).joinToString(" · ")
    }

    SongListScreen(
        viewModel = viewModel,
        title = album.title,
        subtitle = album.artist,
        songs = album.songs,
        artworkModel = album.artworkUri,
        onBack = onBack,
        onEditTags = onEditTags,
        showTrackNumbers = true,
        onOpenArtist = onOpenArtist,
        meta = meta,
        onSubtitleClick = { onOpenArtist(album.artist) },
        subtitleClickLabel = "Voir l'artiste",
        eyebrow = "Album",
        heroSharedKey = SharedKeys.albumArt(album.id),
        topBarActions = {
            IconButton(
                onClick = { viewModel.refetchMetadata(album.songs, album.title) },
                enabled = !metadataProgress.manual,
            ) {
                Icon(Icons.Rounded.ImageSearch, contentDescription = "Récupérer les infos et les pochettes de l'album")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    viewModel: AppViewModel,
    artistName: String,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val artist = remember(artistName, library) { viewModel.artistByName(artistName) }
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    if (artist == null) {
        DetailMessageScreen(
            topBarTitle = artistName.ifBlank { "Artiste" },
            onBack = onBack,
            loading = !library.hasScanned,
            title = "Artiste introuvable",
            subtitle = "Il a peut-être été supprimé depuis le dernier scan.",
            icon = Icons.Rounded.Person,
        )
        return
    }

    val topSongs = remember(artist) { artist.songs.sortedByDescending { it.dateAddedSec }.take(5) }
    val meta = remember(artist) {
        "${artist.albumCount.pluralAlbums()} · ${artist.songCount.pluralSongs()} · ${artist.durationMs.asLongDuration()}"
    }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DetailTopBar(
                title = artist.name,
                onBack = onBack,
                listState = listState,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Space.bottomInset),
        ) {
            item(key = "header", contentType = "header") {
                // Portrait rond, comme sur la carte d'artiste dont il arrive.
                DetailHero(
                    artworkModel = artist.artworkUri,
                    eyebrow = "Artiste",
                    title = artist.name,
                    subtitle = "",
                    stats = meta,
                    onPlay = { viewModel.play(artist.songs) },
                    onShuffle = { viewModel.shufflePlay(artist.songs) },
                    topInset = padding.calculateTopPadding(),
                    listState = listState,
                    sharedKey = SharedKeys.artistArt(artist.name),
                    artworkShape = CircleShape,
                )
            }

            item(key = "recent-title", contentType = "section") {
                SectionHeader("Titres récents", Modifier.padding(top = Space.sm), eyebrow = "Derniers ajouts")
            }
            itemsIndexed(topSongs, key = { _, it -> "top-${it.id}" }, contentType = { _, _ -> "song" }) { index, song ->
                val isCurrent = currentSongId == song.id
                SongRow(
                    song = song,
                    onClick = { viewModel.playSong(song, artist.songs) },
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    isFavorite = song.id in favorites,
                    modifier = Modifier
                        .animateItem()
                        .enterCascade(index, "artist-top-${song.id}")
                        .padding(horizontal = Space.sm)
                        .currentSongSemantics(isCurrent, isPlaying),
                    onLongClick = { menuSong = song },
                    onMenuClick = { menuSong = song },
                )
            }

            item(key = "albums-title", contentType = "section") {
                SectionHeader("Albums", Modifier.padding(top = Space.lg), eyebrow = "Discographie")
            }
            // Carrousel de cartes : chaque pochette voyage vers l'en-tête de l'album.
            item(key = "albums", contentType = "carousel") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(artist.albums, key = { _, it -> "album-${it.id}" }) { index, album ->
                        Column(
                            Modifier
                                .animateItem()
                                .width(AlbumCarouselCardWidth)
                                .enterCascade(index, "artist-album-${album.id}")
                        ) {
                            AlbumCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id) },
                            )
                            val year = album.year.takeIf { it > 0 }?.toString()
                            if (year != null) {
                                Text(
                                    year,
                                    style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Space.sm),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SongMenuHost(
        viewModel = viewModel,
        song = menuSong,
        onDismiss = { menuSong = null },
        onOpenAlbum = onOpenAlbum,
    )
}

/** Distingue « pas encore reçu » de « reçu, mais la playlist n'existe pas ». */
private class Loaded<T>(val value: T)

@Composable
fun PlaylistDetailScreen(
    viewModel: AppViewModel,
    playlistId: Long,
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val state by produceState<Loaded<Playlist?>?>(initialValue = null, playlistId) {
        viewModel.observePlaylist(playlistId).collect { value = Loaded(it) }
    }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    val current = state?.value
    if (current == null) {
        DetailMessageScreen(
            topBarTitle = "Playlist",
            onBack = onBack,
            loading = state == null,
            title = "Playlist introuvable",
            subtitle = "Elle a peut-être été supprimée.",
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
        )
        return
    }

    SongListScreen(
        viewModel = viewModel,
        title = current.name,
        subtitle = "",
        songs = current.songs,
        artworkModel = current.artworkUris,
        onBack = onBack,
        onEditTags = onEditTags,
        onRemoveSong = { viewModel.removeFromPlaylist(current.id, it.id) },
        eyebrow = "Playlist",
        heroSharedKey = SharedKeys.playlistArt(current.id),
        topBarActions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options de la playlist")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = SynxioShape.cardSm,
                    containerColor = scheme.surfaceContainerHigh,
                ) {
                    if (current.songs.size > 1) {
                        DropdownMenuItem(
                            text = { Text("Réorganiser") },
                            leadingIcon = { Icon(Icons.Rounded.SwapVert, null) },
                            onClick = { reordering = true; menuExpanded = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { renaming = true; menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer la playlist", color = scheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = scheme.error) },
                        onClick = { confirmDelete = true; menuExpanded = false },
                    )
                }
            }
        },
    )

    if (reordering) {
        fr.synxio.player.ui.components.ReorderSongsSheet(
            title = "Réorganiser",
            songs = current.songs,
            onMove = { from, to -> viewModel.moveInPlaylist(current.id, from, to) },
            onDismiss = { reordering = false },
        )
    }

    if (renaming) {
        RenamePlaylistDialog(
            initialName = current.name,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                viewModel.renamePlaylist(current.id, name)
                viewModel.showMessage("Playlist renommée")
                renaming = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = scheme.surfaceContainerHigh,
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error) },
            title = { Text("Supprimer « ${current.name} » ?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("La playlist sera supprimée. Les titres restent dans ta bibliothèque.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        // Le ViewModel affiche lui-même la confirmation de suppression.
                        viewModel.deletePlaylist(current)
                        onBack()
                    },
                    shape = SynxioShape.pill,
                ) { Text("Supprimer", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, shape = SynxioShape.pill) { Text("Annuler") }
            },
        )
    }
}

/** Renommage avec validation en ligne : un nom vide est refusé sur place, pas en silence. */
@Composable
private fun RenamePlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var showError by remember { mutableStateOf(false) }
    val isBlank = name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Renommer la playlist", style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; showError = false },
                singleLine = true,
                label = { Text("Nom") },
                isError = showError && isBlank,
                supportingText = if (showError && isBlank) {
                    { Text("Le nom ne peut pas être vide.") }
                } else null,
                shape = SynxioShape.cardSm,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            BrandButton(
                text = "Renommer",
                onClick = { if (isBlank) showError = true else onConfirm(name.trim()) },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = SynxioShape.pill) { Text("Annuler") }
        },
    )
}

/** Associe à chaque titre une clé unique, même quand il apparaît plusieurs fois. */
private fun List<Song>.withOccurrenceKeys(): List<Pair<String, Song>> {
    val seen = HashMap<Long, Int>()
    return map { song ->
        val n = seen.merge(song.id, 1, Int::plus) ?: 1
        "${song.id}#$n" to song
    }
}
