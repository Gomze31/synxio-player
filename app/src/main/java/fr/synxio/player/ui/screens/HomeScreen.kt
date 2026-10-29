package fr.synxio.player.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.asLongDuration
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.BookProgress
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.bookToResume
import fr.synxio.player.data.model.chapterLabel
import fr.synxio.player.data.model.inBookOrder
import fr.synxio.player.data.model.remainingLabel
import fr.synxio.player.data.repo.SmartPlaylist
import fr.synxio.player.data.repo.SmartPlaylistId
import fr.synxio.player.ui.components.AlbumCard
import fr.synxio.player.ui.components.ArtistCard
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.ArtworkMosaic
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandPlayButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.PlayingIndicator
import fr.synxio.player.ui.components.ScreenHeader
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SkeletonCard
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.components.shimmer
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalArtworkColors
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
import fr.synxio.player.ui.viewmodel.AppViewModel
import java.util.Calendar

/** Nombre de titres montrés dans les carrousels « Reprendre » et « Les plus écoutés ». */
private const val CAROUSEL_SIZE = 15

/** Largeurs des cartes de carrousel : fixes pour que les rangées gardent leur rythme. */
private val SongCardWidth = 148.dp
private val AlbumCardWidth = 164.dp
private val ArtistCardWidth = 116.dp
private val SmartCardWidth = 164.dp

/** Pochette de la carte héros : assez grande pour porter la lumière de l'écran. */
private val HeroArtSize = 132.dp

