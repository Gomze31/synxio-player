package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.discord.DiscordPresenceRepository
import fr.synxio.player.data.discord.PresenceStatus
import fr.synxio.player.data.repo.DiscordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscordUiState(
    val enabled: Boolean = false,
    val webhookUrl: String = "",
    val testing: Boolean = false,
    val message: String? = null,
    val presenceEnabled: Boolean = false,
    val presenceConfigured: Boolean = false,
    val presenceStatus: PresenceStatus = PresenceStatus.IDLE,
) {
    /** Ce que l'ecran doit dire de la liaison au client Discord. */
    val presenceSubtitle: String
        get() = when {
            !presenceConfigured -> "Aucun identifiant d'application compile"
            !presenceEnabled -> "Affiche le morceau en cours sur ton profil Discord"
            else -> when (presenceStatus) {
                PresenceStatus.PUBLISHING -> "Statut publie sur ton profil"
                PresenceStatus.REFUSED ->
                    "Discord a refuse - ajoute ton compte aux testeurs de l'application"
                PresenceStatus.UNREACHABLE ->
                    "Client Discord injoignable - verifie qu'il est installe et connecte"
                PresenceStatus.NOT_CONFIGURED -> "Aucun identifiant d'application compile"
                PresenceStatus.IDLE -> "En attente de la premiere lecture"
            }
        }

    val urlLooksValid: Boolean
        get() = webhookUrl.startsWith("https://discord.com/api/webhooks/") ||
            webhookUrl.startsWith("https://discordapp.com/api/webhooks/")

    /**
     * Affichage tronqué de l'URL.
     *
     * Le jeton en fin d'URL suffit à écrire dans le salon : l'afficher en entier dans
     * les réglages l'exposerait à toute personne regardant l'écran.
     */
    val maskedUrl: String
        get() = if (webhookUrl.length <= 40) webhookUrl
        else webhookUrl.take(37) + "…"
}

@HiltViewModel
class DiscordViewModel @Inject constructor(
    private val discord: DiscordRepository,
    private val presence: DiscordPresenceRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscordUiState())
    val state: StateFlow<DiscordUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _state.value = _state.value.copy(
                enabled = s.discordEnabled,
                webhookUrl = s.discordWebhookUrl,
                presenceEnabled = s.discordPresenceEnabled,
                presenceConfigured = presence.isConfigured,
            )
        }
        viewModelScope.launch {
            presence.status.collect { _state.value = _state.value.copy(presenceStatus = it) }
        }
    }

    fun setPresenceEnabled(value: Boolean) {
        _state.value = _state.value.copy(presenceEnabled = value)
        viewModelScope.launch {
            settings.setDiscordPresenceEnabled(value)
            // Desactiver doit effacer le statut : le laisser affiche apres coup ferait
            // croire que Synxio joue encore.
            if (!value) presence.clear()
        }
    }

    fun setUrl(value: String) {
        _state.value = _state.value.copy(webhookUrl = value)
        viewModelScope.launch { settings.setDiscordWebhookUrl(value) }
    }

    fun setEnabled(value: Boolean) {
        // Activer sans URL valide donnerait un interrupteur sans effet : on le refuse
        // en expliquant, plutôt que de laisser croire que la publication fonctionne.
        if (value && !_state.value.urlLooksValid) {
            _state.value = _state.value.copy(
                message = "Colle d'abord une URL de webhook Discord valide"
            )
            return
        }
        _state.value = _state.value.copy(enabled = value)
        viewModelScope.launch { settings.setDiscordEnabled(value) }
    }

    fun sendTest() {
        val url = _state.value.webhookUrl
        if (!_state.value.urlLooksValid) {
            _state.value = _state.value.copy(message = "URL de webhook invalide")
            return
        }
        _state.value = _state.value.copy(testing = true)
        viewModelScope.launch {
            val result = discord.sendTest(url)
            _state.value = _state.value.copy(
                testing = false,
                message = if (result.isSuccess) "Message envoyé — regarde le salon"
                else "Discord a refusé : vérifie l'URL et que le webhook existe toujours",
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
