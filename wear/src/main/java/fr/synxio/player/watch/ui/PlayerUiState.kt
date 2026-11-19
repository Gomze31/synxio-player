package fr.synxio.player.watch.ui

import fr.synxio.player.wear.protocol.WearState

/** Ce que l'écran lecteur dessine. */
sealed interface PlayerUiState {
    data object PlayServicesMissing : PlayerUiState
    data object PhoneUnreachable : PlayerUiState
    /** Téléphone joignable, rien en cours (ou jamais rien reçu). */
    data object Idle : PlayerUiState
    data class VersionMismatch(val phoneNewer: Boolean) : PlayerUiState
    /** Après un lancement, jusqu'au prochain état non vide. */
    data object Connecting : PlayerUiState
    data class Playing(
        val state: WearState,
        /** Après bascule optimiste et péremption. */
        val isPlaying: Boolean,
        val favorite: Boolean,
        /** 0..1, après bascule optimiste. */
        val volume: Float,
        val volumeVisible: Boolean,
    ) : PlayerUiState
}
