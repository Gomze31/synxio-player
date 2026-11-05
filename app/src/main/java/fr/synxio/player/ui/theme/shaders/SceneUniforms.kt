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
