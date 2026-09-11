package fr.synxio.player

import fr.synxio.player.data.model.Song

/**
 * Fabrique de [Song] pour les tests : seuls les champs qui comptent pour le test
 * examiné sont nommés à l'appel, le reste prend une valeur neutre.
 */
fun song(
    id: Long = 1,
    title: String = "Titre",
    artist: String = "Artiste",
    album: String = "Album",
    albumArtist: String? = null,
    genre: String? = null,
    durationMs: Long = 180_000,
    track: Int = 1,
    disc: Int = 1,
    year: Int = 2020,
    dateAddedSec: Long = 0,
    sizeBytes: Long = 5_000_000,
    mimeType: String = "audio/mpeg",
    path: String = "/music/$id.mp3",
): Song = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = 1,
    album = album,
    albumId = 1,
    albumArtist = albumArtist,
    genre = genre,
    durationMs = durationMs,
    track = track,
    disc = disc,
    year = year,
    dateAddedSec = dateAddedSec,
    dateModifiedSec = dateAddedSec,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    path = path,
)
