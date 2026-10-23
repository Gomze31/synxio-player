package fr.synxio.player.data.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * URI de base des pochettes d'album exposées par MediaStore.
 *
 * Paresseuse : les modèles sont aussi exercés par les tests JVM, où `Uri` n'existe
 * qu'en coquille vide. Rien ne la touche tant qu'aucune pochette n'est demandée.
 */
private val ALBUM_ART_BASE: Uri by lazy { Uri.parse("content://media/external/audio/albumart") }

/**
 * Un morceau tel qu'indexé depuis MediaStore.
 *
 * On garde volontairement le modèle plat et immuable : le scanner produit une liste
 * complète en mémoire, tout le reste (albums, artistes, genres, dossiers) en est dérivé.
 */
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
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val artworkUri: Uri
        get() = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

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

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: Int,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
    val durationMs: Long get() = songs.sumOf { it.durationMs }
    val artworkUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_BASE, id)
    val dateAddedSec: Long get() = songs.maxOfOrNull { it.dateAddedSec } ?: 0L
}

data class Artist(
    val id: Long,
    val name: String,
    val albums: List<Album>,
) {
    val songs: List<Song> get() = albums.flatMap { it.songs }
    val songCount: Int get() = albums.sumOf { it.songCount }
    val albumCount: Int get() = albums.size
    val durationMs: Long get() = albums.sumOf { it.durationMs }
    /** Pochette de l'album le plus récent : meilleure « photo » d'artiste sans réseau. */
    val artworkUri: Uri? get() = albums.maxByOrNull { it.year }?.artworkUri
}

data class Genre(
    val name: String,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
    val artworkUris: List<Uri> get() = songs.distinctBy { it.albumId }.take(4).map { it.artworkUri }
}

/** Un dossier du système de fichiers contenant au moins un morceau. */
data class Folder(
    val path: String,
    val songs: List<Song>,
) {
    val name: String get() = path.substringAfterLast('/', path)
    val songCount: Int get() = songs.size
}

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songs: List<Song>,
) {
    val songCount: Int get() = songs.size
    val durationMs: Long get() = songs.sumOf { it.durationMs }
    val artworkUris: List<Uri> get() = songs.distinctBy { it.albumId }.take(4).map { it.artworkUri }
}

/** Une ligne de paroles ; [timeMs] vaut null pour des paroles non synchronisées. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
) {
    val plainText: String get() = lines.joinToString("\n") { it.text }

    /** Indices des lignes horodatées, et leurs instants, pour la recherche dichotomique. */
    private val timedIndices: IntArray by lazy {
        lines.indices.filter { lines[it].timeMs != null }.toIntArray()
    }
    private val timedValues: LongArray by lazy {
        LongArray(timedIndices.size) { k -> lines[timedIndices[k]].timeMs!! }
    }

    /**
     * Index de la ligne active à [positionMs], ou -1 avant la première.
     *
     * Recherche dichotomique sur les seules lignes horodatées : la position est
     * interrogée plusieurs fois par seconde pendant l'affichage des paroles, et un
     * parcours linéaire de plusieurs centaines de lignes à cette cadence finissait par
     * se voir. Les instants sont supposés croissants, comme dans un fichier .lrc.
     */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        val values = timedValues
        if (values.isEmpty() || values[0] > positionMs) return -1
        var low = 0
        var high = values.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (values[mid] <= positionMs) low = mid else high = mid - 1
        }
        return timedIndices[low]
    }

    companion object {
        val EMPTY = Lyrics(emptyList(), synced = false, source = "")
    }
}