/**
 * Accueil éditorial Lumen.
 *
 * Une page de magazine plutôt qu'un tableau de bord : une salutation, une carte héros
 * qui met en scène le titre en cours (ou celui à reprendre), puis des rangées de
 * pochettes. Chaque section entre en cascade, une fois, à la première ouverture.
 *
 * [onOpenSmartPlaylist] est optionnel pour garder la signature compatible : sans lui,
 * toucher une playlist intelligente la lance directement.
 */
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onSeeAll: () -> Unit,
    onSeeRecents: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSmartPlaylist: ((SmartPlaylistId) -> Unit)? = null,
    onOpenBook: ((String) -> Unit)? = null,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val bookProgress by viewModel.bookProgress.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val smartPlaylists by viewModel.smartPlaylists.collectAsStateWithLifecycle()
    val dailyMixes by viewModel.dailyMixes.collectAsStateWithLifecycle()
    // Triés par le ViewModel hors du thread principal, jamais pendant la composition.
    val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    // Seulement l'identifiant et l'état lecture/pause : playerState change 4×/s avec la
    // position et recomposerait tout l'accueil pour rien.
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // Premier scan pas encore terminé : un squelette qui a la forme de l'accueil plutôt
    // qu'un rond qui tourne au milieu du vide, l'écran ne « saute » pas à l'arrivée.
    if (library.isLoading && library.songs.isEmpty()) {
        HomeSkeleton(modifier)
        return
    }

    if (library.isEmpty) {
        EmptyState(
            title = "Aucune musique trouvée",
            subtitle = "Ajoute des fichiers audio sur ton appareil (dossier Musique ou Téléchargements), puis relance un scan.",
            icon = Icons.Rounded.LibraryMusic,
            actionLabel = "Relancer le scan",
            onAction = { viewModel.rescan() },
            modifier = modifier,
        )
        return
    }

    val resumeSongs = remember(recentlyPlayed) { recentlyPlayed.take(CAROUSEL_SIZE) }
    val topSongs = remember(mostPlayed) { mostPlayed.take(CAROUSEL_SIZE) }
    // Le titre en cours est cherché dans la bibliothèque à partir de son seul identifiant :
    // lire playerState ici recomposerait l'accueil à chaque tic de position.
    val currentSong = remember(currentSongId, library.songs) {
        currentSongId?.let { id -> library.songs.firstOrNull { it.id == id } }
    }
    val heroSong = currentSong ?: recentlyPlayed.firstOrNull()
    // Le livre entamé écouté en dernier, avec ses chapitres dans l'ordre de lecture.
    val resumeBook = remember(bookProgress, library.audiobooks) {
        bookProgress.bookToResume()?.let { (path, progress) ->
            val chapters = library.audiobooks.filter { it.folderPath == path }.inBookOrder()
            if (chapters.isEmpty()) null else Triple(path, progress, chapters)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.bottomInset + Space.xl),
    ) {
        // Rang d'apparition de chaque section : la cascade suit l'ordre de lecture de la
        // page, quelles que soient les sections absentes. Remis à zéro à chaque passage du
        // constructeur de liste, qui peut être rejoué.
        var order = 0
        val headerIndex = order++
        item(key = "greeting", contentType = "greeting") {
            GreetingHeader(
                songCount = library.songs.size,
                totalDurationMs = library.totalDurationMs,
                modifier = Modifier.enterCascade(headerIndex, "home-greeting"),
            )
        }

        val quickIndex = order++
        item(key = "quick", contentType = "quick") {
            QuickActions(
                canShuffle = library.songs.isNotEmpty(),
                onShuffleAll = { viewModel.shufflePlay(library.songs) },
                favoriteCount = favorites.size,
                onFavorites = { viewModel.play(favorites) },
                // La puce ouvre l'écran des ajouts récents au lieu de lancer la lecture :
                // « qu'est-ce qui est arrivé ? » est une question, pas une commande.
                onRecent = onSeeRecents,
                modifier = Modifier.enterCascade(quickIndex, "home-quick"),
            )
        }

        if (heroSong != null) {
            val heroIndex = order++
            item(key = "hero", contentType = "hero") {
                val isCurrent = heroSong.id == currentSongId
                HeroCard(
                    song = heroSong,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    onOpen = { if (isCurrent) onOpenPlayer() else viewModel.playSong(heroSong, recentlyPlayed) },
                    onPlayPause = {
                        if (isCurrent) viewModel.togglePlayPause() else viewModel.playSong(heroSong, recentlyPlayed)
                    },
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                        .enterCascade(heroIndex, "home-hero"),
                )
            }
        }

        if (resumeBook != null) {
            val index = order++
            val (path, progress, chapters) = resumeBook
            item(key = "book", contentType = "book") {
                HomeSection(
                    title = "Reprendre ce livre",
                    eyebrow = "Livre audio",
                    modifier = Modifier.enterCascade(index, "home-book"),
                ) {
                    ResumeBookCard(
                        name = path.substringAfterLast('/', path),
                        progress = progress,
                        chapters = chapters,
                        isPlaying = isPlaying && chapters.any { it.id == currentSongId },
                        onOpen = onOpenBook?.let { { it(path) } },
                        onResume = {
                            if (chapters.any { it.id == currentSongId }) viewModel.togglePlayPause()
                            else viewModel.resumeBook(chapters)
                        },
                    )
                }
            }
        }

        if (resumeSongs.isNotEmpty()) {
            val index = order++
            item(key = "s-resume", contentType = "songSection") {
                HomeSection(
                    title = "Reprendre l'écoute",
                    eyebrow = "Ton historique",
                    modifier = Modifier.enterCascade(index, "home-resume"),
                ) {
                    SongCarousel(
                        songs = resumeSongs,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        onClick = { song -> viewModel.playSong(song, recentlyPlayed) },
                    )
                }
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            val index = order++
            item(key = "s-added", contentType = "songSection") {
                HomeSection(
                    title = "Ajoutés récemment",
                    eyebrow = "Arrivés sur l'appareil",
                    actionLabel = "Tout voir",
                    onAction = onSeeRecents,
                    modifier = Modifier.enterCascade(index, "home-added"),
                ) {
                    SongCarousel(
                        songs = recentlyAdded,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        onClick = { song -> viewModel.playSong(song, recentlyAdded) },
                    )
                }
            }
        }

        if (topSongs.isNotEmpty()) {
            val index = order++
            item(key = "s-top", contentType = "songSection") {
                HomeSection(
                    title = "Les plus écoutés",
                    eyebrow = "Tes classiques",
                    modifier = Modifier.enterCascade(index, "home-top"),
                ) {
                    SongCarousel(
                        songs = topSongs,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        ranked = true,
                        onClick = { song -> viewModel.playSong(song, mostPlayed) },
                    )
                }
            }
        }

        if (dailyMixes.isNotEmpty()) {
            val index = order++
            item(key = "s-mixes", contentType = "smartSection") {
                HomeSection(
                    title = "Tes mixs du jour",
                    eyebrow = "Renouvelés chaque matin",
                    modifier = Modifier.enterCascade(index, "home-mixes"),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        itemsIndexed(dailyMixes, key = { _, it -> it.key }) { i, mix ->
                            MosaicCard(
                                title = mix.title,
                                subtitle = mix.subtitle,
                                artworkUris = mix.artworkUris,
                                onClickLabel = "Lancer le mix",
                                // Un mix est fait pour être écouté tel quel : le toucher
                                // le lance, dans l'ordre composé.
                                onClick = { viewModel.play(mix.songs) },
                                modifier = Modifier
                                    .animateItem()
                                    .enterCascade(i, "mix-${mix.key}"),
                            )
                        }
                    }
                }
            }
        }

        if (smartPlaylists.isNotEmpty()) {
            val index = order++
            item(key = "s-smart", contentType = "smartSection") {
                HomeSection(
                    title = "Pour toi",
                    eyebrow = "Playlists intelligentes",
                    modifier = Modifier.enterCascade(index, "home-smart"),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        itemsIndexed(smartPlaylists, key = { _, it -> it.id.name }) { i, playlist ->
                            SmartPlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    if (onOpenSmartPlaylist != null) onOpenSmartPlaylist(playlist.id)
                                    else viewModel.play(playlist.songs)
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .enterCascade(i, "smart-${playlist.id.name}"),
                            )
                        }
                    }
                }
            }
        }

        if (recentAlbums.isNotEmpty()) {
            val index = order++
            item(key = "s-albums", contentType = "albumSection") {
                HomeSection(
                    title = "Albums",
                    eyebrow = "Ta bibliothèque",
                    actionLabel = "Tout voir",
                    onAction = onSeeAll,
                    modifier = Modifier.enterCascade(index, "home-albums"),
                ) {
                    LazyRow(
                        // AlbumCard porte déjà Space.sm de marge interne : gutter = md + sm.
                        contentPadding = PaddingValues(horizontal = Space.md),
                    ) {
                        itemsIndexed(recentAlbums, key = { _, it -> it.id }) { i, album ->
                            // La pochette de la carte voyage jusqu'à l'en-tête de l'album
                            // (élément partagé posé par AlbumCard).
                            AlbumCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .enterCascade(i, album.id)
                                    .width(AlbumCardWidth),
                            )
                        }
                    }
                }
            }
        }

        if (topArtists.isNotEmpty()) {
            val index = order++
            item(key = "s-artists", contentType = "artistSection") {
                HomeSection(
                    title = "Artistes",
                    eyebrow = "Ceux que tu écoutes",
                    actionLabel = "Tout voir",
                    onAction = onSeeAll,
                    modifier = Modifier.enterCascade(index, "home-artists"),
                ) {
                    LazyRow(contentPadding = PaddingValues(horizontal = Space.md)) {
                        itemsIndexed(topArtists, key = { _, it -> it.name }) { i, artist ->
                            ArtistCard(
                                artist = artist,
                                onClick = { onOpenArtist(artist.name) },
                                modifier = Modifier
                                    .animateItem()
                                    .enterCascade(i, "artist-${artist.name}")
                                    .width(ArtistCardWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Salutation selon l'heure et grand titre éditorial, avec la taille de la bibliothèque. */
@Composable
private fun GreetingHeader(songCount: Int, totalDurationMs: Long, modifier: Modifier = Modifier) {
    // Calculée une fois par entrée sur l'écran : inutile de relire l'horloge à chaque
    // recomposition.
    val (greeting, title) = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Bonjour" to "Une matinée en musique"
            in 12..17 -> "Bon après-midi" to "De quoi tenir l'après-midi"
            in 18..22 -> "Bonsoir" to "Ce soir, on écoute quoi ?"
            else -> "Bonne nuit" to "Pour les heures calmes"
        }
    }
    ScreenHeader(
        title = title,
        eyebrow = greeting,
        subtitle = "${songCount.pluralSongs()} · ${totalDurationMs.asLongDuration()} de musique",
        modifier = modifier.padding(top = Space.sm),
    )
}

/**
 * Raccourcis de l'accueil en puces Lumen : plus compactes que des tuiles, elles laissent
 * la place aux pochettes, qui sont le vrai contenu de l'écran.
 */
@Composable
private fun QuickActions(
    canShuffle: Boolean,
    onShuffleAll: () -> Unit,
    favoriteCount: Int,
    onFavorites: () -> Unit,
    onRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (canShuffle) {
            item(key = "shuffle") {
                LumenChip(label = "Tout mélanger", selected = false, onClick = onShuffleAll, icon = Icons.Rounded.Shuffle)
            }
        }
        // Sans favori, la puce ne lancerait rien : elle est retirée plutôt que laissée muette.
        if (favoriteCount > 0) {
            item(key = "favorites") {
                LumenChip(
                    label = "Favoris · $favoriteCount",
                    selected = false,
                    onClick = onFavorites,
                    icon = Icons.Rounded.Favorite,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "recent") {
            LumenChip(label = "Ajouts récents", selected = false, onClick = onRecent, icon = Icons.Rounded.NewReleases)
        }
    }
}

/**
 * Carte héros : le titre en cours (ou celui à reprendre) mis en scène. La pochette
 * éclaire la carte : le verre est infusé de sa couleur, une lueur teintée glisse dessous,
 * un halo diffus s'étale depuis le coin. La teinte change en fondu lent d'un morceau à
 * l'autre, au rythme de l'aurore du fond.
 */
/**
 * Carte de reprise d'un livre audio : couverture, titre, chapitre en cours et temps
 * restant, barre d'avancement. Le bouton reprend au bon endroit ; la carte ouvre le livre.
 */
@Composable
private fun ResumeBookCard(
    name: String,
    progress: BookProgress,
    chapters: List<Song>,
    isPlaying: Boolean,
    onOpen: (() -> Unit)?,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.45f)
            .then(
                if (onOpen != null) {
                    Modifier.clickable(onClickLabel = "Ouvrir le livre", role = Role.Button, onClick = onOpen)
                } else Modifier
            )
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Artwork(
            model = chapters.firstOrNull()?.artworkUri,
            modifier = Modifier.size(64.dp),
            shape = SynxioShape.artworkSm,
        )
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                "${progress.chapterLabel()} · ${progress.remainingLabel()}",
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            BrandProgressBar(progress = progress.fraction)
        }
        BrandPlayButton(isPlaying = isPlaying, onClick = onResume, size = 48.dp)
    }
}

@Composable
private fun HeroCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val art = LocalArtworkColors.current
    val colors = SynxioTheme.colors
    val reduce = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme
    // Les couleurs de pochette ne valent que pour le titre en cours ; pour une reprise, la
    // signature Lumen prend le relais.
    val target = if (isCurrent) art.vibrant ?: art.seed ?: colors.brandEnd else colors.brandEnd
    val tint by animateColorAsState(
        targetValue = target,
        animationSpec = tween(if (reduce) 0 else Motion.PALETTE, easing = Motion.Standard),
        label = "heroTint",
    )
    val haloAlpha = if (isDarkSurface) 0.34f else 0.20f
    val eyebrow = when {
        !isCurrent -> "Reprendre l'écoute"
        isPlaying -> "En lecture"
        else -> "En pause"
    }

    Box(
        modifier
            .padding(horizontal = Space.gutter, vertical = Space.md)
            .fillMaxWidth()
            .pressBounce(0.98f)
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.8f, tint = tint)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = haloAlpha), Color.Transparent),
                        center = Offset(0f, size.height),
                        radius = size.maxDimension * 0.9f,
                    )
                )
            }
            .clickable(
                role = Role.Button,
                onClickLabel = if (isCurrent) "Ouvrir le lecteur" else "Reprendre",
                onClick = onOpen,
            )
            .padding(Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                model = song.artworkUri,
                modifier = Modifier
                    .size(HeroArtSize)
                    .glowShadow(SynxioShape.artworkLg, tint, elevation = 24.dp),
                shape = SynxioShape.artworkLg,
                contentDescription = "Pochette de ${song.title}",
            )
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        PlayingIndicator(isPlaying = isPlaying, color = scheme.primary)
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(
                        eyebrow.uppercase(),
                        style = EyebrowTextStyle,
                        color = scheme.primary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.md))
                if (isCurrent) {
                    // Le geste principal de l'écran : le bouton lecture signature.
                    BrandPlayButton(isPlaying = isPlaying, onClick = onPlayPause, size = 52.dp)
                } else {
                    BrandButton(text = "Reprendre", onClick = onPlayPause, icon = Icons.Rounded.PlayArrow)
                }
            }
        }
    }
}

