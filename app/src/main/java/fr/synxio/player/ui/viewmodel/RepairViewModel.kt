package fr.synxio.player.ui.viewmodel

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.meta.BackupSession
import fr.synxio.player.data.meta.EnrichProgress
import fr.synxio.player.data.meta.FixField
import fr.synxio.player.data.meta.TagWorkshopRepository
import fr.synxio.player.data.meta.TrackFix
import fr.synxio.player.data.meta.WorkshopReport
import fr.synxio.player.data.repo.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WorkshopPhase { INTRO, ANALYSING, REVIEW, EMPTY, WRITING, DONE }

/** Filtres de la revue. */
enum class WorkshopFilter(val label: String) {
    ALL("Tout"),
    SURE("Sûrs"),
    CHECK("À vérifier"),
    ARTWORK("Pochettes"),
}

data class WorkshopUiState(
    val phase: WorkshopPhase = WorkshopPhase.INTRO,
    val done: Int = 0,
    val total: Int = 0,
    val fixes: List<TrackFix> = emptyList(),
    /** Chemin du fichier → champs cochés. */
    val selection: Map<String, Set<FixField>> = emptyMap(),
    val filter: WorkshopFilter = WorkshopFilter.ALL,
    val backup: Boolean = true,
    val backupBytes: Long = 0,
    val backupFits: Boolean = true,
    val report: WorkshopReport? = null,
    /** Vrai pendant une restauration, pour l'écran de progression. */
    val restoring: Boolean = false,
    val backups: List<BackupSession> = emptyList(),
    val consentRequest: IntentSender? = null,
    val message: String? = null,
) {
    val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
    val selectedFields: Int get() = selection.values.sumOf { it.size }
    val selectedFiles: Int get() = selection.count { it.value.isNotEmpty() }
    val sureCount: Int get() = fixes.count { it.sure }
    val checkCount: Int get() = fixes.count { !it.sure }
    val artworkCount: Int get() = fixes.count { it.fix(FixField.ARTWORK) != null }

    val visible: List<TrackFix>
        get() = when (filter) {
            WorkshopFilter.ALL -> fixes
            WorkshopFilter.SURE -> fixes.filter { it.sure }
            WorkshopFilter.CHECK -> fixes.filter { !it.sure }
            WorkshopFilter.ARTWORK -> fixes.filter { it.fix(FixField.ARTWORK) != null }
        }
}

/** Ce que l'utilisateur a demandé avant que le consentement Android ne revienne. */
private sealed interface PendingWrite {
    data object Apply : PendingWrite
    data class Restore(val session: BackupSession) : PendingWrite
}

/**
 * Atelier des tags : revue des corrections retrouvées par la cascade, puis écriture
 * dans les fichiers avec sauvegarde, ou restauration d'une séance précédente.
 */
