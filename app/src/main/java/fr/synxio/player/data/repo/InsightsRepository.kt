package fr.synxio.player.data.repo

import fr.synxio.player.data.model.Insights
import fr.synxio.player.data.model.InsightsEngine
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les insights d'écoute (heatmap, série, horloge, mois, tempo, premières fois), recalculés
 * quand l'historique ou les traits changent, et à minuit. `null` tant que rien n'a été calculé.
 */
@Singleton
class InsightsRepository @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val traitsRepository: TrackTraitsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val insights: StateFlow<Insights?> = combine(
        historyRepository.history,
        traitsRepository.traits,
        localDays(),
    ) { history, traits, _ -> history to traits }
        // Une écoute met à jour l'historique ; inutile de tout recalculer à la milliseconde.
        .debounce(500)
        .mapLatest { (history, traits) ->
            InsightsEngine.compute(history, traits, LocalDate.now(), ZoneId.systemDefault())
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}
