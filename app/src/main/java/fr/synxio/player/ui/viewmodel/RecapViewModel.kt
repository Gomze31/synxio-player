package fr.synxio.player.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.Recap
import fr.synxio.player.data.model.RecapCalculator
import fr.synxio.player.data.model.RecapPeriod
import fr.synxio.player.data.model.StatsPeriod
import fr.synxio.player.data.repo.HistoryRepository
import fr.synxio.player.data.repo.ShareCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecapViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    private val shareCards: ShareCardRepository,
) : ViewModel() {

    private val _period = MutableStateFlow(RecapPeriod.MONTH)
    val period: StateFlow<RecapPeriod> = _period.asStateFlow()

    /** `null` tant que le journal n'est pas chargé : l'écran ne montre pas un faux « vide ». */
    // Flux froid de Room plutôt que `history`, partagé avec une valeur initiale vide : le
    // récap aurait d'abord affiché « pas encore assez d'écoutes » le temps du chargement.
    val recap: StateFlow<Recap?> = combine(
        historyRepository.getHistoryForPeriod(StatsPeriod.ALL_TIME),
        _period,
    ) { history, period ->
        RecapCalculator.compute(history, period, LocalDate.now())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(period: RecapPeriod) {
        _period.value = period
    }

    suspend fun shareIntent(recap: Recap): Intent? = shareCards.createRecapShareIntent(recap)
}
