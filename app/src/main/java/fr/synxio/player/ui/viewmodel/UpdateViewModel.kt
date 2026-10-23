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
import kotlinx.coroutines.flow.update
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

    // Toutes les écritures passent par `update` : la progression du téléchargement est
    // rapportée depuis un fil d'E/S, et un `value = value.copy(...)` concurrent d'une
    // action de l'utilisateur perdait l'une des deux modifications.
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
            is UpdateCheck.Available -> _state.update { it.copy(available = result.release) }
            else -> Unit
        }
    }

    /** Vérification déclenchée par l'utilisateur : celle-ci rapporte tout, succès comme échec. */
    fun checkNow() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, message = null) }

        viewModelScope.launch {
            settings.setLastUpdateCheck(System.currentTimeMillis())
            val result = updates.check()
            _state.update { current ->
                when (result) {
                    is UpdateCheck.Available ->
                        current.copy(checking = false, available = result.release)

                    UpdateCheck.UpToDate -> current.copy(
                        checking = false,
                        available = null,
                        message = "Synxio est à jour (version ${BuildConfig.VERSION_NAME})",
                    )

                    is UpdateCheck.Failed ->
                        current.copy(checking = false, message = result.reason)
                }
            }
        }
    }

    fun downloadAndInstall() {
        val release = _state.value.available ?: return
        if (_state.value.downloading) return

        // Sans l'autorisation système, le téléchargement serait perdu : on la demande avant.
        if (!updates.canInstall()) {
            _state.update {
                it.copy(
                    needsPermission = true,
                    pendingIntent = updates.installPermissionIntent(),
                    message = "Autorise Synxio à installer des applications, puis relance la mise à jour",
                )
            }
            return
        }

        _state.update { it.copy(downloading = true, progress = 0f, message = null) }
        job = viewModelScope.launch {
            // La progression n'est publiée qu'au changement de pourcentage entier : le
            // téléchargement rapporte chaque tampon lu, soit des milliers de
            // recompositions pour une barre qui n'en affiche que cent.
            var lastPercent = -1
            updates.download(release) { fraction ->
                val percent = (fraction * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    _state.update { it.copy(progress = fraction) }
                }
            }.onSuccess { apk ->
                if (!updates.hasMatchingSignature(apk)) {
                    apk.delete()
                    _state.update {
                        it.copy(
                            downloading = false,
                            message = "Signature différente de l'app installée : mise à jour refusée",
                        )
                    }
                    return@onSuccess
                }
                _state.update {
                    it.copy(
                        downloading = false,
                        progress = 1f,
                        pendingIntent = updates.installIntent(apk),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloading = false,
                        message = "Téléchargement interrompu : ${error.message}",
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(downloading = false, progress = 0f) }
    }

    fun consumeIntent() {
        _state.update { it.copy(pendingIntent = null, needsPermission = false) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun setAutoCheck(value: Boolean) = viewModelScope.launch {
        settings.setAutoCheckUpdates(value)
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
