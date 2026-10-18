package fr.synxio.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import fr.synxio.player.ui.theme.glass
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.core.util.pluralAlbums
import fr.synxio.player.core.util.pluralSongs
import fr.synxio.player.data.model.Album
import fr.synxio.player.data.model.Artist
import fr.synxio.player.data.model.Folder
import fr.synxio.player.data.model.Genre
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme

/** Hauteur minimale d'une ligne de liste : vignette + deux lignes de texte, au doigt. */
private val RowMinHeight = 64.dp

/** Vignette de ligne : assez grande pour reconnaître une pochette d'un coup d'œil. */
private val RowThumbSize = 52.dp

/**
 * Ligne de titre, brique de toutes les listes de l'app.
 *
 * Le titre en cours passe en `primary` ET est annoncé par TalkBack via
 * `stateDescription` : la couleur seule ne suffit pas à porter l'information.
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    showArtwork: Boolean = true,
    trackNumber: Int? = null,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val brand = SynxioTheme.colors.brandBrush
    Row(
        modifier = modifier
            .pressBounce(0.98f)
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(SynxioShape.artworkMd)
            .then(
                // Titre en cours : verre teinté et filet de signature à gauche, comme une
                // piste éclairée sur une console.
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
            .combinedClickable(
                onClick = onClick,
                onClickLabel = "Lire",
                role = Role.Button,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) "Options" else null,
            )
            .then(
                if (isCurrent) Modifier.semantics {
                    stateDescription = if (isPlaying) "En cours de lecture" else "En pause"
                } else Modifier
            )
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showArtwork -> Artwork(
                model = song.artworkUri,
                modifier = Modifier.size(RowThumbSize),
                shape = SynxioShape.artworkSm,
            )

            trackNumber != null -> Box(
                modifier = Modifier.width(Space.xxl),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent) {
                    PlayingIndicator(isPlaying, color = scheme.primary)
                } else {
                    Text(
                        text = trackNumber.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(isFavorite) {
                    Row {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Favori",
                            tint = SynxioTheme.colors.favorite,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(Space.xs))
                    }
                }
                Text(
                    text = "${song.displayArtist} · ${song.durationMs.asDuration()}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (showArtwork && isCurrent) {
            PlayingIndicator(isPlaying, Modifier.padding(horizontal = Space.sm), scheme.primary)
        }

        trailing?.invoke()

        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(Space.touchTarget)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options de ${song.title}")
            }
        }
    }
}

/**
 * Carte d'album pour les grilles et carrousels : la pochette fait la carte, le texte
 * vit dessous. Pas de conteneur autour, la pochette suffit à délimiter l'élément.
 */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .pressBounce()
            .clip(SynxioShape.artworkMd)
            .combinedClickable(
                onClick = onClick,
                onClickLabel = "Ouvrir l'album",
                role = Role.Button,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) "Options" else null,
            )
            .padding(Space.sm),
    ) {
        Artwork(
            model = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sharedNavElement(SharedKeys.albumArt(album.id), SynxioShape.artworkMd),
            shape = SynxioShape.artworkMd,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Carte d'artiste : portrait rond, convention universelle pour « une personne ». */
@Composable
fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .pressBounce()
            .clip(SynxioShape.artworkMd)
            .clickable(onClickLabel = "Ouvrir l'artiste", role = Role.Button, onClick = onClick)
            .padding(Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            model = artist.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sharedNavElement(SharedKeys.artistArt(artist.name), CircleShape),
            shape = CircleShape,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = artist.albumCount.pluralAlbums(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GenreRow(genre: Genre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SimpleRow(
        title = genre.name,
        subtitle = genre.songCount.pluralSongs(),
        onClick = onClick,
        onClickLabel = "Ouvrir le genre",
        modifier = modifier,
    ) {
        ArtworkMosaic(
            models = genre.artworkUris,
            modifier = Modifier.size(RowThumbSize),
            shape = SynxioShape.artworkSm,
        )
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
) {
    SimpleRow(
        title = playlist.name,
        subtitle = "${playlist.songCount.pluralSongs()} · ${playlist.durationMs.asDuration()}",
        onClick = onClick,
        onClickLabel = "Ouvrir la playlist",
        modifier = modifier,
        onMenuClick = onMenuClick,
    ) {
        ArtworkMosaic(
            models = playlist.artworkUris,
            modifier = Modifier
                .size(RowThumbSize)
                .sharedNavElement(SharedKeys.playlistArt(playlist.id), SynxioShape.artworkSm),
            shape = SynxioShape.artworkSm,
        )
    }
}

@Composable
fun FolderRow(folder: Folder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SimpleRow(
        title = folder.name,
        subtitle = "${folder.songCount.pluralSongs()} · ${folder.path}",
        onClick = onClick,
        onClickLabel = "Ouvrir le dossier",
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(RowThumbSize)
                .clip(SynxioShape.artworkSm)
                .glass(SynxioShape.artworkSm, strength = 0.8f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Gabarit commun des lignes « collection » (genre, playlist, dossier). */
@Composable
private fun SimpleRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .pressBounce(0.98f)
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(SynxioShape.artworkMd)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(Space.touchTarget)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options de $title")
            }
        }
    }
}
