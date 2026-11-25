package fr.synxio.player.data.repo

import fr.synxio.player.data.media.AudioFeatureAnalyzer
import fr.synxio.player.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

enum class Mood(val label: String) {
    SPORT("Sport & Énergie"),
    RELAXATION("Détente & Calme"),
    FOCUS("Concentration")
}

@Singleton
class MoodRadioRepository @Inject constructor(
    private val similarityRepository: SimilarityRepository,
    private val traitsRepository: TrackTraitsRepository,
) {
    /**
     * Filtre les morceaux correspondant à une humeur spécifique : énergie et tempo de
     * l'empreinte 2 quand ils existent, sinon l'heuristique spectrale standardisée.
     */
    fun generateMix(mood: Mood, allSongs: List<Song>, limit: Int = 50): List<Song> {
        val standardised = similarityRepository.allStandardised()
        val traits = traitsRepository.traits.value
        if (standardised.isEmpty() && traits.isEmpty()) return emptyList()

        val scoredSongs = allSongs.mapNotNull { song ->
            val fromTraits = traits[song.path]?.let { evaluateMood(mood, it) }
            val score = fromTraits
                ?: standardised[song.path]?.let { evaluateMood(mood, it) }
                ?: return@mapNotNull null
            if (score > 0f) song to score else null
        }

        // On trie par meilleur score et on limite à `limit`
        return scoredSongs
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .shuffled() // Mélange pour ne pas avoir toujours exactement le même ordre
    }

    /** Sport : énergique et 120–180 BPM ; Détente : énergie ≤ 0,35 ; Concentration : énergie moyenne et tempo posé. */
    private fun evaluateMood(mood: Mood, traits: fr.synxio.player.data.model.TrackTraits): Float = when (mood) {
        Mood.SPORT -> if (traits.energy >= 0.6f && (!traits.hasBpm || traits.bpm in 120f..180f)) traits.energy else -1f
        Mood.RELAXATION -> if (traits.energy <= 0.35f) 1f - traits.energy else -1f
        Mood.FOCUS -> if (traits.energy in 0.3f..0.6f && (!traits.hasBpm || traits.bpm <= 130f)) 1f - kotlin.math.abs(traits.energy - 0.45f) else -1f
    }

    private fun evaluateMood(mood: Mood, features: FloatArray): Float {
        if (features.size != AudioFeatureAnalyzer.DIMENSIONS) return -1f
        
        // Indices des features (voir AudioFeatureAnalyzer)
        // 0..19: Bands (Mean, Dev)
        // 20, 21: Centroid (Mean, Dev)
        // 22, 23: Flatness (Mean, Dev)
        // 24, 25: Zero Crossings (Mean, Dev)
        // 26: Flux (Mean)

        val centroidMean = features[20]
        val centroidDev = features[21]
        val flatnessMean = features[22]
        val crossingsDev = features[25]
        val fluxMean = features[26]

        return when (mood) {
            Mood.SPORT -> {
                // Sport: Haute énergie percussive (flux), brillance (centroid)
                // Plus les valeurs sont au-dessus de la moyenne (0), meilleur est le score.
                val score = fluxMean * 1.5f + centroidMean
                if (score > 0.5f) score else -1f
            }
            Mood.RELAXATION -> {
                // Détente: Faible énergie percussive (flux < 0), planéité élevée (flatness > 0)
                // et potentiellement moins de hautes fréquences (centroid < 0).
                val score = (flatnessMean * 1.5f) - fluxMean - centroidMean
                if (score > 0.5f) score else -1f
            }
            Mood.FOCUS -> {
                // Concentration: Musique constante/prévisible. 
                // Faibles écart-types (centroidDev < 0, crossingsDev < 0) -> moins de variations.
                val score = -centroidDev - crossingsDev
                if (score > 0.5f) score else -1f
            }
        }
    }
}
