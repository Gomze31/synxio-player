package fr.synxio.player.data.repo

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/** Ce qui cloche dans les tags d'un morceau. */
enum class TagIssue(val label: String) {
    UNKNOWN_ARTIST("Artiste manquant"),
    CHANNEL_ARTIST("Artiste = chaîne de re-upload"),
    NOISY_TITLE("Titre pollué (Lyrics, Official Video…)"),
    DAMAGED_ACCENTS("Accents perdus dans les tags"),
    ALBUM_IS_TITLE("Album identique au titre"),
    COMPILATION("Album de compilation générique"),
}

/** Proposition de correction pour un morceau. */
data class RepairProposal(
    val song: Song,
    val issues: List<TagIssue>,
    val match: MetadataMatch?,
    val year: Int?,
    val genre: String?,
    val selected: Boolean = true,
) {
    val hasMatch: Boolean get() = match != null

    /** Résumé « avant → après » affiché dans la liste. */
    val changes: List<Pair<String, String>>
        get() {
            val m = match ?: return emptyList()
            return buildList {
                if (!m.title.equals(song.title, true)) add("Titre" to m.title)
                if (!m.artist.equals(song.displayArtist, true)) add("Artiste" to m.artist)
                if (!m.album.equals(song.displayAlbum, true)) add("Album" to m.album)
                year?.let { add("Année" to it.toString()) }
                genre?.let { add("Genre" to it) }
                if (m.artworkUrl != null) add("Pochette" to "haute résolution")
            }
        }
}

data class RepairProgress(
    val analysed: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
) {
    val fraction: Float get() = if (total > 0) analysed.toFloat() / total else 0f
}

/**
 * Analyse la bibliothèque et propose des corrections de tags en masse.
 *
 * Pensé pour les bibliothèques constituées de rips : artistes qui sont en fait des
 * chaînes YouTube, titres suffixés « (Lyrics) », albums de compilation à la place
 * du vrai album, et accents disparus.
 */
