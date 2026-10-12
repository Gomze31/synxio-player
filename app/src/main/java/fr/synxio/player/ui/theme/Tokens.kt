package fr.synxio.player.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/*
 * Design system Synxio.
 *
 * Toutes les valeurs visuelles partagées vivent ici : un écran ne choisit plus ses
 * marges, rayons ou durées à la main. Changer l'identité de l'app revient à changer
 * ces tokens, pas à parcourir trente écrans.
 */

/** Échelle d'espacement sur une grille de 4 dp. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Marge latérale des écrans : un peu plus que 16 dp, les pochettes respirent. */
    val gutter = 20.dp

    /** Hauteur minimale d'une cible tactile (Material, WCAG 2.5.5). */
    val touchTarget = 48.dp
}

/**
 * Rayons de coins. Une famille resserrée : les éléments d'un même rôle ont toujours
 * le même arrondi, où qu'ils soient.
 */
object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** Vignette de pochette dans une ligne de liste. */
    val artworkSm = 10.dp
    /** Pochette en carte (grilles, carrousels). */
    val artworkMd = 16.dp
    /** Pochette héroïque : lecteur, en-têtes de détail. */
    val artworkLg = 28.dp
}

object SynxioShape {
    val artworkSm = RoundedCornerShape(Radius.artworkSm)
    val artworkMd = RoundedCornerShape(Radius.artworkMd)
    val artworkLg = RoundedCornerShape(Radius.artworkLg)
    val card = RoundedCornerShape(Radius.lg)
    val pill = RoundedCornerShape(percent = 50)
    val sheet = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)
}

/**
 * Langage de mouvement.
 *
 * Trois durées, trois courbes. Les entrées décélèrent (l'élément arrive et se pose),
 * les sorties accélèrent (il s'efface sans traîner), le reste suit la courbe standard
 * « emphasized » de Material 3.
 */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 280
    const val LONG = 420

    /** Courbe standard : départ franc, arrivée très douce. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Entrées à l'écran. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Sorties de l'écran. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> standard(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, easing = Standard)
    fun <T> enter(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, easing = Decelerate)
    fun <T> exit(duration: Int = SHORT): FiniteAnimationSpec<T> = tween(duration, easing = Accelerate)

    /** Retour tactile : rebond court, sans oscillation visible. */
    fun <T> press(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Variante qui coupe net quand l'utilisateur a demandé moins d'animations. */
    fun <T> standardOrSnap(reduce: Boolean, duration: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduce) snap() else standard(duration)
}

/**
 * Vrai si l'utilisateur veut moins d'animations : réglage « Réduire les animations »
 * de Synxio, ou animations désactivées dans les options d'accessibilité du système.
 *
 * Les animations ambiantes (fond qui tourne, vinyle, égaliseur décoratif) doivent alors
 * s'arrêter, et les transitions se réduire à un fondu bref ou une coupe.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Lit le facteur d'animation du système (0 = animations coupées). */
@Composable
fun rememberSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Couleurs sémantiques hors du schéma Material. */
@Immutable
data class SynxioColors(
    val favorite: Color = Color(0xFFFF4F8B),
    val rankGold: Color = Color(0xFFE8B931),
    val rankSilver: Color = Color(0xFFB8BEC8),
    val rankBronze: Color = Color(0xFFC9814B),
    /** Voile posé sur une pochette pour y lire du texte. */
    val scrim: Color = Color.Black.copy(alpha = 0.55f),
    val vinyl: Color = Color(0xFF111114),
    val vinylGroove: Color = Color(0xFF26262C),
)

val LocalSynxioColors = staticCompositionLocalOf { SynxioColors() }

/** Accès court : `SynxioTheme.colors.favorite`. */
object SynxioTheme {
    val colors: SynxioColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSynxioColors.current

    val reduceMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReduceMotion.current
}
