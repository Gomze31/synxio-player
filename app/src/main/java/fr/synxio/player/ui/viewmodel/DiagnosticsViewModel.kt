package fr.synxio.player.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.diag.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Carte « Diagnostic » de l'écran À propos. */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: Diagnostics,
) : ViewModel() {

    private val _summary = MutableStateFlow(Diagnostics.Summary(logBytes = 0L, crashCount = 0))
    val summary: StateFlow<Diagnostics.Summary> = _summary

    private val _shareIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareIntents: SharedFlow<Intent> = _shareIntents

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _summary.value = withContext(Dispatchers.IO) { diagnostics.summary() }
    }

    fun export() = viewModelScope.launch {
        val intent = withContext(Dispatchers.IO) { diagnostics.exportIntent() }
        if (intent != null) {
            diagnostics.markCrashesSeen()
            _shareIntents.emit(intent)
        }
    }

    fun clear() = viewModelScope.launch {
        withContext(Dispatchers.IO) { diagnostics.clear() }
        refresh()
    }

    /** Le partage n'a pas pu s'ouvrir (aucune appli cible, activité refusée). */
    fun reportShareFailure(error: Throwable) {
        diagnostics.log("Diagnostics", Diagnostics.Level.W, "Ouverture du partage impossible", error)
    }
}
