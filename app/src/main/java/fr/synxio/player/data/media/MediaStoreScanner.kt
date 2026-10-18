package fr.synxio.player.data.media

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import fr.synxio.player.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Issue d'une demande de suppression de fichiers. */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /** Le système doit demander confirmation à l'utilisateur. */
    data class NeedsConsent(val intentSender: android.content.IntentSender) : DeleteOutcome

    data object Failed : DeleteOutcome
}

/**
 * Lit la bibliothèque audio locale via MediaStore.
 *
 * MediaStore est la seule source qui reste correcte sous Scoped Storage : parcourir
 * le système de fichiers à la main ne marche plus depuis Android 11.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val projection = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ARTIST_ID)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        add(MediaStore.Audio.Media.DATE_ADDED)
        add(MediaStore.Audio.Media.DATE_MODIFIED)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.MIME_TYPE)
        add(MediaStore.Audio.Media.DATA)
        add(MediaStore.Audio.Media.IS_PODCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Audio.Media.IS_AUDIOBOOK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.Audio.Media.ALBUM_ARTIST)
            add(MediaStore.Audio.Media.GENRE)
            add(MediaStore.Audio.Media.DISC_NUMBER)
        }
    }.toTypedArray()

    /**
     * Charge tous les morceaux.
     *
     * @param minDurationMs filtre les bips et notifications indexés comme musique.
     * @param excludedFolders préfixes de chemins à ignorer.
     */
    fun scan(minDurationMs: Long = 30_000L, excludedFolders: Set<String> = emptySet(), audiobookFolders: Set<String> = emptySet()): List<Song> {
        val selection = buildString {
            // Inclure MUSIQUE, PODCAST et AUDIOBOOK natifs. Les fichiers vocaux / alarmes sont souvent IS_MUSIC=0
            append("(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_PODCAST} != 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" OR ${MediaStore.Audio.Media.IS_AUDIOBOOK} != 0")
            }
            append(")")
            append(" AND ${MediaStore.Audio.Media.DURATION} >= ?")
        }
        val args = arrayOf(minDurationMs.toString())

        val songs = ArrayList<Song>(512)
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val discCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
                val podcastCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_PODCAST)
                val audiobookCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.IS_AUDIOBOOK)
                } else -1

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    
                    // Blacklist Intelligente : Ignorer les notes vocales et appels enregistrés
                    val lowerPath = path.lowercase()
                    if (lowerPath.contains("whatsapp audio") ||
                        lowerPath.contains("telegram audio") ||
                        lowerPath.contains("snapchat") ||
                        lowerPath.contains("voice recorder") ||
                        lowerPath.contains("voice_notes") ||
                        lowerPath.contains("call_rec") ||
                        lowerPath.contains("recordings")
                    ) {
                        continue
                    }
                    
                    if (excludedFolders.any { path.startsWith(it) }) continue

                    // MediaStore encode le disque dans TRACK : 1004 = disque 1, piste 4.
                    val rawTrack = cursor.getInt(trackCol)
                    val track = if (rawTrack > 1000) rawTrack % 1000 else rawTrack
                    val disc = when {
                        discCol >= 0 && !cursor.isNull(discCol) -> cursor.getInt(discCol)
                        rawTrack > 1000 -> rawTrack / 1000
                        else -> 1
                    }

                    val isPodcastFile = (podcastCol >= 0 && cursor.getInt(podcastCol) != 0)
                    val isNativeAudiobook = (audiobookCol >= 0 && cursor.getInt(audiobookCol) != 0)
                    val isAudiobookFile = isNativeAudiobook || audiobookFolders.any { path.startsWith(it) }

                    val rawTitle = cursor.getString(titleCol)?.trim().orEmpty()
                    val rawArtist = cursor.getString(artistCol).orEmpty().trim()
                    val rawAlbum = cursor.getString(albumCol).orEmpty().trim()
                    val brokenEncoding = rawTitle.tidyTag() != rawTitle ||
                        rawArtist.tidyTag() != rawArtist ||
                        rawAlbum.tidyTag() != rawAlbum

                    songs += Song(
                        id = cursor.getLong(idCol),
                        // Les tags rippés traînent souvent des espaces parasites
                        // (« <espace>nos souvenirs ») : on nettoie à la source.
                        title = cursor.getString(titleCol)?.tidyTag()?.takeIf { it.isNotEmpty() }
                            ?: path.substringAfterLast('/').tidyTag(),
                        artist = cursor.getString(artistCol).orEmpty().tidyTag(),
                        artistId = cursor.getLong(artistIdCol),
                        album = cursor.getString(albumCol).orEmpty().tidyTag(),
                        albumId = cursor.getLong(albumIdCol),
                        albumArtist = albumArtistCol.takeIf { it >= 0 }
                            ?.let { cursor.getString(it)?.tidyTag() },
                        genre = genreCol.takeIf { it >= 0 }
                            ?.let { cursor.getString(it)?.tidyTag() },
                        durationMs = cursor.getLong(durationCol),
                        track = track,
                        disc = disc.coerceAtLeast(1),
                        year = cursor.getInt(yearCol),
                        dateAddedSec = cursor.getLong(addedCol),
                        dateModifiedSec = cursor.getLong(modifiedCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol).orEmpty(),
                        path = path,
                        isPodcast = isPodcastFile,
                        isAudiobook = isAudiobookFile,
                        brokenEncoding = brokenEncoding,
                    )
                }
            }
        }.onFailure { Log.e(TAG, "Scan MediaStore impossible", it) }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) songs else fillGenresLegacy(songs)
    }

    /**
     * Avant Android 11, MediaStore.Audio.Media.GENRE n'existe pas : il faut passer par
     * la table des genres et croiser les ids de morceaux.
     */
    private fun fillGenresLegacy(songs: List<Song>): List<Song> {
        val genreBySongId = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { genres ->
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(0)
                    val name = genres.getString(1) ?: continue
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null,
                    )?.use { members ->
                        while (members.moveToNext()) genreBySongId[members.getLong(0)] = name
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Genres legacy indisponibles", it) }

        if (genreBySongId.isEmpty()) return songs
        return songs.map { song -> genreBySongId[song.id]?.let { song.copy(genre = it) } ?: song }
    }

    /** Émet à chaque modification de la table audio (ajout, suppression, édition de tag). */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val contentResolver: ContentResolver get() = context.contentResolver

    /** Permission de lecture audio adaptée à la version d'Android. */
    val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasAudioPermission(): Boolean =
        context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED

    /**
     * Supprime ces morceaux de l'appareil.
     *
     * Android 11+ : les fichiers partent à la corbeille système (`createTrashRequest`),
     * récupérables trente jours, après confirmation par le système.
     *
     * Android 10 : la suppression directe lève `RecoverableSecurityException` pour un
     * fichier créé par une autre app. Le consentement accordé ne supprime rien à lui
     * seul : il faut rappeler cette fonction ensuite.
     *
     * Android 8-9 : suppression directe, qui exige WRITE_EXTERNAL_STORAGE.
     */
    fun deleteSongs(songs: List<Song>): DeleteOutcome {
        if (songs.isEmpty()) return DeleteOutcome.Deleted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching {
                DeleteOutcome.NeedsConsent(
                    MediaStore.createTrashRequest(context.contentResolver, songs.map { it.uri }, true)
                        .intentSender
                )
            }.getOrElse {
                Log.w(TAG, "Demande de suppression refusée", it)
                DeleteOutcome.Failed
            }
        }

        for (song in songs) {
            try {
                context.contentResolver.delete(song.uri, null, null)
            } catch (e: SecurityException) {
                val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                } else {
                    null
                }
                Log.w(TAG, "Suppression refusée : ${song.path}", e)
                return sender?.let(DeleteOutcome::NeedsConsent) ?: DeleteOutcome.Failed
            }
        }
        return DeleteOutcome.Deleted
    }

    private companion object {
        const val TAG = "MediaStoreScanner"
    }
}

