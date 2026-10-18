package fr.synxio.player.data.meta

import android.media.MediaMetadataRetriever
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.reference.GenreTypes
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/** Ce que contient réellement le fichier, lu sans passer par MediaStore. */
data class FileTags(
    val metadata: TrackMetadata,
    val hasEmbeddedArt: Boolean,
)

/**
 * Lit les tags directement dans le fichier : ID3v2 et ID3v1 (MP3), Vorbis (FLAC, Ogg),
 * atomes MP4 (M4A).
 *
 * MediaStore ne suffit pas : il ignore l'année des tags ID3v2.4 (TDRC), devine mal le jeu
 * de caractères des tags Latin-1, ne lit pas l'ID3v1 quand un ID3v2 vide est présent, et
 * remplit l'album avec le nom du dossier quand le tag est vide — sans le dire. Lire le
 * fichier permet de distinguer un vrai tag d'un remplissage.
 */
@Singleton
class FileTagReader @Inject constructor() {

    init {
        // jaudiotagger journalise chaque cadre lu : sur une bibliothèque entière, ce bruit
        // coûte plus cher que la lecture elle-même.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /** `null` si le fichier n'est lisible ni par jaudiotagger ni par Android. */
    fun read(path: String): FileTags? {
        val file = File(path)
        if (!file.canRead()) return null
        return readWithJaudiotagger(file) ?: readWithRetriever(path)
    }

    private fun readWithJaudiotagger(file: File): FileTags? = runCatching {
        val audio = AudioFileIO.read(file)
        // En MP3, l'ID3v1 complète un ID3v2 incomplet : certains logiciels n'écrivaient
        // que l'un ou l'autre selon le champ.
        val tags = buildList<Tag> {
            audio.tag?.let(::add)
            (audio as? MP3File)?.takeIf { it.hasID3v1Tag() }?.iD3v1Tag?.let(::add)
        }
        fun first(key: FieldKey): String? = tags.firstNotNullOfOrNull { tag ->
            runCatching { tag.getFirst(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }

        val durationMs = runCatching { (audio.audioHeader.preciseTrackLength * 1000).toLong() }
            .getOrNull()?.takeIf { it > 0 }
        FileTags(
            metadata = TrackMetadata(
                title = first(FieldKey.TITLE),
                artist = first(FieldKey.ARTIST),
                album = first(FieldKey.ALBUM),
                albumArtist = first(FieldKey.ALBUM_ARTIST),
                year = first(FieldKey.YEAR)?.let(::parseYear),
                genre = first(FieldKey.GENRE)?.let(::parseGenre),
                track = first(FieldKey.TRACK)?.let(::parseNumber),
                disc = first(FieldKey.DISC_NO)?.let(::parseNumber),
                durationMs = durationMs,
            ),
            hasEmbeddedArt = tags.any { tag -> runCatching { tag.firstArtwork != null }.getOrDefault(false) },
        )
    }.onFailure { Log.d(TAG, "jaudiotagger ne lit pas ${file.name} : ${it.javaClass.simpleName}") }.getOrNull()

    /** Repli : le lecteur de métadonnées d'Android lit les formats que jaudiotagger refuse. */
    private fun readWithRetriever(path: String): FileTags? = runCatching {
        withRetriever(path) { retriever ->
            fun meta(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            FileTags(
                metadata = TrackMetadata(
                    title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    year = (meta(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?: meta(MediaMetadataRetriever.METADATA_KEY_DATE))?.let(::parseYear),
                    genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let(::parseGenre),
                    track = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let(::parseNumber),
                    disc = meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let(::parseNumber),
                    durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                ),
                hasEmbeddedArt = retriever.embeddedPicture != null,
            )
        }
    }.onFailure { Log.d(TAG, "Métadonnées illisibles : $path") }.getOrNull()

    /** Image intégrée, pour quand MediaStore ne sait pas en tirer une miniature. */
    fun embeddedPicture(path: String): ByteArray? {
        val fromTags = runCatching {
            AudioFileIO.read(File(path)).tag?.firstArtwork?.binaryData
        }.getOrNull()
        if (fromTags != null && fromTags.isNotEmpty()) return fromTags
        return runCatching {
            withRetriever(path) { it.embeddedPicture }
        }.getOrNull()
    }

    /** `close()` n'existe qu'à partir d'Android 10 : `release()` marche partout. */
    private inline fun <T> withRetriever(path: String, block: (MediaMetadataRetriever) -> T): T {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            return block(retriever)
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val TAG = "FileTagReader"

        private val YEAR = Regex("(1[0-9]{3}|20[0-9]{2})")
        private val NUMBER = Regex("^\\s*(\\d{1,3})")
        private val GENRE_CODE = Regex("^\\(?(\\d{1,3})\\)?(.*)$")

        /** « 2017-03-03 », « 2017 », « 20170303 ». */
        internal fun parseYear(raw: String): Int? = YEAR.find(raw)?.value?.toIntOrNull()

        /** « 3/12 », « 03 ». */
        internal fun parseNumber(raw: String): Int? = NUMBER.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

        /** « (13) », « 13 » ou « (13)Pop » : code de genre ID3v1 traduit en nom. */
        internal fun parseGenre(raw: String): String? {
            val match = GENRE_CODE.matchEntire(raw.trim()) ?: return raw.trim().takeIf { it.isNotEmpty() }
            val suffix = match.groupValues[2].trim()
            if (suffix.isNotEmpty()) return suffix
            return runCatching { GenreTypes.getInstanceOf().getValueForId(match.groupValues[1].toInt()) }.getOrNull()
        }
    }
}
