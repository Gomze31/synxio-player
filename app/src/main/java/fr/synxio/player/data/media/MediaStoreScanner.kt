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

                    songs += Song(
                        id = cursor.getLong(idCol),
                        // Les tags rippés traînent souvent des espaces parasites
                        // (« <espace>nos souvenirs ») : on nettoie à la source.
                        title = cursor.getString(titleCol)?.trim()?.takeIf { it.isNotEmpty() }
                            ?: path.substringAfterLast('/'),
                        artist = cursor.getString(artistCol).orEmpty().trim(),
                        artistId = cursor.getLong(artistIdCol),
                        album = cursor.getString(albumCol).orEmpty().trim(),
                        albumId = cursor.getLong(albumIdCol),
                        albumArtist = albumArtistCol.takeIf { it >= 0 }
                            ?.let { cursor.getString(it)?.trim() },
                        genre = genreCol.takeIf { it >= 0 }
                            ?.let { cursor.getString(it)?.trim() },
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

    private companion object {
        const val TAG = "MediaStoreScanner"
    }
}
