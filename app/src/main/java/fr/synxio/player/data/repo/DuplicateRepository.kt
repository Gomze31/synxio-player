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
import kotlin.math.abs

/** Nature du doublon, qui détermine le degré de confiance. */
enum class DuplicateKind(val label: String) {
    /** Même durée à la seconde *et* même taille de fichier : c'est deux fois le même fichier. */
    IDENTICAL("Fichier identique"),

    /** Mêmes titre et artiste, durées très proches : deux copies du même morceau. */
    SAME_TRACK("Même morceau"),
}

/**
 * Un groupe de morceaux considérés comme doublons.
 *
 * [keeper] est la copie que Synxio propose de garder — la meilleure qualité disponible.
 * Tous les autres sont pré-cochés pour suppression, mais l'utilisateur décide.
 */
data class DuplicateGroup(
    val kind: DuplicateKind,
    val keeper: Song,
    val others: List<Song>,
    val selectedIds: Set<Long>,
) {
    val all: List<Song> get() = listOf(keeper) + others
    /** Espace récupéré si l'on supprime ce qui est coché. */
    val reclaimedBytes: Long get() = all.filter { it.id in selectedIds }.sumOf { it.sizeBytes }
}

/**
 * Détection des doublons de la bibliothèque.
 *
 * Le risque ici n'est pas de manquer un doublon, c'est d'en inventer un : proposer de
 * supprimer un live, un remix ou une version radio parce qu'ils portent le même titre
 * que l'original. Les règles sont donc volontairement strictes — deux morceaux ne sont
 * groupés que si leurs durées concordent à [DURATION_TOLERANCE_MS] près.
 */
@Singleton
class DuplicateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun findDuplicates(songs: List<Song>): List<DuplicateGroup> =
        withContext(Dispatchers.Default) {
            val groups = mutableListOf<DuplicateGroup>()
            val consumed = mutableSetOf<Long>()

            // 1) Copies strictement identiques : même taille d'octets et même durée.
            //    Aucun faux positif possible, on les traite en premier.
            songs.groupBy { it.sizeBytes to (it.durationMs / 1000) }
                .values
                .filter { it.size > 1 && it.first().sizeBytes > 0 }
                .forEach { copies ->
                    groups += buildGroup(copies, DuplicateKind.IDENTICAL)
                    consumed += copies.map { it.id }
                }

            // 2) Même titre + même artiste, durées concordantes. La normalisation retire
            //    accents, ponctuation et suffixes de re-upload pour que « Cœur (Official
            //    Video) » et « Coeur » se rejoignent.
            songs.asSequence()
                .filter { it.id !in consumed }
                .groupBy { trackKey(it) }
                .values
                .filter { it.size > 1 }
                .forEach { candidates ->
                    // Un même titre peut couvrir un single de 3 min et un remix de 7 min :
                    // on re-segmente par durée avant de conclure au doublon.
                    segmentByDuration(candidates).forEach { segment ->
                        if (segment.size > 1) groups += buildGroup(segment, DuplicateKind.SAME_TRACK)
                    }
                }

            groups.sortedWith(
                compareBy({ it.kind.ordinal }, { -it.all.sumOf { song -> song.sizeBytes } })
            )
        }

    /**
     * Regroupe les candidats en sous-ensembles de durées compatibles.
     *
     * On trie puis on coupe dès qu'un écart dépasse la tolérance : deux morceaux ne se
     * retrouvent ensemble que s'ils sont reliés par une chaîne de durées proches.
     */
    private fun segmentByDuration(candidates: List<Song>): List<List<Song>> {
        val sorted = candidates.sortedBy { it.durationMs }
        val segments = mutableListOf<MutableList<Song>>()
        sorted.forEach { song ->
            val current = segments.lastOrNull()
            if (current != null && abs(song.durationMs - current.last().durationMs) <= DURATION_TOLERANCE_MS) {
                current += song
            } else {
                segments += mutableListOf(song)
            }
        }
        return segments
    }

    /**
     * Choisit la copie à conserver.
     *
     * Le débit prime : à contenu égal, c'est le seul critère qui traduit vraiment la
     * qualité. La taille puis la date d'ajout ne servent qu'à départager, pour que le
     * choix reste stable d'une analyse à l'autre.
     */
    private fun buildGroup(songs: List<Song>, kind: DuplicateKind): DuplicateGroup {
        val ranked = songs.sortedWith(
            compareByDescending<Song> { it.approxBitrateKbps }
                .thenByDescending { it.sizeBytes }
                .thenBy { it.dateAddedSec }
        )
        val keeper = ranked.first()
        val others = ranked.drop(1)
        return DuplicateGroup(
            kind = kind,
            keeper = keeper,
            others = others,
            selectedIds = others.map { it.id }.toSet(),
        )
    }

    /** Clé de rapprochement : titre et artiste normalisés. */
    private fun trackKey(song: Song): String =
        "${normalize(song.title)}|${normalize(song.displayArtist)}"

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NOISE, " ")
            .replace(NON_ALPHANUMERIC, "")

    /**
     * Une seule demande de consentement pour toute la suppression.
     *
     * `createDeleteRequest` place les fichiers à la corbeille du système : l'utilisateur
     * garde trente jours pour revenir en arrière, ce qui est la bonne garantie pour une
     * opération irréversible sur ses fichiers.
     */
    fun deleteRequestFor(songs: List<Song>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (songs.isEmpty()) return null
        return runCatching {
            MediaStore.createTrashRequest(context.contentResolver, songs.map { it.uri }, true)
                .intentSender
        }.getOrNull()
    }

    private companion object {
        /** Deux encodages du même morceau diffèrent de quelques dizaines de ms au plus. */
        const val DURATION_TOLERANCE_MS = 3_000L

        val DIACRITICS = Regex("\\p{Mn}+")
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]")
        val NOISE = Regex(
            """\((?:official\s*)?(?:lyrics?|audio|video|music\s*video|visualizer|hd|hq|4k)[^)]*\)""" +
                """|\[[^\]]*(?:lyrics?|official|audio|video|hd|hq|4k)[^\]]*]""" +
                """|\b(?:official\s+(?:music\s+)?video|lyrics?\s+video|remaster(?:ed)?)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
