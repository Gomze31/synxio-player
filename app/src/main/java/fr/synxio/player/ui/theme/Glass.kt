package fr.synxio.player.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.PlaceholderPalette
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Verre et lumière : le langage visuel de Synxio « Lumen ».
 *
 * Trois ingrédients, toujours les mêmes :
 *  - une aurore en fond : la base du thème éclairée par trois halos aux couleurs de la
 *    pochette en cours, qui dérivent très lentement pendant l'écoute et s'immobilisent
 *    en pause ;
 *  - des surfaces de verre translucides (mini-lecteur, navigation, barres, panneaux)
 *    avec un reflet spéculaire en haut à gauche et un liseré de lumière ;
 *  - des pochettes de remplacement générées, jamais un carré gris.
 *
 * Pas de flou en temps réel derrière les surfaces : il coûterait de la batterie à chaque
 * image et brouillerait le texte. Le verre se lit grâce à l'aurore qu'il laisse voir. Le
 * seul flou est celui de la pochette du lecteur, calculé une fois sur une image de 64 px.
 */

/** Vrai quand le thème courant est sombre (fond peu lumineux). */
val isDarkSurface: Boolean
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background.luminance() < 0.4f

/**
 * Surface de verre : voile translucide teinté par le thème, reflet spéculaire dans le
 * coin haut-gauche, liseré lumineux qui s'estompe vers le bas.
 *
 * @param strength opacité (0 = presque invisible, 1 = dense)
 * @param tint couleur infusée dans le verre (pochette en cours pour le mini-lecteur)
 */
fun Modifier.glass(
    shape: Shape,
    strength: Float = 0.6f,
    border: Boolean = true,
    tint: Color? = null,
): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkSurface
    val raw = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    val base = if (tint != null) lerpColor(raw, tint, if (dark) 0.22f else 0.10f) else raw
    val k = 0.6f + 0.4f * strength
    val top = base.copy(alpha = (if (dark) 0.62f else 0.78f) * k)
    val bottom = base.copy(alpha = (if (dark) 0.46f else 0.66f) * k)
    val fill = Brush.verticalGradient(listOf(top, bottom))
    val specular = Color.White.copy(alpha = if (dark) 0.07f else 0.45f)
    val edge = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))
        else listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.07f))
    )
    this
        .background(fill, shape)
        .drawBehind {
            // Reflet : une tache de lumière douce, comme une fenêtre sur une vitre.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(specular, Color.Transparent),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.maxDimension * 0.55f,
                )
            )
        }
        .then(if (border) Modifier.border(1.dp, edge, shape) else Modifier)
}

/**
 * Lueur colorée sous un élément (pochette, bouton lecture) : une ombre portée teintée,
 * comme si l'objet éclairait la scène. Désactivée avec les effets lumineux.
 *
 * Les ombres colorées existent depuis Android 9 ; avant, l'ombre reste neutre.
 */
fun Modifier.glowShadow(
    shape: Shape,
    color: Color,
    elevation: Dp = 28.dp,
): Modifier = composed {
    if (!LocalGlowEffects.current) {
        this.shadow(elevation / 3, shape, clip = false)
    } else {
        this.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = color.copy(alpha = 0.55f),
            spotColor = color,
        )
    }
}

/**
 * Fond de l'application : l'aurore Lumen.
 *
 * Trois halos aux couleurs de la pochette en cours (ou de l'accent sans pochette) sur
 * la base du thème. Les couleurs glissent en douceur d'un morceau à l'autre.
 *
 * [drifting] fait orbiter lentement les halos (un tour toutes les 40 s) : vrai pendant
 * la lecture. La phase est lue uniquement au dessin, donc aucune recomposition ; et
 * l'horloge d'images s'arrête d'elle-même quand l'app n'est plus visible.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    drifting: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val art = LocalArtworkColors.current
    val dark = isDarkSurface
    val reduce = LocalReduceMotion.current
    val glow = LocalGlowEffects.current
    val spec = tween<Color>(if (reduce) 0 else Motion.PALETTE, easing = Motion.Standard)

    val glowA by animateColorAsState(art.vibrant ?: art.seed ?: scheme.primary, spec, label = "glowA")
    val glowB by animateColorAsState(art.darkMuted ?: art.dominant ?: scheme.tertiary, spec, label = "glowB")
    val glowC by animateColorAsState(art.dominant ?: SynxioColors().favorite, spec, label = "glowC")
    val base = scheme.background
    val k = intensity * if (glow) 1f else 0.55f
    val alphaA = (if (dark) 0.36f else 0.22f) * k
    val alphaB = (if (dark) 0.28f else 0.15f) * k
    val alphaC = (if (dark) 0.16f else 0.10f) * k

    val phase = remember { Animatable(0f) }
    val animate = drifting && glow && !reduce
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (isActive) {
            phase.animateTo(phase.value + 1f, tween(DRIFT_PERIOD_MS, easing = LinearEasing))
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val t = (phase.value % 1f) * 2f * PI.toFloat()
                val w = size.width
                val h = size.height
                drawRect(base)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowA.copy(alpha = alphaA), Color.Transparent),
                        center = Offset(w * (0.18f + 0.10f * cos(t)), h * (0.04f + 0.04f * sin(t))),
                        radius = size.maxDimension * 0.78f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowB.copy(alpha = alphaB), Color.Transparent),
                        center = Offset(w * (0.98f - 0.10f * sin(t)), h * (0.58f + 0.06f * cos(t))),
                        radius = size.maxDimension * 0.70f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowC.copy(alpha = alphaC), Color.Transparent),
                        center = Offset(w * (0.30f + 0.12f * sin(t + 2f)), h * (1.02f - 0.04f * cos(t))),
                        radius = size.maxDimension * 0.55f,
                    )
                )
            }
    )
}

/** Un tour complet de l'aurore : assez lent pour ne jamais attirer l'œil. */
private const val DRIFT_PERIOD_MS = 40_000

/**
 * Barres du haut transparentes sur le fond ambiant, qui deviennent verre (voile dense)
 * dès que le contenu défile dessous.
 */
@Composable
fun synxioTopBarColors(): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = scheme.surfaceContainer.copy(alpha = if (isDarkSurface) 0.90f else 0.94f),
    )
}

/**
 * Voile de lisibilité en bas d'une image (texte posé sur une pochette) : le haut de
 * l'image reste intact, le bas s'assombrit progressivement.
 */
fun Modifier.bottomScrim(color: Color = Color.Black, strength: Float = 0.7f): Modifier =
    drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0.35f to Color.Transparent,
                1f to color.copy(alpha = strength),
            )
        )
    }

/**
 * Dégradé de remplacement pour une pochette absente, stable pour un même modèle :
 * un album sans image garde toujours les mêmes couleurs, et deux albums voisins se
 * distinguent au premier coup d'œil.
 */
fun placeholderBrush(seed: Any?, dark: Boolean): Brush {
    val (start, end) = PlaceholderPalette.pairFor(seed)
    val alpha = if (dark) 1f else 0.85f
    return Brush.linearGradient(listOf(Color(start).copy(alpha = alpha), Color(end).copy(alpha = alpha)))
}

/** Ombre portée douce sous les éléments flottants. */
val FloatingElevation: Dp = 10.dp
