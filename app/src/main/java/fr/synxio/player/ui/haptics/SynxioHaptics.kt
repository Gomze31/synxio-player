package fr.synxio.player.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Vocabulaire haptique de Synxio : quatre intentions, pas des types système.
 *
 * Les écrans parlent en intentions (« un tap », « une confirmation ») ; la correspondance
 * avec les types Compose vit ici, et le réglage « Retour haptique » choisit entre
 * [ComposeHaptics] et [None] sans que les écrans le sachent.
 */
interface SynxioHaptics {
    /** Commande simple : lecture/pause, changement d'onglet. */
    fun tap()
    /** Action qui laisse une trace : cœur, ajout à la file, signet. */
    fun confirm()
    /** Cran franchi : changement de piste par balayage, seuil d'un geste. */
    fun tick()
    /** Fin d'un glissement : relâcher de la barre de progression. */
    fun release()

    /** Silence : réglage désactivé ou aperçu. */
    object None : SynxioHaptics {
        override fun tap() = Unit
        override fun confirm() = Unit
        override fun tick() = Unit
        override fun release() = Unit
    }
}

/** Haptique réelle, via [HapticFeedback] de Compose (respecte le réglage système). */
class ComposeHaptics(private val feedback: HapticFeedback) : SynxioHaptics {
    override fun tap() = feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
    override fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    override fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    override fun release() = feedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
}

val LocalSynxioHaptics = staticCompositionLocalOf<SynxioHaptics> { SynxioHaptics.None }

/** À fournir une fois à la racine, selon `Settings.hapticsEnabled`. */
@Composable
fun rememberSynxioHaptics(enabled: Boolean): SynxioHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(enabled, feedback) { if (enabled) ComposeHaptics(feedback) else SynxioHaptics.None }
}
