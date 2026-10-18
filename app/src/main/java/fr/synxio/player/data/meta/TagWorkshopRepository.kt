package fr.synxio.player.data.meta

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.meta.MetadataEnricher.Companion.sourceMap
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.repo.MusicRepository
import fr.synxio.player.data.repo.TagEdit
import fr.synxio.player.data.repo.TagEditorRepository
import fr.synxio.player.data.repo.TagWriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Une séance d'écriture sauvegardée : les fichiers tels qu'ils étaient avant. */
data class BackupSession(
    val id: String,
    val createdAt: Long,
    val files: List<BackupFile>,
    val totalBytes: Long,
)

data class BackupFile(val songId: Long, val path: String, val copy: String, val size: Long)

/** Bilan d'une écriture ou d'une restauration. */
data class WorkshopReport(
    val ok: Int,
    val failed: List<Pair<String, String>>,
    val backup: BackupSession?,
)

/**
 * Atelier des tags : écrit dans les fichiers les valeurs que la cascade a retrouvées.
 *
 * Trois garanties, parce que l'on touche aux fichiers de l'utilisateur :
 *  - les originaux sont copiés avant toute écriture (stockage de l'app, 30 jours) et la
 *    séance peut être annulée d'un geste ;
 *  - l'audio n'est jamais réencodé (jaudiotagger ne réécrit que les tags) ;
 *  - un seul consentement Android pour toute la sélection.
 */
