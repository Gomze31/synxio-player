package fr.synxio.player.data.model

/** Un titre à analyser et ce qui lui manque. */
data class AnalysisItem(val song: Song, val needsLoudness: Boolean, val needsFeatures: Boolean)

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
    ): List<AnalysisItem> {
        val items = songs.mapNotNull { song ->
            val needsLoudness = loudnessModified[song.path] != song.dateModifiedSec
            val wantsFeatures = !song.isPodcast && !song.isAudiobook
            val needsFeatures = wantsFeatures &&
                (featureModified[song.path] != song.dateModifiedSec || featureDimensions[song.path] != expectedDimensions)
            if (needsLoudness || needsFeatures) AnalysisItem(song, needsLoudness, needsFeatures) else null
        }
        if (priorityPaths.isEmpty()) return items
        val (first, rest) = items.partition { it.song.path in priorityPaths }
        return first + rest
    }
}
