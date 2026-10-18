package fr.synxio.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import kotlinx.coroutines.delay

/*
 * Micro-interactions partagées.
 *
 * Toutes s'effacent quand l'utilisateur a demandé moins d'animations (réglage Synxio ou
 * animations système coupées) : l'interface reste alors immédiate et immobile.
 */

/**
 * La carte s'enfonce légèrement sous le doigt puis rebondit au relâcher.
 *
 * Observe le toucher sans le consommer : le `clickable` posé sur le même élément
 * continue de recevoir le geste normalement.
 */
fun Modifier.pressBounce(pressedScale: Float = 0.95f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = Motion.press(),
        label = "pressBounce",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressed = false
            }
        }
}

/**
 * Apparition en cascade : l'élément monte de quelques dp en fondu, avec un léger
 * décalage selon sa position ([index]).
 *
 * Ne joue qu'une fois par élément ([key]) : revenir en arrière ou faire défiler une
 * liste ne refait pas l'animation, qui deviendrait vite fatigante.
 */
fun Modifier.enterCascade(index: Int, key: Any = index): Modifier = composed {
    val reduce = LocalReduceMotion.current
    var shown by rememberSaveable(key) { mutableStateOf(reduce) }
    val progress = remember(key) { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(key) {
        if (shown) return@LaunchedEffect
        delay(index.coerceIn(0, Motion.MAX_STAGGERED) * Motion.STAGGER_MS)
        progress.animateTo(1f, tween(Motion.LONG, easing = Motion.Decelerate))
        shown = true
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 22.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}
