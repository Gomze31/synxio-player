package fr.synxio.player.data.repo

import fr.synxio.player.data.model.Memory
import fr.synxio.player.data.model.MemoryEngine
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
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Les souvenirs de l'accueil, recalculés quand l'historique ou la bibliothèque change, et à minuit. */
@Singleton
class MemoryRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val memories: StateFlow<List<Memory>> = combine(
        musicRepository.library.map { it.songs to it.albums }.distinctUntilChanged { a, b -> a.first === b.first && a.second === b.second },
        historyRepository.history,
        localDays(),
    ) { (songs, albums), history, _ -> Triple(songs, albums, history) }
        // Une écoute met à jour l'historique ; inutile de tout recalculer à la milliseconde.
        .debounce(500)
        .mapLatest { (songs, albums, history) ->
            if (songs.isEmpty() || history.isEmpty()) emptyList()
            else MemoryEngine.build(songs, albums, history, LocalDate.now(), ZoneId.systemDefault())
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Numéro du jour local, réémis juste après minuit : les cartes changent avec la date. */
    private fun localDays(): Flow<Long> = flow {
        while (true) {
            emit(LocalDate.now().toEpochDay())
            val untilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay())
            delay(untilMidnight.toMillis() + 1_000)
        }
    }
}
