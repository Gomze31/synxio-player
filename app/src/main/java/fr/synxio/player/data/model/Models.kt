package fr.synxio.player.data.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable

/** URI de base des pochettes d'album exposées par MediaStore. */
private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

/**
 * Un morceau tel qu'indexé depuis MediaStore.
 *
 * On garde volontairement le modèle plat et immuable : le scanner produit une liste
 * complète en mémoire, tout le reste (albums, artistes, genres, dossiers) en est dérivé.
 */
@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val genre: String?,
    val durationMs: Long,
    val track: Int,
    val disc: Int,
    val year: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val path: String,
    val isPodcast: Boolean = false,
    val isAudiobook: Boolean = false,
    /**
     * Vrai quand les tags lus dans MediaStore étaient mal encodés (double encodage,
     * accents décomposés) et ont été réparés à l'affichage. Le fichier, lui, reste à
     * corriger : la réparation des tags le propose.
     */
    val brokenEncoding: Boolean = false,
    /**
     * Pochette retenue par la cascade de métadonnées (intégrée, dossier, en ligne), quand
     * elle diffère de celle de l'album MediaStore. Voir `MetadataEnricher`.
     */
    val artworkOverride: Uri? = null,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val artworkUri: Uri
        get() = artworkOverride ?: ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

    val folderPath: String
        get() = path.substringBeforeLast('/', "")

    val folderName: String
        get() = folderPath.substringAfterLast('/', folderPath)

    val fileName: String
        get() = path.substringAfterLast('/')

    val extension: String
        get() = fileName.substringAfterLast('.', "").uppercase()

    /** Débit moyen approximatif, suffisant pour l'afficher dans les infos du titre. */
    val approxBitrateKbps: Int
        get() = if (durationMs > 0) ((sizeBytes * 8.0) / durationMs).toInt() else 0

    val displayArtist: String
        get() = artist.takeUnless { it.isBlank() || it == UNKNOWN_MEDIASTORE } ?: UNKNOWN_ARTIST

    val displayAlbum: String
        get() = album.takeUnless { it.isBlank() || it == UNKNOWN_MEDIASTORE } ?: UNKNOWN_ALBUM

    companion object {
        const val UNKNOWN_MEDIASTORE = "<unknown>"
        const val UNKNOWN_ARTIST = "Artiste inconnu"
        const val UNKNOWN_ALBUM = "Album inconnu"
        const val UNKNOWN_GENRE = "Genre inconnu"
    }
}

/**
 * Découpe un tag d'artiste contenant plusieurs noms.
 *
 * MediaStore ne restitue pas les champs multi-valués d'ID3 : une compilation arrive
 * sous la forme d'une seule chaîne « Damso/Sidaction/Adèle Castillon/... », qui créait
 * un artiste fantôme dans la bibliothèque.
 *
 * Les seuils sont volontairement conservateurs pour ne pas casser les vrais noms :
 * `AC/DC` n'a qu'une barre oblique, `Earth, Wind & Fire` qu'une virgule.
 */
fun splitArtistTag(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()

    val separators = buildList {
        if (trimmed.contains(';')) add(';')
        if (trimmed.count { it == '/' } >= 2) add('/')
        if (trimmed.count { it == ',' } >= 3) add(',')
    }
    if (separators.isEmpty()) return listOf(trimmed)

    return trimmed
        .split(*separators.toCharArray())
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .ifEmpty { listOf(trimmed) }
}

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: Int,
    val songs: List<Song>,
    /** Pochette résolue d'un des titres, prioritaire sur celle de MediaStore. */
    val artworkOverride: Uri? = null,
) {
    val songCount: Int get() = songs.size
    val durationMs: Long get() = songs.sumOf { it.durationMs }
    val artworkUri: Uri get() = artworkOverride ?: ContentUris.withAppendedId(ALBUM_ART_BASE, id)
    /**
     * Ajout le plus récent parmi les morceaux de l'album.
     *
     * Calculé une fois à la construction : l'accueil et le tri « date d'ajout » le
     * lisent à chaque comparaison, soit des milliers de parcours par tri en getter.
     */
    val dateAddedSec: Long = songs.maxOfOrNull { it.dateAddedSec } ?: 0L
}

@Immutable
data class Artist(
    val id: Long,
    val name: String,
    val albums: List<Album>,
) {
    /** Aplati une seule fois : les statistiques le parcourent pour chaque artiste. */
    val songs: List<Song> by lazy { albums.flatMap { it.songs } }
    val songCount: Int get() = albums.sumOf { it.songCount }
    val albumCount: Int get() = albums.size
    val durationMs: Long get() = albums.sumOf { it.durationMs }
    /** Pochette de l'album le plus récent : meilleure « photo » d'artiste sans réseau. */
    val artworkUri: Uri? get() = albums.maxByOrNull { it.year }?.artworkUri
}

@Immutable
data class Genre(
    val name: String,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
    val artworkUris: List<Uri> get() = songs.asSequence().distinctBy { it.albumId }.take(4).map { it.artworkUri }.toList()
}

/** Un dossier du système de fichiers contenant au moins un morceau. */
@Immutable
data class Folder(
    val path: String,
    val songs: List<Song>,
) {
    val name: String get() = path.substringAfterLast('/', path)
    val songCount: Int get() = songs.size
}

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
    val durationMs: Long get() = songs.sumOf { it.durationMs }
    val artworkUris: List<Uri> get() = songs.asSequence().distinctBy { it.albumId }.take(4).map { it.artworkUri }.toList()
}

/** Une ligne de paroles ; [timeMs] vaut null pour des paroles non synchronisées. */
@Immutable
data class LyricLine(val timeMs: Long?, val text: String)

@Immutable
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
) {
    val plainText: String get() = lines.joinToString("\n") { it.text }

    /** Index de la ligne active à [positionMs], ou -1 avant la première. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        var result = -1
        for ((i, line) in lines.withIndex()) {
            val t = line.timeMs ?: continue
            if (t <= positionMs) result = i else break
        }
        return result
    }

    companion object {
        val EMPTY = Lyrics(emptyList(), synced = false, source = "")
    }
}
