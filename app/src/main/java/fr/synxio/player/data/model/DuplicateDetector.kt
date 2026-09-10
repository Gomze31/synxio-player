package fr.synxio.player.data.model

import java.text.Normalizer

/** Un groupe de morceaux jugés identiques. [keepIndex] désigne l'exemplaire recommandé. */
data class DuplicateGroup(
    val songs: List<Song>,
    val keepIndex: Int,
) {
    val keeper: Song get() = songs[keepIndex]
    val removable: List<Song> get() = songs.filterIndexed { i, _ -> i != keepIndex }
    val reclaimableBytes: Long get() = removable.sumOf { it.sizeBytes }
}

/**
 * Détection de doublons sur les tags, sans lire les fichiers : instantané même sur
 * une grosse bibliothèque.
 *
 * Le critère est volontairement prudent — le résultat sert à proposer des suppressions
 * de fichiers. Titre et artiste normalisés doivent être identiques ET les durées ne pas
 * s'écarter de plus de la tolérance : c'est ce qui distingue une vraie copie d'un live,
 * d'un remix ou d'une reprise, qui partagent titre et artiste mais jamais la durée.
 */
object DuplicateDetector {

    private val ACCENTS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    // Suffixes ajoutés par les sites de téléchargement et les rééditions.
    private val SUFFIXES = listOf(
        "\\((remaster|remastered|live|acoustic|radio edit|single version|album version|" +
            "bonus track|explicit|clean|mono|stereo|version|edit)[^)]*\\)",
        "\\[(remaster|remastered|live|acoustic|radio edit|single version|album version|" +
            "bonus track|explicit|clean|mono|stereo|version|edit)[^\\]]*\\]",
        "-\\s*(remaster|remastered|live|acoustic|radio edit|single version|album version)\\b.*$",
        "\\b(feat|ft|featuring|avec)\\b\\.?.*$",
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    private val NON_ALNUM = "[^a-z0-9]".toRegex()

    /**
     * Minuscules → accents retirés → suffixes parasites retirés → tout ce qui n'est ni
     * lettre ni chiffre retiré. « Formidable (Remastered 2011) » et « formidable »
     * donnent la même chaîne.
     */
    fun normalize(text: String): String {
        var s = text.lowercase().trim()
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(ACCENTS, "")
        SUFFIXES.forEach { s = it.replace(s, " ") }
        return s.replace(NON_ALNUM, "")
    }

    fun findGroups(songs: List<Song>, toleranceMs: Long = 3_000): List<DuplicateGroup> =
        songs
            .groupBy { normalize(it.title) to normalize(it.artist) }
            .values
            .filter { it.size > 1 }
            .flatMap { splitByDuration(it, toleranceMs) }
            .filter { it.size > 1 }
            .map { candidates ->
                val ordered = candidates.sortedWith(BEST_FIRST)
                DuplicateGroup(songs = ordered, keepIndex = 0)
            }
            .sortedByDescending { it.reclaimableBytes }

    /**
     * Dans un lot de même titre/artiste, sépare les versions dont les durées s'écartent
     * trop : le studio et le live d'un même titre ne doivent pas finir dans le même groupe.
     */
    private fun splitByDuration(candidates: List<Song>, toleranceMs: Long): List<List<Song>> {
        val buckets = mutableListOf<MutableList<Song>>()
        candidates.sortedBy { it.durationMs }.forEach { song ->
            val bucket = buckets.lastOrNull()
            if (bucket != null && song.durationMs - bucket.first().durationMs <= toleranceMs) {
                bucket += song
            } else {
                buckets += mutableListOf(song)
            }
        }
        return buckets
    }

    /** Complétude des tags : chaque champ renseigné vaut un point. */
    private fun tagScore(song: Song): Int =
        listOf(
            song.albumArtist.isNullOrBlank().not(),
            song.genre.isNullOrBlank().not(),
            song.year > 0,
            song.track > 0,
            song.album.isNotBlank() && song.album != Song.UNKNOWN_MEDIASTORE,
        ).count { it }

    private val BEST_FIRST: Comparator<Song> = compareByDescending<Song> { it.approxBitrateKbps }
        .thenByDescending { it.sizeBytes }
        .thenByDescending { tagScore(it) }
        .thenBy { it.path.length }
}