/**
 * Nettoie un tag lu dans MediaStore : espaces parasites, double encodage, Unicode décomposé.
 *
 *  - Double encodage : un tag UTF-8 déclaré Latin-1 ressort en « RÃ©veiller » ou, avec un
 *    accent combinant, en « ReÌ\u0081veiller ». On le répare quand, et seulement quand,
 *    ses caractères relus comme des octets forment un UTF-8 valide (voir [repairMojibake]).
 *  - Unicode décomposé : certains fichiers stockent « é » en « e » + accent combinant. Les
 *    lanceurs l'affichaient « Reì veiller » dans le widget, et la recherche ou le tri le
 *    traitaient comme un autre mot. On recompose en NFC.
 */
internal fun String.tidyTag(): String {
    val repaired = repairC1Controls(repairMojibake(trim()))
    return if (java.text.Normalizer.isNormalized(repaired, java.text.Normalizer.Form.NFC)) repaired
    else java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFC)
}

/**
 * Répare un texte UTF-8 qui a été lu comme du Latin-1 ou du Windows-1252.
 *
 * « Câ€™est la Vie » : les trois octets UTF-8 de l'apostrophe typographique, relus en
 * Windows-1252, donnent « â€™ ». On refait le chemin inverse : chaque caractère redevient
 * l'octet d'origine, puis le tout est décodé en UTF-8.
 *
 * Sans risque pour un texte sain : « Café » donne l'octet E9 seul, « C’est » l'octet 92
 * seul, qui ne sont pas de l'UTF-8 valide, et le texte est rendu tel quel. Seules les
 * suites d'octets qui forment un UTF-8 correct — signature du double encodage — sont
 * reconverties.
 */
