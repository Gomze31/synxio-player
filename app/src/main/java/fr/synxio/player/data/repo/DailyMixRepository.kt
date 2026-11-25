package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.data.model.DailyMix
import fr.synxio.player.data.model.DailyMixEngine
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.TrackTraits
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les mixs du jour, recalculés quand la bibliothèque, les écoutes, les favoris ou
 * l'analyse du son changent, et à minuit.
 */
@Singleton
class DailyMixRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val similarity: SimilarityRepository,
    ratingRepository: RatingRepository,
    trackTraits: TrackTraitsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    val mixes: StateFlow<List<DailyMix>> = combine(
        combine(
            musicRepository.library.map { it.songs }.distinctUntilChanged { a, b -> a === b },
            musicRepository.allStats,
            musicRepository.favoriteIds,
            similarity.analysedCount,
            localDays(),
        ) { songs, stats, favorites, _, day -> Inputs(songs, stats, favorites, day) },
        ratingRepository.ratings,
        trackTraits.traits,
    ) { inputs, ratings, traits -> inputs.copy(ratings = ratings, traits = traits) }
        // Une écoute met à jour les stats ; inutile de tout recalculer à la milliseconde.
        .debounce(500)
        .mapLatest { inputs ->
            val songs = inputs.songs
            if (songs.isEmpty()) return@mapLatest emptyList()
            val seed = weeklyFavourite(songs, inputs.stats)
            val similar = seed?.let { similarity.similarTo(it, songs, limit = DailyMixEngine.MIX_SIZE) }.orEmpty()
            DailyMixEngine.build(
                songs = songs,
                stats = inputs.stats,
                favoriteIds = inputs.favorites,
                day = inputs.day,
                similarSeed = seed?.takeIf { similar.isNotEmpty() },
                similar = similar,
                ratings = inputs.ratings,
                traits = inputs.traits,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun byKey(key: String): DailyMix? = mixes.value.firstOrNull { it.key == key }

    /** Le titre le plus joué parmi ceux écoutés ces sept derniers jours, s'il a été analysé. */
    private fun weeklyFavourite(songs: List<Song>, stats: Map<Long, PlayStatEntity>): Song? {
        val cutoff = System.currentTimeMillis() - WEEK_MS
        return songs
            .filter { song -> stats[song.id]?.let { it.lastPlayedAt >= cutoff } == true }
            .filter(similarity::hasFingerprint)
            .maxByOrNull { stats.getValue(it.id).playCount }
    }

    /** Numéro du jour local, réémis juste après minuit. */
    private fun localDays(): Flow<Long> = flow {
        while (true) {
            emit(LocalDate.now().toEpochDay())
            val untilMidnight = Duration.between(
                LocalDateTime.now(),
                LocalDate.now().plusDays(1).atStartOfDay(),
            )
            delay(untilMidnight.toMillis() + 1_000)
        }
    }

    private data class Inputs(
        val songs: List<Song>,
        val stats: Map<Long, PlayStatEntity>,
        val favorites: Set<Long>,
        val day: Long,
        val ratings: Map<Long, Int> = emptyMap(),
        val traits: Map<String, TrackTraits> = emptyMap(),
    )

    private companion object {
        const val WEEK_MS = 7L * 24 * 3600 * 1000
    }
}
