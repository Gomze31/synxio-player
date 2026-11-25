package fr.synxio.player.data.model

/** Un titre à analyser et ce qui lui manque. */
data class AnalysisItem(
    val song: Song,
    val needsLoudness: Boolean,
    val needsFeatures: Boolean,
    /** Empreinte 2 (tempo, tonalité, énergie) absente ou d'une version antérieure. */
    val needsTraits: Boolean = false,
)

/**
 * Décide quels titres le Worker d'analyse doit traiter, et dans quel ordre.
 *
 * Pur : les mesures connues sont passées en paramètres (chemin → `fileModifiedSec`), pas
 * lues en base. Les podcasts et livres audio n'ont pas d'empreinte sonore (ils ne servent
 * ni à la similarité ni aux ambiances), mais ont un niveau pour la normalisation.
 */
object AnalysisPlanner {

    fun plan(
        songs: List<Song>,
        loudnessModified: Map<String, Long>,
        featureModified: Map<String, Long>,
        featureDimensions: Map<String, Int>,
        expectedDimensions: Int,
        priorityPaths: Set<String> = emptySet(),
        traitsVersion: Map<String, Int> = emptyMap(),
        expectedTraitsVersion: Int = TrackTraits.VERSION,
    ): List<AnalysisItem> {
        val items = songs.mapNotNull { song ->
            val needsLoudness = loudnessModified[song.path] != song.dateModifiedSec
            val wantsFeatures = !song.isPodcast && !song.isAudiobook
            val needsFeatures = wantsFeatures &&
                (featureModified[song.path] != song.dateModifiedSec || featureDimensions[song.path] != expectedDimensions)
            val needsTraits = wantsFeatures && (needsFeatures || (traitsVersion[song.path] ?: 0) < expectedTraitsVersion)
            if (needsLoudness || needsFeatures || needsTraits) AnalysisItem(song, needsLoudness, needsFeatures, needsTraits) else null
        }
        if (priorityPaths.isEmpty()) return items
        val (first, rest) = items.partition { it.song.path in priorityPaths }
        return first + rest
    }
}
