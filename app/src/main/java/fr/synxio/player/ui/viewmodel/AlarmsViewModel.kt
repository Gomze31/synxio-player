package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.alarm.AlarmScheduler
import fr.synxio.player.data.db.AlarmDao
import fr.synxio.player.data.db.AlarmEntity
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.repo.PlaylistRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler,
    playlistRepository: PlaylistRepository,
) : ViewModel() {

    val alarms: StateFlow<List<AlarmEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun save(alarm: AlarmEntity) = viewModelScope.launch {
        val enabled = alarm.copy(enabled = true)
        val rowId = dao.upsert(enabled)
        // Upsert rend l'identifiant d'une insertion, -1 pour une mise à jour.
        val saved = if (enabled.id == 0L) enabled.copy(id = rowId) else enabled
        scheduler.schedule(saved)
        _messages.emit("Réveil réglé : ${untilLabel(saved)}")
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        dao.setEnabled(alarm.id, enabled)
        val updated = alarm.copy(enabled = enabled)
        scheduler.schedule(updated)
        if (enabled) _messages.emit("Réveil réglé : ${untilLabel(updated)}")
    }

    fun delete(alarm: AlarmEntity) = viewModelScope.launch {
        scheduler.cancel(alarm.id)
        dao.delete(alarm.id)
    }

    /** Fait sonner le réveil dans une minute, pour l'essayer sans attendre demain matin. */
    fun tryNow(alarm: AlarmEntity) {
        scheduler.snooze(alarm.id, minutes = 1)
        _messages.tryEmit("Le réveil va sonner dans une minute")
    }

    /** « dans 7 h 12 min » jusqu'au prochain déclenchement. */
    fun untilLabel(alarm: AlarmEntity): String {
        val minutes = (scheduler.nextTriggerMillis(alarm) - System.currentTimeMillis()).coerceAtLeast(0) / 60_000 + 1
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours >= 24 -> "dans ${hours / 24} j ${hours % 24} h"
            hours > 0 -> "dans $hours h ${rest.toString().padStart(2, '0')} min"
            else -> "dans $rest min"
        }
    }
}
