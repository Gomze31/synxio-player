package fr.synxio.player.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * L'app est-elle à l'écran ? Alimenté par `MainActivity` (`onStart` / `onStop`). Remplace
 * `ProcessLifecycleOwner`, dont la bibliothèque n'est pas dépendue : une seule activité,
 * un compteur suffit.
 */
object AppVisibility {
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()
    private var started = 0

    fun onStart() { started++; _foreground.value = true }
    fun onStop() { started = (started - 1).coerceAtLeast(0); _foreground.value = started > 0 }
}