/** Une section de l'accueil : en-tête avec surtitre, puis son carrousel. */
@Composable
private fun HomeSection(
    title: String,
    eyebrow: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = title, eyebrow = eyebrow, actionLabel = actionLabel, onAction = onAction)
        content()
    }
}

@Composable
private fun SongCarousel(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onClick: (Song) -> Unit,
    ranked: Boolean = false,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        itemsIndexed(songs, key = { _, it -> it.id }) { index, song ->
            SongCard(
                song = song,
                isCurrent = song.id == currentSongId,
                isPlaying = song.id == currentSongId && isPlaying,
                rank = if (ranked) index + 1 else null,
                onClick = { onClick(song) },
                modifier = Modifier
                    .animateItem()
                    .enterCascade(index, song.id),
            )
        }
    }
}

/**
 * Carte de titre : la pochette fait la carte. Le titre en cours porte un petit égaliseur
 * dans une pastille de verre ; les classements, une médaille pour le podium.
 */
@Composable
private fun SongCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    rank: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    Column(
        modifier = modifier
            .pressBounce()
            .width(SongCardWidth)
            .clip(SynxioShape.artworkMd)
            .clickable(onClickLabel = "Lire", role = Role.Button, onClick = onClick)
            .semantics {
                if (isCurrent) stateDescription = if (isPlaying) "En cours de lecture" else "En pause"
            }
            // Sans cette marge, l'arrondi du clip rognait le bas du nom de l'artiste.
            .padding(bottom = Space.sm),
    ) {
        Box {
            Artwork(
                model = song.artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = SynxioShape.artworkMd,
            )
            if (rank != null) {
                val medal = when (rank) {
                    1 -> colors.rankGold
                    2 -> colors.rankSilver
                    3 -> colors.rankBronze
                    else -> null
                }
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(Space.sm)
                        .clip(SynxioShape.pill)
                        .then(
                            if (medal != null) Modifier.background(medal)
                            else Modifier.glass(SynxioShape.pill, strength = 1f)
                        )
                        .padding(horizontal = Space.sm, vertical = Space.xxs),
                ) {
                    Text(
                        "$rank",
                        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                        // Les médailles sont claires : un chiffre sombre y reste lisible.
                        color = if (medal != null) Color.Black.copy(alpha = 0.85f) else scheme.onSurface,
                    )
                }
            }
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Space.sm)
                        .size(32.dp)
                        .clip(SynxioShape.pill)
                        .glass(SynxioShape.pill, strength = 1f),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingIndicator(isPlaying = isPlaying, color = scheme.primary)
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Playlist intelligente : mosaïque des pochettes, nom, nombre de titres. */
@Composable
private fun SmartPlaylistCard(
    playlist: SmartPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = MosaicCard(
    title = playlist.title,
    subtitle = playlist.songCount.pluralSongs(),
    artworkUris = playlist.artworkUris,
    onClickLabel = "Ouvrir la playlist",
    onClick = onClick,
    modifier = modifier,
)

/** Carte de playlist ou de mix : mosaïque de pochettes, titre, sous-titre. */
@Composable
private fun MosaicCard(
    title: String,
    subtitle: String,
    artworkUris: List<Any>,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .pressBounce()
            .width(SmartCardWidth)
            .clip(SynxioShape.artworkMd)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(bottom = Space.sm),
    ) {
        ArtworkMosaic(
            models = artworkUris,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = SynxioShape.artworkMd,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Squelette du premier chargement : mêmes marges et mêmes proportions que l'accueil
 * réel (en-tête, puces, carte héros, carrousels), balayé par un reflet discret.
 */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Chargement de ta bibliothèque" }
            .padding(vertical = Space.xl),
    ) {
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Box(Modifier.width(90.dp).height(12.dp).clip(SynxioShape.pill).shimmer())
            Spacer(Modifier.height(Space.sm))
            Box(Modifier.fillMaxWidth(0.8f).height(34.dp).clip(SynxioShape.cardSm).shimmer())
            Spacer(Modifier.height(Space.sm))
            Box(Modifier.width(180.dp).height(14.dp).clip(SynxioShape.pill).shimmer())
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                repeat(3) {
                    Box(Modifier.width(112.dp).height(36.dp).clip(SynxioShape.pill).shimmer())
                }
            }
            Spacer(Modifier.height(Space.lg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HeroArtSize + Space.xxl)
                    .clip(SynxioShape.card)
                    .shimmer()
            )
            Spacer(Modifier.height(Space.xl))
            Box(Modifier.width(180.dp).height(22.dp).clip(RoundedPill).shimmer())
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.padding(horizontal = Space.md)) {
            repeat(3) { SkeletonCard(Modifier.width(SongCardWidth + Space.lg)) }
        }
    }
}

/** Rayon des barres de titre du squelette. */
private val RoundedPill = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xs)