internal fun repairMojibake(text: String): String {
    if (text.all { it.code < 0x80 }) return text
    val bytes = ByteArray(text.length)
    for ((i, c) in text.withIndex()) {
        val code = c.code
        bytes[i] = when {
            code <= 0xFF -> code.toByte()
            else -> (CP1252_BYTES[c] ?: return text).toByte()
        }
    }
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrDefault(text)
}

/**
 * Répare un texte Windows-1252 lu comme du Latin-1.
 *
 * « C\u0092est » : l'apostrophe typographique de Windows (octet 92) devient, relue en
 * Latin-1, un caractère de contrôle invisible. Aucun tag sain ne contient de caractère
 * de la plage 80–9F : on les remplace par leur équivalent Windows-1252.
 */
internal fun repairC1Controls(text: String): String {
    if (text.none { it.code in 0x80..0x9F }) return text
    return buildString(text.length) {
        for (c in text) append(if (c.code in 0x80..0x9F) CP1252_CHARS[c.code] ?: c else c)
    }
}

/** Octet de la plage 80–9F, et le caractère Windows-1252 qu'il représente. */
private val CP1252_CHARS: Map<Int, Char> by lazy { CP1252_BYTES.entries.associate { (char, byte) -> byte to char } }

/** Caractères Windows-1252 de la plage 80–9F, et l'octet qu'ils représentent. */
private val CP1252_BYTES: Map<Char, Int> = mapOf(
    '\u20AC' to 0x80,
    '\u201A' to 0x82,
    '\u0192' to 0x83,
    '\u201E' to 0x84,
    '\u2026' to 0x85,
    '\u2020' to 0x86,
    '\u2021' to 0x87,
    '\u02C6' to 0x88,
    '\u2030' to 0x89,
    '\u0160' to 0x8A,
    '\u2039' to 0x8B,
    '\u0152' to 0x8C,
    '\u017D' to 0x8E,
    '\u2018' to 0x91,
    '\u2019' to 0x92,
    '\u201C' to 0x93,
    '\u201D' to 0x94,
    '\u2022' to 0x95,
    '\u2013' to 0x96,
    '\u2014' to 0x97,
    '\u02DC' to 0x98,
    '\u2122' to 0x99,
    '\u0161' to 0x9A,
    '\u203A' to 0x9B,
    '\u0153' to 0x9C,
    '\u017E' to 0x9E,
    '\u0178' to 0x9F
)
