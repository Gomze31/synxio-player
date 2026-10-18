package fr.synxio.player.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/*
 * Design system Synxio « Lumen ».
 *
 * Toutes les valeurs visuelles partagées vivent ici : un écran ne choisit plus ses
 * marges, rayons ou durées à la main. Changer l'identité de l'app revient à changer
 * ces tokens, pas à parcourir trente écrans.
 *
 * Lumen, en trois idées :
 *  - une scène sombre et profonde (ou un papier chaud en clair), éclairée par la
 *    pochette en cours comme par un projecteur coloré ;
 *  - du verre posé sur cette lumière : surfaces translucides, liseré spéculaire ;
 *  - une signature lumineuse, le dégradé « braise → magenta », réservée aux gestes
 *    importants (lecture, progression, onglet actif) pour qu'elle garde sa valeur.
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

    /** Réserve en bas des listes pour ne rien cacher sous le mini-lecteur flottant. */
    val bottomInset = 24.dp
}

/**
 * Rayons de coins. Une famille resserrée : les éléments d'un même rôle ont toujours
 * le même arrondi, où qu'ils soient. Lumen arrondit franchement : on manipule des
 * objets doux, pas des cellules de tableur.
 */
object Radius {
    val xs = 10.dp
    val sm = 14.dp
    val md = 20.dp
    val lg = 28.dp
    val xl = 36.dp

    /** Vignette de pochette dans une ligne de liste. */
    val artworkSm = 12.dp
    /** Pochette en carte (grilles, carrousels). */
    val artworkMd = 20.dp
    /** Pochette héroïque : lecteur, en-têtes de détail. */
    val artworkLg = 32.dp
}

object SynxioShape {
    val artworkSm = RoundedCornerShape(Radius.artworkSm)
    val artworkMd = RoundedCornerShape(Radius.artworkMd)
    val artworkLg = RoundedCornerShape(Radius.artworkLg)
    val card = RoundedCornerShape(Radius.lg)
    val cardSm = RoundedCornerShape(Radius.md)
    val pill = RoundedCornerShape(percent = 50)
    val sheet = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)
}

/**
 * Langage de mouvement.
 *
 * Deux familles, comme Material 3 Expressive :
 *  - les mouvements **spatiaux** (position, taille, échelle) sont des ressorts : ils
 *    s'interrompent proprement quand le doigt change d'avis et gardent leur élan ;
 *  - les **effets** (opacité, couleur) sont des courbes courtes sans rebond.
 *
 * Les entrées décélèrent (l'élément arrive et se pose), les sorties accélèrent et sont
 * plus brèves que les entrées (il s'efface sans traîner).
 */
object Motion {
    /** Micro-retour (changement d'icône, pression). */
    const val XS = 90
    const val SHORT = 160
    const val MEDIUM = 300
    const val LONG = 450
    /** Changement de palette entre deux morceaux : lent, pour ne pas clignoter. */
    const val PALETTE = 900

    /** Décalage entre deux éléments d'une cascade d'apparition. */
    const val STAGGER_MS = 38L
    /** Au-delà, les éléments arrivent ensemble : attendre le dixième serait trop long. */
    const val MAX_STAGGERED = 10

    /** Courbe standard « emphasized » : départ franc, arrivée très douce. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Entrées à l'écran. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Sorties de l'écran. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> standard(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, easing = Standard)
    fun <T> enter(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, easing = Decelerate)
    fun <T> exit(duration: Int = SHORT): FiniteAnimationSpec<T> = tween(duration, easing = Accelerate)

    /** Déplacement ou redimensionnement : ressort amorti, sans oscillation visible. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)

    /** Variante expressive (bouton lecture, cœur, onglet) : un seul petit rebond. */
    fun <T> spatialBouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.58f, stiffness = 520f)

    /** Opacité, couleur : rapide et sans rebond. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 900f)

    /** Retour tactile : rebond court, l'élément suit le doigt sans retard. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 700f)

    /** Variante qui coupe net quand l'utilisateur a demandé moins d'animations. */
    fun <T> standardOrSnap(reduce: Boolean, duration: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduce) snap() else standard(duration)

    fun <T> spatialOrSnap(reduce: Boolean): FiniteAnimationSpec<T> = if (reduce) snap() else spatial()
}

/**
 * Vrai si l'utilisateur veut moins d'animations : réglage « Réduire les animations »
 * de Synxio, ou animations désactivées dans les options d'accessibilité du système.
 *
 * Les animations ambiantes (fond qui dérive, vinyle, égaliseur décoratif) doivent alors
 * s'arrêter, et les transitions se réduire à un fondu bref ou une coupe.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Effets lumineux (halos, aurore du fond, lueur sous les pochettes). Désactivables dans
 * les réglages : certains les trouvent distrayants, et ils coûtent un peu de GPU.
 */
val LocalGlowEffects = compositionLocalOf { true }

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
    val success: Color = Color(0xFF3DDC97),
    val warning: Color = Color(0xFFFFB547),
    /** Voile posé sur une pochette pour y lire du texte. */
    val scrim: Color = Color.Black.copy(alpha = 0.55f),
    val vinyl: Color = Color(0xFF111114),
    val vinylGroove: Color = Color(0xFF26262C),
    /** Début et fin de la signature lumineuse, dérivés de l'accent courant. */
    val brandStart: Color = Color(0xFFFF8A4C),
    val brandEnd: Color = Color(0xFFFF4F8B),
    /** Contenu posé sur la signature (icône du bouton lecture). */
    val onBrand: Color = Color.White,
) {
    /** La signature : diagonale, du coin haut-gauche au coin bas-droit. */
    val brandBrush: Brush get() = Brush.linearGradient(listOf(brandStart, brandEnd))

    /** La signature à l'horizontale : barres de progression, indicateurs. */
    val brandBrushHorizontal: Brush get() = Brush.horizontalGradient(listOf(brandStart, brandEnd))
}

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

    val glow: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalGlowEffects.current
}