@Singleton
class TagWorkshopRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val music: MusicRepository,
    private val enricher: MetadataEnricher,
    private val tagReader: FileTagReader,
    private val tagEditor: TagEditorRepository,
) {
    private val backupRoot: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_DIR).apply { mkdirs() }

    /** Prépare les propositions pour toute la bibliothèque. */
    suspend fun analyse(onProgress: (Int, Int) -> Unit): List<TrackFix> = withContext(Dispatchers.IO) {
        val songs = music.scannedSongs().filter { !it.isPodcast && !it.isAudiobook }
        songs.mapIndexedNotNull { index, song ->
            if (index % 8 == 0) onProgress(index, songs.size)
            runCatching { proposalFor(song) }
                .onFailure { Log.w(TAG, "Proposition impossible pour ${song.path}", it) }
                .getOrNull()
        }.also { onProgress(songs.size, songs.size) }
    }

    private suspend fun proposalFor(song: Song): TrackFix? {
        val (entry, lookup) = enricher.entryFor(song) ?: return null
        val tags = tagReader.read(song.path) ?: return null
        val sources = entry.sourceMap()
        return TagWorkshop.propose(
            WorkshopInput(
                songId = song.id,
                path = song.path,
                fileTags = tags.metadata,
                hasEmbeddedArt = tags.hasEmbeddedArt,
                resolved = TrackMetadata(
                    title = entry.title, artist = entry.artist, album = entry.album,
                    albumArtist = entry.albumArtist, year = entry.year, genre = entry.genre,
                    track = entry.track, disc = entry.disc,
                ),
                sources = sources,
                artSource = runCatching { ArtSource.valueOf(entry.artSource) }.getOrDefault(ArtSource.NONE),
                artUri = entry.artUri,
                ambiguousOrder = entry.altTitle != null,
                lookup = lookup?.let {
                    // La clé est « titre|artiste|durée » des seules sources locales :
                    // artiste vide = la recherche n'avait que le titre.
                    LookupInfo(it.confidence, byTitleOnly = it.key.split('|').getOrNull(1).isNullOrEmpty())
                },
            )
        )
    }

    /** Un seul dialogue système pour tous les fichiers (Android 11+). */
    fun writeRequestFor(paths: Collection<String>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val byPath = music.scannedSongs().associateBy { it.path }
        val uris = paths.mapNotNull { byPath[it]?.uri }
        if (uris.isEmpty()) return null
        return runCatching { MediaStore.createWriteRequest(context.contentResolver, uris).intentSender }.getOrNull()
    }

    /** Taille des copies de sécurité pour [paths], et si la place suffit. */
    fun backupCost(paths: Collection<String>): Pair<Long, Boolean> {
        val bytes = paths.sumOf { File(it).length() }
        val free = runCatching { StatFs(backupRoot.path).availableBytes }.getOrDefault(0L)
        return bytes to (free > bytes + SPARE_BYTES)
    }

    /**
     * Écrit les champs [selection] (titre → champs retenus) dans les fichiers.
     * [backup] : copie d'abord chaque fichier, pour pouvoir tout annuler.
     */
    suspend fun apply(
        fixes: List<TrackFix>,
        selection: Map<String, Set<FixField>>,
        backup: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): WorkshopReport = withContext(Dispatchers.IO) {
        val byPath = music.scannedSongs().associateBy { it.path }
        val work = fixes.mapNotNull { fix ->
            val fields = selection[fix.path].orEmpty()
            val song = byPath[fix.path]
            if (fields.isEmpty() || song == null) null else Triple(fix, fields, song)
        }
        val session = if (backup) backupSession(work.map { it.third }) else null
        if (backup && session == null) {
            return@withContext WorkshopReport(0, listOf("Sauvegarde" to "copie des originaux impossible, rien n'a été écrit"), null)
        }

        var ok = 0
        val failed = ArrayList<Pair<String, String>>()
        work.forEachIndexed { index, (fix, fields, song) ->
            onProgress(index, work.size)
            when (val result = tagEditor.write(song, editFor(fix, fields))) {
                is TagWriteResult.Success -> ok++
                is TagWriteResult.Failure -> failed += fix.fileName to result.message
                is TagWriteResult.NeedsUserConsent -> failed += fix.fileName to "autorisation d'écriture refusée"
            }
        }
        onProgress(work.size, work.size)
        music.refresh()
        WorkshopReport(ok, failed, session)
    }

    private fun editFor(fix: TrackFix, fields: Set<FixField>): TagEdit {
        fun value(field: FixField) = fix.fix(field)?.proposed?.takeIf { field in fields }
        val artwork = if (FixField.ARTWORK in fields) {
            fix.artworkPath?.let(::File)?.takeIf { it.exists() }?.readBytes()
        } else null
        return TagEdit(
            title = value(FixField.TITLE),
            artist = value(FixField.ARTIST),
            album = value(FixField.ALBUM),
            albumArtist = value(FixField.ALBUM_ARTIST),
            genre = value(FixField.GENRE),
            year = value(FixField.YEAR),
            track = value(FixField.TRACK),
            artwork = artwork,
        )
    }

    // --- Sauvegardes -----------------------------------------------------------------

    private fun backupSession(songs: List<Song>): BackupSession? = runCatching {
        prune()
        val now = System.currentTimeMillis()
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now))
        val dir = File(backupRoot, id).apply { mkdirs() }
        val files = songs.map { song ->
            val copy = File(dir, "${song.id}.${song.extension.lowercase().ifBlank { "bin" }}")
            File(song.path).inputStream().use { input -> copy.outputStream().use { input.copyTo(it) } }
            check(copy.length() == File(song.path).length()) { "copie incomplète de ${song.fileName}" }
            BackupFile(song.id, song.path, copy.path, copy.length())
        }
        val session = BackupSession(id, now, files, files.sumOf { it.size })
        File(dir, MANIFEST).writeText(session.toJson().toString())
        session
    }.onFailure { Log.w(TAG, "Sauvegarde des originaux impossible", it) }.getOrNull()

    fun backups(): List<BackupSession> = backupRoot.listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { dir -> runCatching { File(dir, MANIFEST).readText().toSession() }.getOrNull() }
        .sortedByDescending { it.createdAt }

    fun restoreRequestFor(session: BackupSession): IntentSender? = writeRequestFor(session.files.map { it.path })

    /** Remet les fichiers de [session] tels qu'ils étaient. */
    suspend fun restore(session: BackupSession, onProgress: (Int, Int) -> Unit): WorkshopReport = withContext(Dispatchers.IO) {
        val byPath = music.scannedSongs().associateBy { it.path }
        var ok = 0
        val failed = ArrayList<Pair<String, String>>()
        session.files.forEachIndexed { index, file ->
            onProgress(index, session.files.size)
            val song = byPath[file.path]
            if (song == null) {
                failed += file.path.substringAfterLast('/') to "fichier absent de la bibliothèque"
                return@forEachIndexed
            }
            when (val result = tagEditor.restore(song, File(file.copy))) {
                is TagWriteResult.Success -> ok++
                is TagWriteResult.Failure -> failed += song.fileName to result.message
                is TagWriteResult.NeedsUserConsent -> failed += song.fileName to "autorisation d'écriture refusée"
            }
        }
        onProgress(session.files.size, session.files.size)
        music.refresh()
        if (failed.isEmpty()) delete(session)
        WorkshopReport(ok, failed, null)
    }

    fun delete(session: BackupSession) {
        File(backupRoot, session.id).deleteRecursively()
    }

    /** Les sauvegardes de plus de 30 jours partent d'elles-mêmes. */
    private fun prune() {
        val limit = System.currentTimeMillis() - BACKUP_TTL_MS
        backups().filter { it.createdAt < limit }.forEach(::delete)
    }

    private fun BackupSession.toJson() = JSONObject()
        .put("id", id)
        .put("createdAt", createdAt)
        .put("files", JSONArray().apply {
            files.forEach {
                put(JSONObject().put("songId", it.songId).put("path", it.path).put("copy", it.copy).put("size", it.size))
            }
        })

    private fun String.toSession(): BackupSession {
        val json = JSONObject(this)
        val array = json.getJSONArray("files")
        val files = (0 until array.length()).map { i ->
            val f = array.getJSONObject(i)
            BackupFile(f.getLong("songId"), f.getString("path"), f.getString("copy"), f.getLong("size"))
        }
        return BackupSession(json.getString("id"), json.getLong("createdAt"), files, files.sumOf { it.size })
    }

    private companion object {
        const val TAG = "TagWorkshop"
        const val BACKUP_DIR = "tag-backups"
        const val MANIFEST = "manifest.json"
        const val BACKUP_TTL_MS = 30L * 24 * 60 * 60 * 1000
        /** Marge laissée libre sur le stockage après les copies. */
        const val SPARE_BYTES = 200L * 1024 * 1024
    }
}