@HiltViewModel
class RepairViewModel @Inject constructor(
    private val workshop: TagWorkshopRepository,
    private val music: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopUiState(backups = workshop.backups()))
    val state: StateFlow<WorkshopUiState> = _state.asStateFlow()

    /** Avancement de la cascade : l'atelier attend qu'elle ait fini pour proposer. */
    val cascade: StateFlow<EnrichProgress> = music.metadataProgress

    private var job: Job? = null
    private var pending: PendingWrite? = null

    fun analyse() {
        job?.cancel()
        update { copy(phase = WorkshopPhase.ANALYSING, done = 0, total = 0, report = null) }
        job = viewModelScope.launch {
            // Des propositions faites pendant que la cascade tourne seraient incomplètes.
            music.metadataProgress.first { !it.running }
            val fixes = workshop.analyse { done, total -> update { copy(done = done, total = total) } }
            val selection = fixes.associate { fix -> fix.path to fix.fixes.filter { it.sure }.mapTo(HashSet()) { it.field } }
            update {
                copy(
                    phase = if (fixes.isEmpty()) WorkshopPhase.EMPTY else WorkshopPhase.REVIEW,
                    fixes = fixes,
                    selection = selection,
                    filter = WorkshopFilter.ALL,
                )
            }
            refreshBackupCost()
        }
    }

    fun setFilter(filter: WorkshopFilter) = update { copy(filter = filter) }

    fun toggleField(fix: TrackFix, field: FixField) {
        val current = _state.value.selection[fix.path].orEmpty()
        val next = if (field in current) current - field else current + field
        update { copy(selection = selection + (fix.path to next)) }
        refreshBackupCost()
    }

    /** Tout le titre : coche tout s'il manque quelque chose, sinon décoche tout. */
    fun toggleTrack(fix: TrackFix) {
        val all = fix.fixes.mapTo(HashSet()) { it.field }
        val current = _state.value.selection[fix.path].orEmpty()
        update { copy(selection = selection + (fix.path to if (current.containsAll(all)) emptySet() else all)) }
        refreshBackupCost()
    }

    /** Coche ce qui est sûr, ou tout, dans la liste affichée. */
    fun selectVisible(includeDoubtful: Boolean) {
        val s = _state.value
        val updates = s.visible.associate { fix ->
            fix.path to fix.fixes.filter { includeDoubtful || it.sure }.mapTo(HashSet()) { it.field }
        }
        update { copy(selection = selection + updates) }
        refreshBackupCost()
    }

    fun clearVisible() {
        val updates = _state.value.visible.associate { it.path to emptySet<FixField>() }
        update { copy(selection = selection + updates) }
        refreshBackupCost()
    }

    fun setBackup(value: Boolean) = update { copy(backup = value) }

    private fun refreshBackupCost() {
        val paths = _state.value.selection.filterValues { it.isNotEmpty() }.keys
        val (bytes, fits) = workshop.backupCost(paths)
        update { copy(backupBytes = bytes, backupFits = fits) }
    }

    fun requestApply() {
        val paths = _state.value.selection.filterValues { it.isNotEmpty() }.keys
        if (paths.isEmpty()) return
        pending = PendingWrite.Apply
        val sender = workshop.writeRequestFor(paths)
        if (sender != null) update { copy(consentRequest = sender) } else runPending()
    }

    fun requestRestore(session: BackupSession) {
        pending = PendingWrite.Restore(session)
        val sender = workshop.restoreRequestFor(session)
        if (sender != null) update { copy(consentRequest = sender) } else runPending()
    }

    fun onConsentResult(granted: Boolean) {
        update { copy(consentRequest = null) }
        if (granted) runPending() else {
            pending = null
            update { copy(message = "Autorisation refusée : aucun fichier n'a été modifié") }
        }
    }

    private fun runPending() {
        when (val action = pending.also { pending = null }) {
            PendingWrite.Apply -> applyNow()
            is PendingWrite.Restore -> restoreNow(action.session)
            null -> Unit
        }
    }

    private fun applyNow() {
        val s = _state.value
        update { copy(phase = WorkshopPhase.WRITING, restoring = false, done = 0, total = s.selectedFiles) }
        job = viewModelScope.launch {
            val report = workshop.apply(s.fixes, s.selection, s.backup && s.backupFits) { done, total ->
                update { copy(done = done, total = total) }
            }
            update { copy(phase = WorkshopPhase.DONE, report = report, backups = workshop.backups()) }
        }
    }

    private fun restoreNow(session: BackupSession) {
        update { copy(phase = WorkshopPhase.WRITING, restoring = true, done = 0, total = session.files.size) }
        job = viewModelScope.launch {
            val report = workshop.restore(session) { done, total -> update { copy(done = done, total = total) } }
            update {
                copy(
                    phase = WorkshopPhase.INTRO,
                    restoring = false,
                    report = null,
                    backups = workshop.backups(),
                    message = if (report.failed.isEmpty()) "${report.ok} fichier${if (report.ok > 1) "s" else ""} remis comme avant"
                    else "${report.ok} remis · ${report.failed.size} échec(s) : ${report.failed.first().second}",
                )
            }
        }
    }

    fun deleteBackup(session: BackupSession) {
        workshop.delete(session)
        update { copy(backups = workshop.backups(), message = "Sauvegarde supprimée") }
    }

    fun backToIntro() = update { copy(phase = WorkshopPhase.INTRO, report = null) }

    fun consumeMessage() = update { copy(message = null) }

    private inline fun update(block: WorkshopUiState.() -> WorkshopUiState) {
        _state.value = _state.value.block()
    }
}
