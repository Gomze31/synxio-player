package fr.synxio.player.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.PlaceholderPalette

/*
 * Verre et lumière : le langage visuel de Synxio.
 *
 * Trois ingrédients, toujours les mêmes :
 *  - un fond ambiant, sombre et profond, éclairé par la couleur de la pochette en cours ;
 *  - des surfaces de verre translucides (mini-lecteur, navigation, barres, panneaux)
 *    avec un liseré de lumière en haut, comme un reflet sur une vitre ;
 *  - des pochettes de remplacement générées, jamais un carré gris.
 *
 * Pas de flou en temps réel derrière les surfaces : il coûterait de la batterie à chaque
 * image et brouillerait le texte. Le verre se lit grâce au fond ambiant qu'il laisse voir.
 */

/** Vrai quand le thème courant est sombre (fond peu lumineux). */
val isDarkSurface: Boolean
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background.luminance() < 0.4f

/**
 * Surface de verre : voile translucide teinté par le thème, liseré lumineux en haut qui
 * s'estompe vers le bas. [strength] règle l'opacité (0 = presque invisible, 1 = dense).
 */
fun Modifier.glass(shape: Shape, strength: Float = 0.6f, border: Boolean = true): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkSurface
    val base = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    val top = base.copy(alpha = (if (dark) 0.55f else 0.72f) * (0.6f + 0.4f * strength))
    val bottom = base.copy(alpha = (if (dark) 0.42f else 0.62f) * (0.6f + 0.4f * strength))
    val fill = Brush.verticalGradient(listOf(top, bottom))
    val edge = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))
        else listOf(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.06f))
    )
    this
        .background(fill, shape)
        .then(if (border) Modifier.border(1.dp, edge, shape) else Modifier)
}

/**
 * Fond de l'application : base du thème éclairée par deux halos aux couleurs de la
 * pochette en cours (ou de l'accent Synxio sans pochette). Les couleurs glissent en
 * douceur d'un morceau à l'autre ; rien ne bouge en continu, pour la batterie.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val scheme = MaterialTheme.colorScheme
    val art = LocalArtworkColors.current
    val dark = isDarkSurface
    val reduce = LocalReduceMotion.current
    val spec = tween<Color>(if (reduce) 0 else Motion.LONG * 2, easing = Motion.Standard)

    val glowA by animateColorAsState(art.vibrant ?: art.seed ?: scheme.primary, spec, label = "glowA")
    val glowB by animateColorAsState(art.darkMuted ?: art.dominant ?: scheme.tertiary, spec, label = "glowB")
    val base = scheme.background
    val alphaA = (if (dark) 0.34f else 0.20f) * intensity
    val alphaB = (if (dark) 0.26f else 0.14f) * intensity

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowA.copy(alpha = alphaA), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.02f),
                        radius = size.maxDimension * 0.75f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowB.copy(alpha = alphaB), Color.Transparent),
                        center = Offset(size.width * 1.0f, size.height * 0.62f),
                        radius = size.maxDimension * 0.7f,
                    )
                )
            }
    )
}

/**
 * Barres du haut transparentes sur le fond ambiant, qui deviennent verre (voile dense)
 * dès que le contenu défile dessous.
 */
@Composable
fun synxioTopBarColors(): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = scheme.surfaceContainer.copy(alpha = if (isDarkSurface) 0.88f else 0.92f),
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
