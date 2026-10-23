package fr.synxio.player.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import fr.synxio.player.ui.theme.LocalReduceMotion

/*
 * Transitions partagées.
 *
 * Une pochette touchée dans une grille ne disparaît pas pour réapparaître ailleurs :
 * elle grandit jusqu'à sa place dans l'écran suivant. Même chose entre le mini-lecteur
 * et le lecteur plein écran.
 *
 * Deux portées, fournies par [fr.synxio.player.ui.SynxioRoot] :
 *  - [LocalSharedTransitionScope] : la couche commune où voyagent les éléments ;
 *  - [LocalNavAnimatedScope] : la transition de navigation de l'écran courant
 *    (chaque destination du NavHost la fournit), ou [LocalPlayerAnimatedScope] pour le
 *    couple mini-lecteur / lecteur.
 *
 * Sans l'une d'elles (aperçu, écran hors navigation, animations réduites), les
 * modificateurs ne font rien : l'élément s'affiche normalement.
 */

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalPlayerAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Clés partagées : un seul endroit pour que les deux bouts s'accordent. */
object SharedKeys {
    fun albumArt(albumId: Long) = "album-art-$albumId"
    fun albumTitle(albumId: Long) = "album-title-$albumId"
    fun artistArt(name: String) = "artist-art-$name"
    fun playlistArt(id: Long) = "playlist-art-$id"
    fun playerArt(songId: Long) = "player-art-$songId"
    fun playerTitle(songId: Long) = "player-title-$songId"
}

/** Trajectoire des éléments partagés : ressort ferme, sans dépassement visible. */
private val SharedBounds = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.86f, stiffness = 380f)
}

/**
 * Fait voyager cet élément entre deux écrans de navigation portant la même [key].
 * À poser sur la pochette d'une carte ET sur la pochette d'en-tête de l'écran de détail.
 */
@Composable
fun Modifier.sharedNavElement(key: Any, shape: Shape? = null): Modifier =
    sharedElementIn(key, LocalNavAnimatedScope.current, shape)

/** Même chose entre le mini-lecteur et le lecteur plein écran. */
@Composable
fun Modifier.sharedPlayerElement(key: Any, shape: Shape? = null): Modifier =
    sharedElementIn(key, LocalPlayerAnimatedScope.current, shape)

@Composable
private fun Modifier.sharedElementIn(key: Any, scope: AnimatedVisibilityScope?, shape: Shape?): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (scope == null || LocalReduceMotion.current) return this
    return with(shared) {
        val state = rememberSharedContentState(key)
        if (shape != null) {
            this@sharedElementIn.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = scope,
                boundsTransform = SharedBounds,
                clipInOverlayDuringTransition = OverlayClip(shape),
            )
        } else {
            this@sharedElementIn.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = scope,
                boundsTransform = SharedBounds,
            )
        }
    }
}

/**
 * Variante « limites partagées » pour du texte ou des conteneurs dont le contenu change
 * entre les deux écrans (titre en `titleSmall` dans la carte, `displaySmall` dans
 * l'en-tête) : la boîte voyage, le contenu se fond.
 */
@Composable
fun Modifier.sharedNavBounds(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedNavBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            boundsTransform = SharedBounds,
        )
    }
}