@Singleton
class LibraryRepairRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadata: MetadataRepository,
    private val tagEditor: TagEditorRepository,
) {

    /** Repère les morceaux dont les tags méritent un examen. */
    fun detectIssues(song: Song): List<TagIssue> = buildList {
        val artist = song.displayArtist
        if (artist == Song.UNKNOWN_ARTIST) add(TagIssue.UNKNOWN_ARTIST)
        else if (CHANNEL_NOISE.any { artist.equals(it, true) }) add(TagIssue.CHANNEL_ARTIST)

        if (NOISE_REGEX.containsMatchIn(song.title)) add(TagIssue.NOISY_TITLE)
        if (song.displayAlbum.equals(song.title, true)) add(TagIssue.ALBUM_IS_TITLE)
        if (COMPILATION_HINTS.any { song.displayAlbum.contains(it, true) }) add(TagIssue.COMPILATION)
        if (hasDamagedAccents(song)) add(TagIssue.DAMAGED_ACCENTS)
    }

    /**
     * Détecte les accents perdus : le nom de fichier en contient, les tags non.
     *
     * C'est le symptôme d'un tag ID3 écrit en Latin-1 dont les octets accentués ont
     * été supprimés à l'encodage. L'information n'est pas récupérable depuis le tag —
     * seul le nom de fichier l'a conservée.
     */
    private fun hasDamagedAccents(song: Song): Boolean {
        val fileHasAccents = song.fileName.any { it.isAccented() }
        if (!fileHasAccents) return false
        val tagsHaveAccents = (song.title + song.displayArtist + song.displayAlbum)
            .any { it.isAccented() }
        return !tagsHaveAccents
    }

    private fun Char.isAccented(): Boolean =
        this.code > 127 && Normalizer.normalize(this.toString(), Normalizer.Form.NFD).length > 1

    /**
     * Analyse une liste de morceaux et cherche une correspondance pour chacun.
     *
     * Quand les tags sont abîmés, la requête part du **nom de fichier** plutôt que du
     * tag : c'est la seule source qui a gardé les accents.
     */
    suspend fun analyse(
        songs: List<Song>,
        minConfidence: Int,
        onProgress: (Int, Int) -> Unit,
    ): List<RepairProposal> = withContext(Dispatchers.IO) {
        val suspects = songs.mapNotNull { song ->
            val issues = detectIssues(song)
            if (issues.isEmpty()) null else song to issues
        }

        suspects.mapIndexedNotNull { index, (song, issues) ->
            onProgress(index + 1, suspects.size)

            val fromTags = metadata.findMatches(song, limit = 5).getOrNull().orEmpty()
            val candidates = fromTags.ifEmpty {
                // Repli sur le nom de fichier, qui a souvent gardé les accents.
                metadata.search(
                    query = song.fileName.substringBeforeLast('.').cleanFileName(),
                    expectedDurationSec = song.durationMs / 1000,
                    limit = 5,
                ).getOrNull().orEmpty()
            }

            val best = candidates.firstOrNull { it.confidence >= minConfidence }
            val details = best?.let { metadata.albumDetails(it.albumId) }

            RepairProposal(
                song = song,
                issues = issues,
                match = best,
                year = details?.year,
                genre = details?.genre,
                selected = best != null,
            )
        }.filter { it.hasMatch }
    }

    /**
     * Demande en **une seule fois** l'autorisation d'écrire dans tous les fichiers.
     *
     * Sans ça, Android afficherait une boîte de dialogue par fichier : inutilisable
     * sur 300 morceaux. `createWriteRequest` accepte une liste d'URI.
     */
    fun writeRequestFor(songs: List<Song>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = songs.map { it.uri }
        if (uris.isEmpty()) return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        }.getOrNull()
    }

    /**
     * Résultat d'une application en masse.
     *
     * [firstError] existe parce que la première version ne renvoyait que des compteurs :
     * un échec total affichait « 0 corrigé · 121 échecs » sans jamais dire pourquoi, ce
     * qui rendait le diagnostic impossible depuis l'appareil.
     */
    data class ApplyResult(val ok: Int, val failed: Int, val firstError: String? = null)

    /** Applique les propositions retenues. */
    suspend fun apply(
        proposals: List<RepairProposal>,
        withArtwork: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): ApplyResult = withContext(Dispatchers.IO) {
        var ok = 0
        var failed = 0
        var firstError: String? = null

        proposals.forEachIndexed { index, proposal ->
            onProgress(index + 1, proposals.size)
            val match = proposal.match ?: return@forEachIndexed

            val artwork = if (withArtwork && match.artworkUrl != null) {
                metadata.downloadArtwork(match.artworkUrl)
            } else {
                null
            }

            val edit = TagEdit(
                title = match.title,
                artist = match.artist,
                album = match.album,
                albumArtist = match.artist,
                genre = proposal.genre,
                year = proposal.year?.toString(),
                artwork = artwork,
            )

            when (val result = tagEditor.write(proposal.song, edit)) {
                is TagWriteResult.Success -> ok++

                is TagWriteResult.Failure -> {
                    failed++
                    if (firstError == null) firstError = result.message
                }

                is TagWriteResult.NeedsUserConsent -> {
                    failed++
                    if (firstError == null) firstError = "autorisation d'écriture refusée"
                }
            }
        }

        ApplyResult(ok, failed, firstError)
    }

    private fun String.cleanFileName(): String = this
        .replace(Regex("^\\d{1,3}\\s*[-._]\\s*"), "")   // « 47 - » en tête
        .replace(NOISE_REGEX, " ")
        .replace('_', ' ')
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    private companion object {
        val CHANNEL_NOISE = setOf(
            "7clouds", "Various Artists", "Topic", "Unknown", "VA",
            "NoCopyrightSounds", "Trap Nation", "Artiste inconnu",
        )
        val COMPILATION_HINTS = listOf(
            "hits", "compilation", "best of", "nrj", "fun radio", "mix ", "playlist",
        )
        val NOISE_REGEX = Regex(
            """\((?:official\s*)?(?:lyrics?|audio|video|music\s*video|visualizer|hd|hq|4k)[^)]*\)""" +
                """|\[[^\]]*(?:lyrics?|official|audio|video|hd|hq|4k)[^\]]*]""" +
                """|\b(?:official\s+(?:music\s+)?video|lyrics?\s+video|audio\s+officiel)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
