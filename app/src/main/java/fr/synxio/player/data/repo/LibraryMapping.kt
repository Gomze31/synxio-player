package fr.synxio.player.data.repo

import fr.synxio.player.data.db.LibrarySongEntity
import fr.synxio.player.data.model.Song

/**
 * Ligne de cache → modèle de l'UI. Le drapeau « dossier livre audio » et la pochette
 * enrichie viennent plus tard (`LibraryBuilder`, `MetadataEnricher.overlay`).
 */
internal fun LibrarySongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    albumArtist = albumArtist,
    genre = genre,
    durationMs = durationMs,
    track = track,
    disc = disc,
    year = year,
    dateAddedSec = dateAddedSec,
    dateModifiedSec = dateModifiedSec,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    path = path,
    isPodcast = isPodcast,
    isAudiobook = isNativeAudiobook,
    brokenEncoding = brokenEncoding,
)
