# Lumen Vivant 5.8.0 — plan 2 : verre AGSL et canvas génératif

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Donner une épaisseur à la lumière : aurore et verre rendus par shaders AGSL sur API 33+, un canvas animé derrière la pochette, avec repli Canvas en dessous et un drapeau qui désactive les shaders dès qu'un appareil les refuse.

**Architecture:** Les trois shaders partagent un même « champ » d'aurore (trois halos gaussiens) décrit par `SceneUniforms`, rempli à chaque image par `AmbientBackdrop` depuis `SceneParams` et publié par `LocalSceneUniforms` ; le verre réfracté redessine ce champ déformé sous son voile, puisqu'il ne peut pas échantillonner le fond. `ShaderSupport` (singleton Hilt) compile et met en cache les `RuntimeShader`, et pose le drapeau `shadersUnavailable` à la première exception ; `shaderMode()` (pur) choisit AGSL ou Canvas. Le canvas suit le pouls en direct, rien n'est précalculé.

**Tech Stack:** Kotlin 2.4, Compose BOM 2026.09 (`ShaderBrush`, `drawWithCache`, `onGloballyPositioned`), `android.graphics.RuntimeShader` (API 33), Hilt, DataStore, JUnit 4 sur la JVM.

**Spec:** `docs/superpowers/specs/2026-11-05-lumen-vivant-5.8.0-design.md` (section 3 et les réglages « Canvas animé » / « Réessayer les shaders » de la section 6)

## Global Constraints

- Aucune nouvelle dépendance Gradle. `RuntimeShader` n'est référencé que dans des classes annotées `@RequiresApi(33)` ; sous API 33, ou drapeau levé, le chemin Canvas actuel reste intact.
- Les sources AGSL vivent dans `ui/theme/shaders/ShaderSources.kt` (constantes Kotlin), pas dans `res/raw` : écart assumé par rapport au spec §3.2, même texte de shader, testable sur la JVM.
- Pouls lu uniquement dans la phase de dessin ; aucune recomposition par tick. Les shaders se redessinent sur chaque tick du pouls (30 Hz) et, hors pouls, seulement sur la dérive de l'aurore ou la boucle du canvas.
- Verre réfracté sur deux surfaces seulement : le mini-lecteur et le cadre du skin Carte du lecteur. `glass()` sans `refract` est inchangé octet pour octet.
- Canvas : boucle de 8 s à vitesse 1, vitesse `0.5 + rms`, contraste `0.6 + 0.4 × bass`, couleurs `vibrant`, `darkMuted`, `dominant` de la palette ; skins Immersif et Carte seulement ; mêmes coupures que la scène (`LocalScenePulse == null` → image figée) ; réglage « Canvas animé » (`Settings.canvasAnimated`, clé `canvas_animated`, défaut `true`).
- Drapeau `Settings.shadersUnavailable` (clé `shaders_unavailable`, défaut `false`) ; bouton « Réessayer les shaders » visible seulement si le drapeau est levé.
- Bornes des uniformes : rayons en pixels > 0, alphas dans 0..1, `dispersion = 1 px`, `grain = 0.015`, `wobble = 2 px`.
- Textes de l'interface en français, tutoiement ; KDoc et commentaires en français, pas de TODO.
- Commandes Gradle lancées depuis PowerShell (`.\gradlew.bat`), jamais depuis Git Bash. Tests JVM : `.\gradlew.bat :app:testDebugUnitTest --tests "<classe>"`.
- Chaque tâche se termine par un commit sur `dev-5.5`. Pas de push. Le rendu des shaders n'est vérifiable que sur l'appareil : chaque tâche le dit dans son rapport.

---

## Structure des fichiers

| Fichier | Rôle |
|---|---|
| `ui/theme/shaders/ShaderMode.kt` (créer) | `enum ShaderMode`, `shaderMode(sdkInt, unavailable)`. Pur. |
| `ui/theme/shaders/ShaderSources.kt` (créer) | Prélude commun + sources AGSL `AURORA`, `GLASS`, `CANVAS`. |
| `ui/theme/shaders/SceneUniforms.kt` (créer) | Tableaux d'uniformes de l'aurore et `fillSceneUniforms(...)`. Pur. |
| `ui/theme/shaders/ShaderSupport.kt` (créer) | Singleton Hilt : mode courant, compilation/cache des `RuntimeShader`, pose du drapeau. `LocalShaderSupport`, `LocalSceneUniforms`. |
| `ui/theme/Glass.kt` (modifier) | `AmbientBackdrop` : chemin AGSL + remplissage des uniformes ; `glass(refract)`. |
| `ui/components/ArtworkCanvas.kt` (créer) | Canvas génératif, AGSL et repli. |
| `ui/screens/nowplaying/NowPlayingScreen.kt` (modifier) | `ArtworkStage` : canvas derrière la pochette, `refract` sur le cadre Carte. |
| `ui/components/MiniPlayer.kt` (modifier) | `refract = true`. |
| `ui/SynxioRoot.kt` (modifier) | Fourniture de `LocalShaderSupport` et `LocalSceneUniforms`. |
| `ui/viewmodel/AppViewModel.kt` (modifier) | Expose `shaderSupport`. |
| `core/prefs/SettingsRepository.kt`, `ui/viewmodel/SettingsViewModel.kt`, `ui/screens/SettingsScreen.kt` (modifier) | Réglages `canvasAnimated`, `shadersUnavailable`, bouton de réessai. |
| `docs/release-notes/v5.8.0.md` (modifier) | Section verre/canvas, checklist. |

---

### Task 1 : `ShaderMode` et réglages

**Files:**
- Create: `app/src/main/java/fr/synxio/player/ui/theme/shaders/ShaderMode.kt`
- Modify: `app/src/main/java/fr/synxio/player/core/prefs/SettingsRepository.kt:181,310,429,579` (après `sceneReactive` à chaque endroit)
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/SettingsViewModel.kt:223`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt:500-507`
- Test: `app/src/test/java/fr/synxio/player/ui/theme/shaders/ShaderModeTest.kt`

