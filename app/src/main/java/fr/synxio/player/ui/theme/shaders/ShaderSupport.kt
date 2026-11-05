package fr.synxio.player.ui.theme.shaders

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compile et garde les shaders AGSL. À la première exception (appareil qui refuse la
 * source), le drapeau `shadersUnavailable` est posé : tout repasse en Canvas, jusqu'au
 * bouton « Réessayer les shaders ». Les `RuntimeShader` sont typés `Any` ici pour que la
 * classe reste chargeable sous API 33 ; seul [Agsl] les touche.
 */
@Singleton
class ShaderSupport @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val mode: StateFlow<ShaderMode> = settings.settings
        .map { shaderMode(Build.VERSION.SDK_INT, it.shadersUnavailable) }
        .stateIn(scope, SharingStarted.Eagerly, shaderMode(Build.VERSION.SDK_INT, false))

    @Volatile private var aurora: Any? = null
    @Volatile private var glass: Any? = null
    @Volatile private var canvas: Any? = null

    fun aurora(): Any? = cached({ aurora }, { aurora = it }, ShaderSources.AURORA, "aurora")
    fun glass(): Any? = cached({ glass }, { glass = it }, ShaderSources.GLASS, "glass")
    fun canvas(): Any? = cached({ canvas }, { canvas = it }, ShaderSources.CANVAS, "canvas")

    /** Pose le drapeau : repli Canvas partout, persistant. */
    fun markUnavailable() {
        scope.launch { settings.setShadersUnavailable(true) }
    }

    private inline fun cached(get: () -> Any?, set: (Any?) -> Unit, source: String, name: String): Any? {
        if (mode.value != ShaderMode.AGSL) return null
        get()?.let { return it }
        val compiled = if (Build.VERSION.SDK_INT >= AGSL_MIN_SDK) Agsl.compile(source, name, ::markUnavailable) else null
        set(compiled)
        return compiled
    }

    @RequiresApi(AGSL_MIN_SDK)
    private object Agsl {
        fun compile(source: String, name: String, onFailure: () -> Unit): RuntimeShader? =
            try {
                RuntimeShader(source)
            } catch (t: Throwable) {
                Log.w("ShaderSupport", "Shader $name refusé, repli Canvas", t)
                onFailure()
                null
            }
    }
}

/** Support des shaders, `null` tant que la racine ne l'a pas fourni (aperçus, tests). */
val LocalShaderSupport = staticCompositionLocalOf<ShaderSupport?> { null }

/** Uniformes de l'aurore de l'image en cours, remplis par `AmbientBackdrop`. */
val LocalSceneUniforms = staticCompositionLocalOf { SceneUniforms() }
