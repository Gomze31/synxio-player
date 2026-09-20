package fr.synxio.player.data.repo

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Champs éditables d'un morceau. `null` = « ne pas toucher ». */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val track: String? = null,
    val disc: String? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    /** Pochette à embarquer (JPEG/PNG). `null` = ne pas toucher à l'existante. */
    val artwork: ByteArray? = null,
) {
    // equals/hashCode générés ignoreraient le contenu du ByteArray : on les écrit
    // pour que deux TagEdit avec la même image soient bien considérés égaux.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagEdit) return false
        return title == other.title && artist == other.artist && album == other.album &&
            albumArtist == other.albumArtist && genre == other.genre && year == other.year &&
            track == other.track && disc == other.disc && comment == other.comment &&
            lyrics == other.lyrics && artwork.contentEquals(other.artwork)
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (albumArtist?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (year?.hashCode() ?: 0)
        result = 31 * result + (track?.hashCode() ?: 0)
        result = 31 * result + (disc?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + (artwork?.contentHashCode() ?: 0)
        return result
    }
}

sealed interface TagWriteResult {
    data object Success : TagWriteResult
    /** Android 11+ : il faut d'abord demander l'accès en écriture à l'utilisateur. */
    data class NeedsUserConsent(val intentSender: IntentSender) : TagWriteResult
    data class Failure(val message: String) : TagWriteResult
}

@Singleton
class TagEditorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Écrit les tags dans le fichier avec jaudiotagger, puis force MediaStore à ré-indexer.
     *
     * Sous Scoped Storage, une app ne peut modifier un fichier média qu'elle ne possède pas
     * qu'après un consentement explicite : on remonte l'[IntentSender] à l'UI dans ce cas.
     */
    suspend fun write(song: Song, edit: TagEdit): TagWriteResult = withContext(Dispatchers.IO) {
        val source = File(song.path)
        if (!source.exists()) return@withContext TagWriteResult.Failure("Fichier introuvable")

        // Copie de travail dans le cache privé de l'application.
        //
        // jaudiotagger a besoin d'un vrai fichier accessible en écriture, or les médias
        // appartiennent au MediaProvider : sous stockage cantonné, écrire directement sur
        // song.path échoue avec EACCES, même après le consentement accordé — celui-ci
        // porte sur l'URI de contenu, pas sur le chemin.
        val workDir = File(context.cacheDir, WORK_DIR).apply { mkdirs() }
        val work = File(workDir, "edit.${song.extension.lowercase().ifBlank { "mp3" }}")

        runCatching {
            source.inputStream().use { input ->
                work.outputStream().use { output -> input.copyTo(output) }
            }

            val audioFile = AudioFileIO.read(work)
            val tag = audioFile.tagOrCreateAndSetDefault

            edit.title?.let { tag.setField(FieldKey.TITLE, it) }
            edit.artist?.let { tag.setField(FieldKey.ARTIST, it) }
            edit.album?.let { tag.setField(FieldKey.ALBUM, it) }
            edit.albumArtist?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
            edit.genre?.let { tag.setField(FieldKey.GENRE, it) }
            edit.year?.let { tag.setField(FieldKey.YEAR, it) }
            edit.track?.let { tag.setField(FieldKey.TRACK, it) }
            edit.disc?.let { tag.setField(FieldKey.DISC_NO, it) }
            edit.comment?.let { tag.setField(FieldKey.COMMENT, it) }
            edit.lyrics?.let { tag.setField(FieldKey.LYRICS, it) }

            // La pochette est optionnelle : si le format ne la supporte pas, on garde
            // les tags texte plutôt que de faire échouer toute l'écriture.
            edit.artwork?.let { bytes ->
                runCatching {
                    tag.deleteArtworkField()
                    tag.setField(
                        AndroidArtwork().apply {
                            binaryData = bytes
                            mimeType = if (bytes.isPng()) "image/png" else "image/jpeg"
                            pictureType = FRONT_COVER
                        }
                    )
                }.onFailure { Log.w(TAG, "Pochette non écrite pour ${song.title}", it) }
            }

            AudioFileIO.write(audioFile)

            // Réécriture par l'URI de contenu, seul canal autorisé.
            //
            // Le mode "wt" tronque avant d'écrire : sans le `t`, un fichier corrigé plus
            // court que l'original laisserait la queue des anciens octets en place et
            // produirait un média corrompu.
            context.contentResolver.openOutputStream(song.uri, "wt")?.use { output ->
                work.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Flux d'écriture indisponible")

            // Forcer la mise à jour des colonnes du MediaStore pour que l'app voie
            // les changements immédiatement au prochain scan (et que l'observer se déclenche).
            val values = android.content.ContentValues().apply {
                edit.title?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.TITLE, it) }
                edit.artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                edit.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    edit.albumArtist?.let { put(MediaStore.Audio.Media.ALBUM_ARTIST, it) }
                    edit.genre?.let { put(MediaStore.Audio.Media.GENRE, it) }
                    edit.disc?.toIntOrNull()?.let { put(MediaStore.Audio.Media.DISC_NUMBER, it) }
                }
                edit.year?.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
                edit.track?.toIntOrNull()?.let { put(MediaStore.Audio.Media.TRACK, it) }
                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            }
            context.contentResolver.update(song.uri, values, null, null)

            rescan(song.path)
            TagWriteResult.Success
        }.getOrElse { error ->
            when (error) {
                is SecurityException ->
                    requestConsent(song)?.let { TagWriteResult.NeedsUserConsent(it) }
                        ?: TagWriteResult.Failure("Accès en écriture refusé")

                else -> {
                    Log.w(TAG, "Écriture impossible pour ${song.title}", error)
                    TagWriteResult.Failure(error.message ?: "Écriture des tags impossible")
                }
            }
        }.also { work.delete() }
    }

    private fun requestConsent(song: Song): IntentSender? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri)).intentSender

        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> runCatching {
            context.contentResolver.openOutputStream(song.uri)?.close()
            null
        }.getOrElse { (it as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender }

        else -> null
    }

    private companion object {
        const val TAG = "TagEditorRepository"
        /** Type de pochette ID3 « front cover ». */
        const val FRONT_COVER = 3
        const val WORK_DIR = "tag-edit"
    }

    /** Relance le media scanner pour que la bibliothèque reflète les nouveaux tags. */
    private fun rescan(path: String) {
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    }

    /** Lit les tags actuels pour préremplir le formulaire d'édition. */
    suspend fun read(song: Song): TagEdit = withContext(Dispatchers.IO) {
        runCatching {
            val tag = AudioFileIO.read(File(song.path)).tag ?: return@runCatching fallback(song)
            TagEdit(
                title = tag.getFirst(FieldKey.TITLE).orEmpty().ifBlank { song.title },
                artist = tag.getFirst(FieldKey.ARTIST).orEmpty().ifBlank { song.displayArtist },
                album = tag.getFirst(FieldKey.ALBUM).orEmpty().ifBlank { song.displayAlbum },
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).orEmpty(),
                genre = tag.getFirst(FieldKey.GENRE).orEmpty(),
                year = tag.getFirst(FieldKey.YEAR).orEmpty(),
                track = tag.getFirst(FieldKey.TRACK).orEmpty(),
                disc = tag.getFirst(FieldKey.DISC_NO).orEmpty(),
                comment = tag.getFirst(FieldKey.COMMENT).orEmpty(),
                lyrics = tag.getFirst(FieldKey.LYRICS).orEmpty(),
            )
        }.getOrElse { fallback(song) }
    }

    private fun ByteArray.isPng(): Boolean =
        size > 8 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()

    /** Si le fichier est illisible par jaudiotagger, on part de ce que MediaStore connaît. */
    private fun fallback(song: Song) = TagEdit(
        title = song.title,
        artist = song.displayArtist,
        album = song.displayAlbum,
        albumArtist = song.albumArtist.orEmpty(),
        genre = song.genre.orEmpty(),
        year = song.year.takeIf { it > 0 }?.toString().orEmpty(),
        track = song.track.takeIf { it > 0 }?.toString().orEmpty(),
        disc = song.disc.toString(),
        comment = "",
        lyrics = "",
    )
}