**Interfaces:**
- Produces: `enum class ShaderMode { AGSL, CANVAS }` ; `fun shaderMode(sdkInt: Int, unavailable: Boolean): ShaderMode` ; `Settings.canvasAnimated: Boolean`, `Settings.shadersUnavailable: Boolean` ; `SettingsRepository.setCanvasAnimated(Boolean)`, `setShadersUnavailable(Boolean)` ; `SettingsViewModel.setCanvasAnimated(Boolean)`, `retryShaders()`.

- [ ] **Step 1 : Test**

```kotlin
package fr.synxio.player.ui.theme.shaders

import org.junit.Assert.assertEquals
import org.junit.Test

class ShaderModeTest {
    @Test
    fun `API 33 et plus sans drapeau - AGSL`() {
        assertEquals(ShaderMode.AGSL, shaderMode(sdkInt = 33, unavailable = false))
        assertEquals(ShaderMode.AGSL, shaderMode(sdkInt = 36, unavailable = false))
    }

    @Test
    fun `sous API 33 - Canvas quoi qu'il arrive`() {
        assertEquals(ShaderMode.CANVAS, shaderMode(sdkInt = 32, unavailable = false))
        assertEquals(ShaderMode.CANVAS, shaderMode(sdkInt = 28, unavailable = true))
    }

    @Test
    fun `drapeau leve - Canvas meme sur API 33`() {
        assertEquals(ShaderMode.CANVAS, shaderMode(sdkInt = 34, unavailable = true))
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.theme.shaders.ShaderModeTest"`
Attendu : échec de compilation.

- [ ] **Step 3 : `ShaderMode.kt`**

```kotlin
package fr.synxio.player.ui.theme.shaders

/** Chemin de rendu de la scène : shaders AGSL (API 33+) ou Canvas classique. */
enum class ShaderMode { AGSL, CANVAS }

/**
 * AGSL exige l'API 33 et un appareil qui n'a jamais refusé de compiler nos shaders
 * (drapeau `shadersUnavailable`). Pur : la version et le drapeau sont passés en argument.
 */
fun shaderMode(sdkInt: Int, unavailable: Boolean): ShaderMode =
    if (sdkInt >= AGSL_MIN_SDK && !unavailable) ShaderMode.AGSL else ShaderMode.CANVAS

const val AGSL_MIN_SDK = 33
```

- [ ] **Step 4 : Réglages**

`SettingsRepository.kt`, après `val sceneReactive: Boolean = true,` :

```kotlin
    /** Canvas animé derrière la pochette (skins Immersif et Carte). */
    val canvasAnimated: Boolean = true,
    /** Levé quand un shader AGSL a refusé de compiler sur cet appareil : repli Canvas définitif, sauf réessai. */
    val shadersUnavailable: Boolean = false,
```

Après `val SCENE_REACTIVE = ...` : `val CANVAS_ANIMATED = booleanPreferencesKey("canvas_animated")` et `val SHADERS_UNAVAILABLE = booleanPreferencesKey("shaders_unavailable")`. Après `sceneReactive = p[Keys.SCENE_REACTIVE] ?: true,` : `canvasAnimated = p[Keys.CANVAS_ANIMATED] ?: true,` et `shadersUnavailable = p[Keys.SHADERS_UNAVAILABLE] ?: false,`. Après `setSceneReactive` : `suspend fun setCanvasAnimated(value: Boolean) = put(Keys.CANVAS_ANIMATED, value)` et `suspend fun setShadersUnavailable(value: Boolean) = put(Keys.SHADERS_UNAVAILABLE, value)`.

`SettingsViewModel.kt`, après `setSceneReactive` :

```kotlin
    fun setCanvasAnimated(value: Boolean) = update { settings.setCanvasAnimated(value) }
    /** Remet le drapeau à faux : les shaders seront réessayés au prochain dessin. */
    fun retryShaders() = update { settings.setShadersUnavailable(false) }
```

`SettingsScreen.kt`, après le `SwitchSetting` « Scène réactive » (`:506`) et avant le `GroupDivider()` qui précède « Retour haptique » :

```kotlin
                            GroupDivider()
                            SwitchSetting(
                                title = "Canvas animé",
                                subtitle = "Une boucle de lumière derrière la pochette, au rythme du titre",
                                icon = Icons.Rounded.Gradient,
                                checked = settings.canvasAnimated,
                                onCheckedChange = settingsViewModel::setCanvasAnimated
                            )
                            Reveal(visible = settings.shadersUnavailable) {
                                Column {
                                    GroupDivider()
                                    ClickableSetting(
                                        title = "Réessayer les shaders",
                                        subtitle = "Cet appareil a refusé les effets avancés ; retente après une mise à jour système",
                                        icon = Icons.Rounded.Refresh,
                                        onClick = settingsViewModel::retryShaders
                                    )
                                }
                            }
```

(`Icons.Rounded.Gradient` et `Icons.Rounded.Refresh` : vérifier les imports existants du fichier, ajouter ceux qui manquent ; si `Gradient` est absent de l'icône set, utiliser `Icons.Rounded.BlurOn` et le dire dans le rapport.)

- [ ] **Step 5 : Vérifier le succès**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.theme.shaders.ShaderModeTest"` puis `.\gradlew.bat :app:compileDebugKotlin`
Attendu : 3 tests verts, compilation OK.

- [ ] **Step 6 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/theme/shaders/ShaderMode.kt app/src/test/java/fr/synxio/player/ui/theme/shaders/ShaderModeTest.kt app/src/main/java/fr/synxio/player/core/prefs/SettingsRepository.kt app/src/main/java/fr/synxio/player/ui/viewmodel/SettingsViewModel.kt app/src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt
git commit -m "shaders: mode de rendu, réglages « Canvas animé » et « Réessayer les shaders »"
```

---

### Task 2 : Sources AGSL, `SceneUniforms`, `ShaderSupport`

**Files:**
- Create: `app/src/main/java/fr/synxio/player/ui/theme/shaders/ShaderSources.kt`
- Create: `app/src/main/java/fr/synxio/player/ui/theme/shaders/SceneUniforms.kt`
- Create: `app/src/main/java/fr/synxio/player/ui/theme/shaders/ShaderSupport.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt:67-84` (injection), `app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt:281-284` (fourniture)
- Test: `app/src/test/java/fr/synxio/player/ui/theme/shaders/ShaderSourcesTest.kt`, `app/src/test/java/fr/synxio/player/ui/theme/shaders/SceneUniformsTest.kt`

**Interfaces:**
- Consumes: `ShaderMode`, `shaderMode`, `Settings.shadersUnavailable`, `SceneParams` (`ui/theme/SceneMapper.kt`).
- Produces: `object ShaderSources { val AURORA: String; val GLASS: String; val CANVAS: String }` ; `class SceneUniforms` (champs publics `centers: FloatArray(6)`, `radii: FloatArray(3)`, `colors: FloatArray(12)`, `base: FloatArray(4)`, `time: Float`, `width: Float`, `height: Float`) ; `fun fillSceneUniforms(out: SceneUniforms, width: Float, height: Float, t: Float, params: SceneParams, colorsRgba: FloatArray, alphas: FloatArray, baseRgba: FloatArray, timeSeconds: Float)` ; `@Singleton class ShaderSupport` avec `val mode: StateFlow<ShaderMode>`, `fun aurora(): Any?`, `fun glass(): Any?`, `fun canvas(): Any?` (renvoient un `RuntimeShader` ou `null`, typés `Any?` pour ne pas référencer la classe hors API 33), `fun markUnavailable()` ; `val LocalShaderSupport: ProvidableCompositionLocal<ShaderSupport?>` ; `val LocalSceneUniforms: ProvidableCompositionLocal<SceneUniforms>`.

- [ ] **Step 1 : Tests**

```kotlin
package fr.synxio.player.ui.theme.shaders

import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderSourcesTest {
    private fun check(src: String, vararg uniforms: String) {
        assertTrue(src.contains("half4 main(float2"))
        uniforms.forEach { assertTrue("uniform $it manquant", src.contains("uniform") && src.contains(it)) }
        assertTrue("accolades déséquilibrées", src.count { it == '{' } == src.count { it == '}' })
    }

    @Test
    fun `aurore - uniformes attendus`() = check(ShaderSources.AURORA, "resolution", "c0", "c1", "c2", "r0", "r1", "r2", "col0", "col1", "col2", "base", "grain", "dispersion", "time")

    @Test
    fun `verre - uniformes attendus`() = check(ShaderSources.GLASS, "origin", "wobble", "dispersion", "time", "c0", "col2", "base")

    @Test
    fun `verre - pas d'uniforme resolution, il n'y est pas lu`() = assertTrue(!ShaderSources.GLASS.contains("resolution"))

    @Test
    fun `canvas - uniformes attendus`() = check(ShaderSources.CANVAS, "resolution", "phase", "contrast", "colA", "colB", "colC")
}
```

```kotlin
package fr.synxio.player.ui.theme.shaders

import fr.synxio.player.ui.theme.SceneParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneUniformsTest {
    private val colors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f)
    private val alphas = floatArrayOf(0.36f, 0.28f, 0.16f)
    private val base = floatArrayOf(0.05f, 0.04f, 0.06f, 1f)

    @Test
    fun `repos - rayons et alphas de base`() {
        val u = SceneUniforms()
        fillSceneUniforms(u, 1080f, 2400f, t = 0f, params = SceneParams.REST, colorsRgba = colors, alphas = alphas, baseRgba = base, timeSeconds = 3f)
        assertEquals(2400f * 0.78f, u.radii[0], 1e-3f)
        assertEquals(2400f * 0.70f, u.radii[1], 1e-3f)
        assertEquals(2400f * 0.55f, u.radii[2], 1e-3f)
        assertEquals(0.36f, u.colors[3], 1e-4f)
        assertEquals(0.28f, u.colors[7], 1e-4f)
        assertEquals(0.16f, u.colors[11], 1e-4f)
        assertEquals(1080f, u.width, 0f)
        assertEquals(3f, u.time, 0f)
        assertEquals(0.05f, u.base[0], 0f)
    }

    @Test
    fun `plein pouls - rayons dilates, alphas bornes`() {
        val u = SceneUniforms()
        val p = SceneParams(floatArrayOf(1.25f, 1.15f, 1.10f), brightness = 1.15f, shimmer = 1f, flash = 1f, frozen = false)
        fillSceneUniforms(u, 1080f, 2400f, 0f, p, colors, floatArrayOf(0.9f, 0.9f, 0.9f), base, 0f)
        assertEquals(2400f * 0.78f * 1.25f, u.radii[0], 1e-2f)
        assertTrue(u.colors[3] <= 1f && u.colors[7] <= 1f && u.colors[11] <= 1f)
        assertTrue(u.colors[3] > 0.9f)
    }

    @Test
    fun `centres - dans l'ecran et continus en t`() {
        val u1 = SceneUniforms(); val u2 = SceneUniforms()
        fillSceneUniforms(u1, 1000f, 2000f, 1.00f, SceneParams.REST, colors, alphas, base, 0f)
        fillSceneUniforms(u2, 1000f, 2000f, 1.01f, SceneParams.REST, colors, alphas, base, 0f)
        for (i in 0 until 3) {
            assertTrue(u1.centers[2 * i] in -200f..1200f && u1.centers[2 * i + 1] in -200f..2200f)
            assertTrue(kotlin.math.abs(u1.centers[2 * i] - u2.centers[2 * i]) < 5f)
        }
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.theme.shaders.*"`
Attendu : échec de compilation.

- [ ] **Step 3 : `ShaderSources.kt`**

```kotlin
package fr.synxio.player.ui.theme.shaders

/**
 * Sources AGSL (Android Graphics Shading Language, API 33+). Les trois shaders partagent
 * le « champ » de l'aurore : trois halos gaussiens aux couleurs de la pochette posés sur
 * la couleur de fond. Constantes Kotlin plutôt que ressources `raw` : testables sur la JVM,
 * et un seul endroit pour le prélude commun.
 */
object ShaderSources {

    /** Uniformes et fonctions partagés par l'aurore et le verre. */
    private val FIELD = """
        uniform float2 c0; uniform float2 c1; uniform float2 c2;
        uniform float r0; uniform float r1; uniform float r2;
        uniform float4 col0; uniform float4 col1; uniform float4 col2;
        uniform float4 base;
        uniform float dispersion;
        uniform float time;

        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float halo(float2 p, float2 c, float r) { float d = distance(p, c) / r; return exp(-d * d * 3.0); }
        float3 field(float2 p) {
            float3 acc = base.rgb;
            acc = mix(acc, col0.rgb, halo(p, c0, r0) * col0.a);
            acc = mix(acc, col1.rgb, halo(p, c1, r1) * col1.a);
            acc = mix(acc, col2.rgb, halo(p, c2, r2) * col2.a);
            return acc;
        }
        float3 dispersed(float2 p) {
            float2 dx = float2(dispersion, 0.0);
            return float3(field(p + dx).r, field(p).g, field(p - dx).b);
        }
    """.trimIndent()

    /** Aurore plein écran : champ dispersé + grain fin. */
    val AURORA: String = FIELD + """

        uniform float2 resolution;
        uniform float grain;

        half4 main(float2 p) {
            float3 c = dispersed(p);
            float n = (hash(p / resolution * 1000.0 + time) - 0.5) * grain;
            return half4(half3(clamp(c + n, 0.0, 1.0)), 1.0);
        }
    """.trimIndent()

    /**
     * Verre réfracté : le champ de l'aurore, échantillonné à la position de la surface dans
     * l'écran (`origin`) et ondulé de `wobble` pixels, dessiné sous le voile du verre.
     */
    val GLASS: String = FIELD + """

        uniform float2 origin;
        uniform float wobble;

        half4 main(float2 p) {
            float2 q = p + origin;
            q += float2(sin(q.y / 40.0 + time * 0.8), cos(q.x / 48.0 + time * 0.6)) * wobble;
            return half4(half3(clamp(dispersed(q), 0.0, 1.0)), 1.0);
        }
    """.trimIndent()

    /** Canvas de pochette : trois nappes sinusoïdales mêlant les couleurs de la palette. */
    val CANVAS: String = """
        uniform float2 resolution;
        uniform float phase;
        uniform float contrast;
        uniform float4 colA; uniform float4 colB; uniform float4 colC;

        half4 main(float2 p) {
            float2 uv = p / resolution;
            float w1 = sin(uv.x * 3.0 + phase) * 0.5 + 0.5;
            float w2 = sin(uv.y * 2.5 - phase * 0.7 + w1 * 1.5) * 0.5 + 0.5;
            float w3 = sin((uv.x + uv.y) * 2.0 + phase * 0.5 + w2) * 0.5 + 0.5;
            float3 c = mix(colA.rgb, colB.rgb, w1);
            c = mix(c, colC.rgb, w2 * 0.6);
            c = mix(float3(0.5), c, contrast) * (0.85 + 0.3 * w3);
            return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
        }
    """.trimIndent()
}
```

- [ ] **Step 4 : `SceneUniforms.kt`**

```kotlin
package fr.synxio.player.ui.theme.shaders

import fr.synxio.player.ui.theme.SceneParams
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Uniformes de l'aurore, partagés par les trois shaders : trois centres (px), trois rayons
 * (px), trois couleurs RGBA (alpha = intensité du halo), couleur de fond, temps. Rempli à
 * chaque image par `AmbientBackdrop` dans la phase de dessin ; lu par le verre et le canvas
 * dans la leur. Objet mutable sans état Compose : pas de recomposition.
 */
class SceneUniforms {
    val centers = FloatArray(6)
    val radii = FloatArray(3)
    val colors = FloatArray(12)
    val base = FloatArray(4)
    var width = 0f
    var height = 0f
    var time = 0f
}

/**
 * Même géométrie que le chemin Canvas : centres qui dérivent avec `t` (radians), rayons
 * × `haloScale`, alphas × `brightness / 0.85`, le troisième halo scintille.
 * `colorsRgba` : 12 flottants (3 × RGBA, alpha ignoré) ; `alphas` : 3 intensités de base.
 */
fun fillSceneUniforms(
    out: SceneUniforms,
    width: Float,
    height: Float,
    t: Float,
    params: SceneParams,
    colorsRgba: FloatArray,
    alphas: FloatArray,
    baseRgba: FloatArray,
    timeSeconds: Float,
    shimmer: Float = 1f,
) {
    out.width = width
    out.height = height
    out.time = timeSeconds
    out.centers[0] = width * (0.18f + 0.10f * cos(t)); out.centers[1] = height * (0.04f + 0.04f * sin(t))
    out.centers[2] = width * (0.98f - 0.10f * sin(t)); out.centers[3] = height * (0.58f + 0.06f * cos(t))
    out.centers[4] = width * (0.30f + 0.12f * sin(t + 2f)); out.centers[5] = height * (1.02f - 0.04f * cos(t))
    val max = maxOf(width, height)
    out.radii[0] = max * 0.78f * params.haloScale[0]
    out.radii[1] = max * 0.70f * params.haloScale[1]
    out.radii[2] = max * 0.55f * params.haloScale[2]
    val k2 = params.brightness / 0.85f
    for (i in 0 until 3) {
        out.colors[4 * i] = colorsRgba[4 * i]
        out.colors[4 * i + 1] = colorsRgba[4 * i + 1]
        out.colors[4 * i + 2] = colorsRgba[4 * i + 2]
        val a = alphas[i] * k2 * (if (i == 2) shimmer else 1f)
        out.colors[4 * i + 3] = a.coerceIn(0f, 1f)
    }
    for (i in 0 until 4) out.base[i] = baseRgba[i]
}

/** Deux pi, pour convertir des tours en radians sans réimporter PI partout. */
const val TWO_PI = (2.0 * PI).toFloat()
```

- [ ] **Step 5 : `ShaderSupport.kt`**

```kotlin
package fr.synxio.player.ui.theme.shaders

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.compositionLocalOf
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
```

(`compositionLocalOf` importé seulement s'il sert ; sinon le retirer.)

- [ ] **Step 6 : Fourniture**

`AppViewModel.kt` : ajouter `val shaderSupport: fr.synxio.player.ui.theme.shaders.ShaderSupport,` aux paramètres du constructeur (paramètre public, sans `private`, pour que `SynxioRoot` le lise). `SynxioRoot.kt`, dans le `CompositionLocalProvider` de `:281-284` :

```kotlin
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalScenePulse provides scenePulse,
            LocalShaderSupport provides viewModel.shaderSupport,
            LocalSceneUniforms provides sceneUniforms,
        ) {
```

avec, avant le `Box(Modifier.fillMaxSize())`, `val sceneUniforms = remember { SceneUniforms() }`, et `AmbientBackdrop(drifting = isPlaying, pulse = scenePulse, uniforms = sceneUniforms, shaders = viewModel.shaderSupport)` — ces deux paramètres sont ajoutés en tâche 3 ; dans cette tâche, ne changer que le provider et le `remember` (l'appel d'`AmbientBackdrop` reste tel quel).

- [ ] **Step 7 : Vérifier**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.theme.shaders.*"` puis `.\gradlew.bat :app:compileDebugKotlin`
Attendu : 10 tests verts (3 `ShaderModeTest` + 4 `ShaderSourcesTest` + 3 `SceneUniformsTest`), compilation et graphe Hilt OK.

- [ ] **Step 8 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/theme/shaders app/src/test/java/fr/synxio/player/ui/theme/shaders app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt
git commit -m "shaders: sources AGSL, uniformes de scène, compilation avec drapeau de repli"
```

---

### Task 3 : Aurore AGSL dans `AmbientBackdrop`

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/ui/theme/Glass.kt` (`AmbientBackdrop`, lignes ~135-253)
- Modify: `app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt:280`

**Interfaces:**
- Consumes: `ShaderSupport.aurora()`, `ShaderSupport.mode`, `SceneUniforms`, `fillSceneUniforms`, `TWO_PI`, `ShaderSources` (uniformes nommés).
- Produces: `AmbientBackdrop(modifier, intensity, drifting, pulse, uniforms: SceneUniforms? = null, shaders: ShaderSupport? = null)`.

- [ ] **Step 1 : Signature et état**

```kotlin
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    drifting: Boolean = false,
    pulse: State<AudioPulse>? = null,
    uniforms: SceneUniforms? = null,
    shaders: ShaderSupport? = null,
) {
```

Après `val alphaC = ...`, ajouter :

```kotlin
    // Chemin de rendu : AGSL si le support le dit (API 33+, pas de drapeau), sinon Canvas.
    // Le shader est gardé à part : `ShaderBrush` n'expose pas le sien.
    val mode by (shaders?.mode ?: remember { MutableStateFlow(ShaderMode.CANVAS) }).collectAsStateWithLifecycle()
    val auroraShader = remember(mode, shaders) { if (mode == ShaderMode.AGSL) shaders?.aurora() else null }
    val auroraBrush = remember(auroraShader) { (auroraShader as? android.graphics.Shader)?.let { ShaderBrush(it) } }
    val local = uniforms ?: remember { SceneUniforms() }
    // Couleurs sous forme de tableaux pour `fillSceneUniforms`, recalculés quand la palette change.
    val colorsRgba = remember(glowA, glowB, glowC) {
        floatArrayOf(glowA.red, glowA.green, glowA.blue, 1f, glowB.red, glowB.green, glowB.blue, 1f, glowC.red, glowC.green, glowC.blue, 1f)
    }
    val alphas = remember(alphaA, alphaB, alphaC) { floatArrayOf(alphaA, alphaB, alphaC) }
    val baseRgba = remember(base) { floatArrayOf(base.red, base.green, base.blue, 1f) }
    val startNanos = remember { System.nanoTime() }
```

`glowA/B/C` sont des `State` lus en composition (déjà le cas) : la palette change rarement.

- [ ] **Step 2 : Dessin**

Dans `drawBehind`, après le calcul de `t`, `now`, `shimmer` et `k2` (garder tout ce qui précède `drawRect(base)`), remplacer les quatre `drawRect` par :

```kotlin
                val timeSeconds = ((now - startNanos) / 1_000_000L % 1_000_000L) / 1_000f
                fillSceneUniforms(local, size.width, size.height, t, p, colorsRgba, alphas, baseRgba, timeSeconds, shimmer)
                if (auroraBrush != null && auroraShader != null && Build.VERSION.SDK_INT >= AGSL_MIN_SDK) {
                    AgslUniforms.applyAurora(auroraShader as android.graphics.RuntimeShader, local)
                    drawRect(auroraBrush)
                } else {
                    drawRect(base)
                    for (i in 0 until 3) {
                        val c = Color(local.colors[4 * i], local.colors[4 * i + 1], local.colors[4 * i + 2], local.colors[4 * i + 3])
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(c, Color.Transparent),
                                center = Offset(local.centers[2 * i], local.centers[2 * i + 1]),
                                radius = local.radii[i],
                            )
                        )
                    }
                }
                if (p.flash > 0.01f) drawRect(Color.White.copy(alpha = 0.06f * p.flash))
```

Ajouter en bas du fichier (objet partagé avec le verre de la tâche 4, `internal` pour rester dans le module) :

```kotlin
/**
 * Pose les uniformes sur les shaders. Isolé pour ne référencer `RuntimeShader` qu'en
 * API 33+. `applyField` : le champ commun (aurore et verre) ; `applyAurora` ajoute ce que
 * seule l'aurore déclare. Un `setFloatUniform` sur un nom absent du shader lève une
 * exception : chaque fonction ne pose que les uniformes déclarés par sa source.
 */
@RequiresApi(AGSL_MIN_SDK)
internal object AgslUniforms {
    fun applyAurora(shader: android.graphics.RuntimeShader, u: SceneUniforms) {
        applyField(shader, u)
        shader.setFloatUniform("resolution", u.width, u.height)
        shader.setFloatUniform("grain", 0.015f)
    }

    fun applyField(shader: android.graphics.RuntimeShader, u: SceneUniforms) {
        shader.setFloatUniform("c0", u.centers[0], u.centers[1])
        shader.setFloatUniform("c1", u.centers[2], u.centers[3])
        shader.setFloatUniform("c2", u.centers[4], u.centers[5])
        shader.setFloatUniform("r0", u.radii[0])
        shader.setFloatUniform("r1", u.radii[1])
        shader.setFloatUniform("r2", u.radii[2])
        shader.setFloatUniform("col0", u.colors[0], u.colors[1], u.colors[2], u.colors[3])
        shader.setFloatUniform("col1", u.colors[4], u.colors[5], u.colors[6], u.colors[7])
        shader.setFloatUniform("col2", u.colors[8], u.colors[9], u.colors[10], u.colors[11])
        shader.setFloatUniform("base", u.base[0], u.base[1], u.base[2], u.base[3])
        shader.setFloatUniform("dispersion", 1f)
        shader.setFloatUniform("time", u.time)
    }
}
```

Le chemin Canvas lit désormais les mêmes uniformes que le shader : une seule géométrie. Le rendu Canvas est identique à l'ancien (mêmes centres, rayons, alphas ; `shimmer` était déjà appliqué à l'alpha du troisième halo).

Imports : `android.os.Build`, `androidx.annotation.RequiresApi`, `androidx.compose.ui.graphics.ShaderBrush`, `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `kotlinx.coroutines.flow.MutableStateFlow`, `fr.synxio.player.ui.theme.shaders.*`.

- [ ] **Step 3 : `SynxioRoot`**

`:280` devient `AmbientBackdrop(drifting = isPlaying, pulse = scenePulse, uniforms = sceneUniforms, shaders = viewModel.shaderSupport)`.

- [ ] **Step 4 : Compiler, tests**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin` puis `.\gradlew.bat :app:testDebugUnitTest`
Attendu : OK. Rendu AGSL non vérifiable ici : à noter dans le rapport.

- [ ] **Step 5 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/theme/Glass.kt app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt
git commit -m "aurore: rendu AGSL sur API 33+, géométrie partagée avec le chemin Canvas"
```

---

### Task 4 : Verre réfracté

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/ui/theme/Glass.kt` (`Modifier.glass`, lignes ~66-96)
- Modify: `app/src/main/java/fr/synxio/player/ui/components/MiniPlayer.kt:152`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt:883`

**Interfaces:**
- Consumes: `LocalShaderSupport`, `LocalSceneUniforms`, `LocalScenePulse`, `ShaderSupport.glass()`.
- Produces: `Modifier.glass(shape, strength, border, tint, refract: Boolean = false)`.

- [ ] **Step 1 : `glass(refract)`**

Signature : ajouter `refract: Boolean = false` en dernier paramètre. Dans le `composed`, après `val edge = ...` :

```kotlin
    // Verre réfracté : le champ de l'aurore, déformé, dessiné sous le voile. Compose ne
    // sait pas échantillonner le fond ; l'aurore étant procédurale, on la redessine depuis
    // les mêmes uniformes. Invalidé par les ticks du pouls ; statique hors lecture.
    val support = LocalShaderSupport.current
    val uniforms = LocalSceneUniforms.current
    val pulse = LocalScenePulse.current
    val refractShader = remember(refract, support) { if (refract) support?.glass() else null }
    val refractBrush = remember(refractShader) { (refractShader as? android.graphics.Shader)?.let { ShaderBrush(it) } }
    val origin = remember { FloatArray(2) }
    val refractModifier = if (refractBrush != null && refractShader != null && Build.VERSION.SDK_INT >= AGSL_MIN_SDK) {
        Modifier
            .onGloballyPositioned { val p = it.positionInRoot(); origin[0] = p.x; origin[1] = p.y }
            .drawBehind {
                pulse?.value // abonnement du dessin aux ticks : le verre suit l'aurore en lecture
                GlassAgsl.apply(refractShader as android.graphics.RuntimeShader, uniforms, origin)
                drawRect(refractBrush, alpha = if (dark) 0.9f else 0.6f)
            }
    } else Modifier
    this
        .clip(shape)
        .then(refractModifier)
        .background(fill, shape)
```

(le `.clip(shape)` n'est ajouté que si `refract` est vrai : `.then(if (refract) Modifier.clip(shape) else Modifier)`, pour que le verre sans `refract` reste identique). Le reste de la chaîne (`drawBehind` du reflet, `border`) est inchangé.

En bas du fichier :

```kotlin
@RequiresApi(AGSL_MIN_SDK)
private object GlassAgsl {
    fun apply(shader: android.graphics.RuntimeShader, u: SceneUniforms, origin: FloatArray) {
        AgslUniforms.applyField(shader, u)
        shader.setFloatUniform("origin", origin[0], origin[1])
        shader.setFloatUniform("wobble", 2f)
    }
}
```

`AgslUniforms` vient de la tâche 3. Le shader du verre ne déclare ni `grain` ni `resolution` : ne pas les poser (un `setFloatUniform` sur un nom inconnu lève une exception).

- [ ] **Step 2 : Deux surfaces**

`MiniPlayer.kt:152` : `.glass(SynxioShape.card, strength = 1f, tint = tint, refract = true)`.
`NowPlayingScreen.kt:883` (cadre du skin Carte) : `.glass(RoundedCornerShape(Radius.xl), strength = 0.9f, refract = true)`.

- [ ] **Step 3 : Compiler, tests**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin` puis `.\gradlew.bat :app:testDebugUnitTest`
Attendu : OK. Rendu à vérifier sur l'appareil (noter dans le rapport).

- [ ] **Step 4 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/theme/Glass.kt app/src/main/java/fr/synxio/player/ui/components/MiniPlayer.kt app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt
git commit -m "verre: réfraction AGSL de l'aurore sous le mini-lecteur et le cadre Carte"
```

---

### Task 5 : `ArtworkCanvas`

**Files:**
- Create: `app/src/main/java/fr/synxio/player/ui/components/ArtworkCanvas.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt` (`ArtworkStage`, lignes ~826-940)
- Test: `app/src/test/java/fr/synxio/player/ui/components/CanvasPhaseTest.kt`

**Interfaces:**
- Consumes: `LocalShaderSupport`, `LocalScenePulse`, `LocalArtworkColors`, `LocalReduceMotion`, `Settings.canvasAnimated` (via `AppViewModel.settings`, déjà collecté dans `NowPlayingScreen` sous le nom `settings`), `ShaderSupport.canvas()`, `TWO_PI`.
- Produces: `@Composable fun ArtworkCanvas(animated: Boolean, modifier: Modifier = Modifier, shape: Shape)` ; `class CanvasPhase` pure avec `fun advance(nowNanos: Long, speed: Float): Float`.

- [ ] **Step 1 : Test de la phase**

```kotlin
package fr.synxio.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPhaseTest {
    @Test
    fun `huit secondes a vitesse 1 - un tour`() {
        val phase = CanvasPhase()
        phase.advance(0L, speed = 1f)
        val v = phase.advance(8_000_000_000L, speed = 1f)
        assertEquals(CANVAS_TWO_PI, v, 1e-3f)
    }

    @Test
    fun `vitesse 2 - deux fois plus vite, sans saut au changement`() {
        val phase = CanvasPhase()
        phase.advance(0L, 1f)
        val a = phase.advance(1_000_000_000L, 1f)
        val b = phase.advance(1_000_000_000L, 2f)
        assertEquals(a, b, 1e-6f)
        val c = phase.advance(2_000_000_000L, 2f)
        assertEquals(a + CANVAS_TWO_PI / 8f * 2f, c, 1e-3f)
    }

    @Test
    fun `reste borne`() {
        val phase = CanvasPhase()
        phase.advance(0L, 1f)
        val v = phase.advance(100_000_000_000_000L, 3f)
        assertTrue(v >= 0f && v < CANVAS_TWO_PI)
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.components.CanvasPhaseTest"`
Attendu : échec de compilation.

- [ ] **Step 3 : `ArtworkCanvas.kt`**

```kotlin
package fr.synxio.player.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.theme.LocalScenePulse
import fr.synxio.player.ui.theme.shaders.AGSL_MIN_SDK
import fr.synxio.player.ui.theme.shaders.LocalShaderSupport
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

const val CANVAS_TWO_PI = (2.0 * PI).toFloat()

/** Phase du canvas : un tour toutes les 8 s à vitesse 1, accumulée pour qu'un changement de vitesse ne saute pas. */
class CanvasPhase {
    private var lastNanos = Long.MIN_VALUE
    private var phase = 0f

    fun advance(nowNanos: Long, speed: Float): Float {
        if (lastNanos != Long.MIN_VALUE) {
            val dt = (nowNanos - lastNanos) / 1_000_000_000f
            phase = (phase + dt * speed * CANVAS_TWO_PI / LOOP_SECONDS) % CANVAS_TWO_PI
            if (phase < 0f) phase += CANVAS_TWO_PI
        }
        lastNanos = nowNanos
        return phase
    }

    private companion object { const val LOOP_SECONDS = 8f }
}

/**
 * Canvas génératif derrière la pochette : trois nappes qui ondulent aux couleurs de la
 * palette, vitesse `0.5 + rms`, contraste `0.6 + 0.4 × bass`. Rien n'est précalculé ni
 * stocké. AGSL sur API 33+, repli en dégradés animés sinon. Figé quand [animated] est faux.
 */
@Composable
fun ArtworkCanvas(animated: Boolean, modifier: Modifier = Modifier, shape: Shape) {
    val art = LocalArtworkColors.current
    val pulse = LocalScenePulse.current
    val support = LocalShaderSupport.current
    val colA = art.vibrant ?: art.seed ?: Color(0xFFC56BFF)
    val colB = art.darkMuted ?: art.dominant ?: Color(0xFF3A2A5A)
    val colC = art.dominant ?: colA
    val shader = remember(support) { support?.canvas() }
    val brush = remember(shader) { (shader as? Shader)?.let { ShaderBrush(it) } }
    val phase = remember { CanvasPhase() }
    // Horloge : une image par trame tant que le canvas est animé ; lue dans le dessin.
    val frame = remember { mutableIntStateOf(0) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        while (isActive) withFrameNanos { frame.intValue++ }
    }
    Canvas(modifier.clip(shape)) {
        frame.intValue
        val p = pulse?.value
        val speed = 0.5f + (p?.rms ?: 0f)
        val contrast = 0.6f + 0.4f * (p?.bass ?: 0f)
        val ph = if (animated) phase.advance(System.nanoTime(), speed) else phase.advance(System.nanoTime(), 0f)
        if (brush != null && shader != null && Build.VERSION.SDK_INT >= AGSL_MIN_SDK) {
            CanvasAgsl.apply(shader as android.graphics.RuntimeShader, size.width, size.height, ph, contrast, colA, colB, colC)
            drawRect(brush)
        } else {
            drawRect(colB)
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    listOf(colA.copy(alpha = 0.9f), Color.Transparent),
                    start = Offset(w * (0.5f + 0.5f * cos(ph)), 0f),
                    end = Offset(w * (0.5f - 0.5f * cos(ph)), h),
                )
            )
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, colC.copy(alpha = 0.7f * contrast)),
                    start = Offset(0f, h * (0.5f + 0.5f * sin(ph * 0.7f))),
                    end = Offset(w, h * (0.5f - 0.5f * sin(ph * 0.7f))),
                )
            )
        }
    }
}

@RequiresApi(AGSL_MIN_SDK)
private object CanvasAgsl {
    fun apply(s: android.graphics.RuntimeShader, w: Float, h: Float, phase: Float, contrast: Float, a: Color, b: Color, c: Color) {
        s.setFloatUniform("resolution", w, h)
        s.setFloatUniform("phase", phase)
        s.setFloatUniform("contrast", contrast)
        s.setFloatUniform("colA", a.red, a.green, a.blue, 1f)
        s.setFloatUniform("colB", b.red, b.green, b.blue, 1f)
        s.setFloatUniform("colC", c.red, c.green, c.blue, 1f)
    }
}
```

Hors animation (`animated` faux), `advance(now, 0f)` fige la phase : une image statique aux couleurs de la pochette, qui reste cohérente avec la palette.

- [ ] **Step 4 : Intégration dans `ArtworkStage`**

`ArtworkStage` gagne un paramètre `canvasAnimated: Boolean` (passé par l'appelant depuis `settings.canvasAnimated`). En tête du composable : `val canvasOn = canvasAnimated && LocalScenePulse.current != null && !reduce` (coupures de la scène + réglage + animations réduites).

Skin `IMMERSIVE` : remplacer l'appel `Artwork(...)` par

```kotlin
                NowPlayingSkin.IMMERSIVE -> Box(
                    modifier = stageModifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .glowFor(SynxioShape.artworkLg),
                ) {
                    if (isCurrent) {
                        ArtworkCanvas(
                            animated = canvasOn,
                            shape = SynxioShape.artworkLg,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.85f },
                        )
                    }
                    Artwork(
                        model = song.artworkUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isCurrent) Modifier.sharedPlayerElement(SharedKeys.playerArt(song.id), SynxioShape.artworkLg) else Modifier),
                        shape = SynxioShape.artworkLg,
                    )
                }
```

Skin `CARD` : à l'intérieur du `Box` verre, avant `Artwork(...)` :

```kotlin
                    if (isCurrent) {
                        ArtworkCanvas(
                            animated = canvasOn,
                            shape = RoundedCornerShape(Radius.xl),
                            modifier = Modifier.matchParentSize().graphicsLayer { alpha = 0.55f },
                        )
                    }
```

(le `padding(Space.lg)` du cadre est appliqué avant les enfants : `matchParentSize` couvre la zone intérieure ; si l'on veut le canvas jusqu'aux bords du cadre, déplacer `.padding(Space.lg)` sur `Artwork` et retirer celui du `Box` : faire cela, c'est plus beau, et le dire dans le rapport). `VINYL` et `MINIMAL` : inchangés.

Appel de `ArtworkStage` : ajouter `canvasAnimated = settings.canvasAnimated`.

- [ ] **Step 5 : Vérifier**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.ui.components.CanvasPhaseTest"`, puis `.\gradlew.bat :app:compileDebugKotlin`, puis la suite complète.
Attendu : 3 tests verts, compilation OK, suite verte. Rendu à vérifier sur l'appareil.

- [ ] **Step 6 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui/components/ArtworkCanvas.kt app/src/test/java/fr/synxio/player/ui/components/CanvasPhaseTest.kt app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt
git commit -m "canvas: boucle générative derrière la pochette, AGSL et repli dégradés"
```

---

### Task 6 : Notes, graphe, vérification finale

**Files:**
- Modify: `docs/release-notes/v5.8.0.md`

- [ ] **Step 1 : Notes**

Sous « Nouveautés », après « Plus de micro » :

```markdown
- **Le verre a une épaisseur** : sur Android 13 et plus, l'aurore est rendue par un shader
  (halos plus doux, grain fin, légère dispersion), et le mini-lecteur comme le cadre du
  style Carte laissent voir l'aurore réfractée sous leur voile. En dessous, le rendu
  classique reste.
- **Chaque pochette a un canvas** : dans les styles Immersif et Carte, une boucle de
  lumière aux couleurs de la pochette ondule derrière elle, plus vite et plus contrastée
  quand le titre monte. Réglage « Canvas animé » dans Mouvement et lumière.
- Si ton appareil refuse ces effets, Synxio repasse au rendu classique et propose
  « Réessayer les shaders » dans les réglages.
```

Dans la checklist, ajouter :

```markdown
- [ ] Android 13+ : aurore AGSL (grain visible de près), réfraction sous le mini-lecteur et le cadre Carte, canvas en Immersif et Carte ; rien en Vinyle ni Minimal.
- [ ] « Canvas animé » off : image figée aux couleurs de la pochette. Pause : canvas figé, verre statique.
- [ ] Drapeau forcé (`shaders_unavailable` à vrai via un build de test) : tout repasse en Canvas, bouton « Réessayer les shaders » visible, qui le remet.
```

- [ ] **Step 2 : Suite et graphe**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest` ; Git Bash : `graphify update .`
Attendu : tout vert.

- [ ] **Step 3 : Commit**

```bash
git add docs/release-notes/v5.8.0.md
git commit -m "docs: notes 5.8.0, verre et canvas"
```

---

## Auto-revue

- **Couverture du spec.** §3.1 principe (uniformes partagés) → Task 2 (`SceneUniforms`), Task 3 (remplissage), Task 4 (lecture par le verre). §3.2 aurore AGSL, repli Canvas, même `SceneMapper` → Task 3. §3.3 `glass(refract)`, deux surfaces → Task 4 (mini-lecteur, cadre Carte ; le spec disait « feuille du lecteur » : le lecteur n'a pas de surface de verre pleine, le cadre Carte est la surface de verre du lecteur, écart assumé). §3.4 canvas, skins, pouls en direct, repli, réglage → Task 5 + Task 1. §3.5 compilation dans un `try`, drapeau persistant, bouton de réessai, cadence → Task 2 (`ShaderSupport`), Task 1 (réglage, bouton) ; cadence : les shaders se redessinent sur tick du pouls (lecture de `pulse.value` dans le dessin) et sur la boucle du canvas (`withFrameNanos`, seulement si animé). §3.6 tests JVM (`SceneUniformsTest`, `ShaderModeTest`, `ShaderSourcesTest`, `CanvasPhaseTest`), checklist → Task 6.
- **Écarts assumés.** Sources AGSL en constantes Kotlin plutôt qu'en `res/raw` ; « feuille du lecteur » = cadre du skin Carte ; le canvas ne lit pas de valeur précalculée (déjà le choix du spec).
- **Placeholders.** Aucun. Les formes « qui fait foi » des tâches 3 et 4 lèvent les deux ambiguïtés (`ShaderBrush` n'expose pas son shader ; `grain` absent du shader de verre).
- **Cohérence des types.** `ShaderSupport.aurora()/glass()/canvas(): Any?` partout, transtypés en `android.graphics.Shader` pour `ShaderBrush` et en `RuntimeShader` dans les objets `@RequiresApi` ; `SceneUniforms` et `fillSceneUniforms` même signature en Task 2 (test) et Task 3 ; `LocalSceneUniforms` fourni en Task 2, lu en Task 4 ; `CANVAS_TWO_PI` dans `ArtworkCanvas.kt`, `TWO_PI` dans `SceneUniforms.kt` (deux constantes, deux paquets, pas de collision).
