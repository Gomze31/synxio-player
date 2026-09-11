package fr.synxio.player.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.BuildConfig
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.repo.Release
import fr.synxio.player.data.repo.UpdateCheck
import fr.synxio.player.data.repo.UpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val checking: Boolean = false,
    val available: Release? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    /** Intent à lancer : installation, ou réglage système d'autorisation. */
    val pendingIntent: Intent? = null,
    val needsPermission: Boolean = false,
) {
    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val hasUpdate: Boolean get() = available != null
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val isConfigured: Boolean get() = updates.isConfigured

    private var job: Job? = null

    init {
        checkAutomatically()
    }

    /**
     * Vérification silencieuse au lancement, une fois par jour au plus.
     *
     * Silencieuse : un échec réseau ne doit pas produire de message. L'utilisateur n'a
     * rien demandé, il n'a pas à être averti que quelque chose a échoué en arrière-plan.
     */
    private fun checkAutomatically() = viewModelScope.launch {
        val s = settings.settings.first()
        if (!s.autoCheckUpdates || !updates.isConfigured) return@launch

        val elapsed = System.currentTimeMillis() - s.lastUpdateCheck
        if (elapsed < CHECK_INTERVAL_MS) return@launch

        settings.setLastUpdateCheck(System.currentTimeMillis())
        when (val result = updates.check()) {
            is UpdateCheck.Available -> _state.value = _state.value.copy(available = result.release)
            else -> Unit
        }
    }

    /** Vérification déclenchée par l'utilisateur : celle-ci rapporte tout, succès comme échec. */
    fun checkNow() {
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true, message = null)

        viewModelScope.launch {
            settings.setLastUpdateCheck(System.currentTimeMillis())
            val result = updates.check()
            _state.value = when (result) {
                is UpdateCheck.Available ->
                    _state.value.copy(checking = false, available = result.release)

                UpdateCheck.UpToDate -> _state.value.copy(
                    checking = false,
                    available = null,
                    message = "Synxio est à jour (version ${BuildConfig.VERSION_NAME})",
                )

                is UpdateCheck.Failed ->
                    _state.value.copy(checking = false, message = result.reason)
            }
        }
    }

    fun downloadAndInstall() {
        val release = _state.value.available ?: return
        if (_state.value.downloading) return

        // Sans l'autorisation système, le téléchargement serait perdu : on la demande avant.
        if (!updates.canInstall()) {
            _state.value = _state.value.copy(
                needsPermission = true,
                pendingIntent = updates.installPermissionIntent(),
                message = "Autorise Synxio à installer des applications, puis relance la mise à jour",
            )
            return
        }

        _state.value = _state.value.copy(downloading = true, progress = 0f, message = null)
        job = viewModelScope.launch {
            updates.download(release) { fraction ->
                _state.value = _state.value.copy(progress = fraction)
            }.onSuccess { apk ->
                if (!updates.hasMatchingSignature(apk)) {
                    apk.delete()
                    _state.value = _state.value.copy(
                        downloading = false,
                        message = "Signature différente de l'app installée : mise à jour refusée",
                    )
                    return@onSuccess
                }
                _state.value = _state.value.copy(
                    downloading = false,
                    progress = 1f,
                    pendingIntent = updates.installIntent(apk),
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    downloading = false,
                    message = "Téléchargement interrompu : ${it.message}",
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(downloading = false, progress = 0f)
    }

    fun consumeIntent() {
        _state.value = _state.value.copy(pendingIntent = null, needsPermission = false)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun setAutoCheck(value: Boolean) = viewModelScope.launch {
        settings.setAutoCheckUpdates(value)
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
