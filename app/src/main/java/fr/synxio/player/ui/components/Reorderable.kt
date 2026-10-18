package fr.synxio.player.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Liste réorganisable au doigt, par une poignée.
 *
 * Le déplacement se fait sur une copie locale ([items]) : la ligne suit le doigt et les
 * autres s'écartent en direct, sans attendre l'aller-retour vers la session ou la base.
 * Un seul déplacement « de → vers » est validé au lâcher ([onCommit]).
 *
 * [keyOf] doit être stable et unique (un même titre peut figurer deux fois) ;
 * [firstIndex] est la position de la première ligne déplaçable dans la `LazyColumn`
 * (en-têtes éventuels avant elle).
 */
@Stable
class ReorderState<T>(
    val listState: LazyListState,
    private val keyOf: (T) -> Any,
    private val firstIndex: Int,
    /** Mis à jour à chaque composition : la dernière version de l'appelant est utilisée. */
    var onCommit: (from: Int, to: Int) -> Unit,
) {
    val items = mutableStateListOf<T>()

    var draggingKey by mutableStateOf<Any?>(null)
        private set

    private var dragTop by mutableFloatStateOf(0f)
    private var startIndex = -1
    /** Sens du dernier mouvement du doigt (négatif = vers le haut). */
    private var lastDy = 0f

    val isDragging: Boolean get() = draggingKey != null

    /** Recopie la liste source, sauf pendant un glissement (elle serait écrasée). */
    fun sync(source: List<T>) {
        if (isDragging) return
        items.clear()
        items.addAll(source)
    }

    fun indexOfKey(key: Any): Int = items.indexOfFirst { keyOf(it) == key }

    fun start(key: Any) {
        val index = indexOfKey(key)
        val info = info(index) ?: return
        startIndex = index
        dragTop = info.offset.toFloat()
        lastDy = 0f
        draggingKey = key
    }

    /** Déplace la ligne tenue ; renvoie vrai si elle a changé de place (retour haptique). */
    fun drag(dy: Float): Boolean {
        val key = draggingKey ?: return false
        dragTop += dy
        if (dy != 0f) lastDy = dy
        return swapIfNeeded(key)
    }

    private fun swapIfNeeded(key: Any): Boolean {
        val index = indexOfKey(key)
        val info = info(index) ?: return false
        val center = dragTop + info.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            val i = it.index - firstIndex
            i in items.indices && i != index && center >= it.offset && center <= it.offset + it.size
        } ?: return false
        val to = target.index - firstIndex
        // La liste garde son ancrage sur la clé de la première ligne visible : déplacer
        // cette ligne la faisait défiler avec elle, et les échanges s'enchaînaient sans
        // fin. On fige la position de défilement le temps de l'échange.
        val first = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        items.add(to, items.removeAt(index))
        listState.requestScrollToItem(first, firstOffset)
        return true
    }

    fun end() {
        val key = draggingKey ?: return
        val to = indexOfKey(key)
        val from = startIndex
        draggingKey = null
        startIndex = -1
        if (from >= 0 && to >= 0 && from != to) onCommit(from, to)
    }

    /** Décalage vertical à appliquer à la ligne [key] : seule la ligne tenue bouge. */
    fun offsetOf(key: Any): Float {
        if (key != draggingKey) return 0f
        val info = info(indexOfKey(key)) ?: return 0f
        return dragTop - info.offset
    }

    /**
     * Vitesse de défilement automatique quand la ligne tenue déborde d'un bord.
     *
     * Seulement si le doigt va vers ce bord : saisir la première ligne visible, déjà au
     * ras du haut, faisait sinon remonter toute la liste sans le moindre geste.
     */
    internal fun edgeScroll(): Float {
        val key = draggingKey ?: return 0f
        val info = info(indexOfKey(key)) ?: return 0f
        val layout = listState.layoutInfo
        val top = layout.viewportStartOffset.toFloat()
        val bottom = layout.viewportEndOffset.toFloat()
        val overTop = top - dragTop
        val overBottom = dragTop + info.size - bottom
        return when {
            overTop > 0f && lastDy < 0f -> -(overTop / info.size).coerceIn(0.1f, 1f) * MAX_SCROLL
            overBottom > 0f && lastDy > 0f -> (overBottom / info.size).coerceIn(0.1f, 1f) * MAX_SCROLL
            else -> 0f
        }
    }

    internal fun afterScroll() {
        draggingKey?.let { swapIfNeeded(it) }
    }

    private fun info(index: Int): LazyListItemInfo? {
        if (index < 0) return null
        val target = index + firstIndex
        return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
    }

    private companion object {
        const val MAX_SCROLL = 18f
    }
}

@Composable
fun <T> rememberReorderState(
    listState: LazyListState,
    source: List<T>,
    keyOf: (T) -> Any,
    firstIndex: Int = 0,
    onCommit: (from: Int, to: Int) -> Unit,
): ReorderState<T> {
    val state = remember(listState) { ReorderState(listState, keyOf, firstIndex, onCommit) }
    state.onCommit = onCommit
    // Pas de remember sur la source : on la recopie à chaque changement, hors glissement.
    LaunchedEffect(source) { state.sync(source) }
    LaunchedEffect(state.isDragging) {
        while (state.isDragging) {
            val speed = state.edgeScroll()
            if (speed != 0f) {
                state.listState.scrollBy(speed)
                state.afterScroll()
            }
            delay(16)
        }
    }
    return state
}

/** Poignée de déplacement : un glissement vertical déplace la ligne [key]. */
@Composable
fun Modifier.reorderHandle(state: ReorderState<*>, key: Any): Modifier {
    val haptics = LocalHapticFeedback.current
    return pointerInput(key) {
        detectDragGestures(
            onDragStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.start(key)
            },
            onDrag = { change, amount ->
                change.consume()
                if (state.drag(amount.y)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
}

/** À poser sur chaque ligne : la ligne tenue suit le doigt et passe au-dessus des autres. */
fun Modifier.reorderItem(state: ReorderState<*>, key: Any): Modifier =
    zIndex(if (state.draggingKey == key) 1f else 0f)
        .graphicsLayer {
            val dragged = state.draggingKey == key
            translationY = state.offsetOf(key)
            scaleX = if (dragged) 1.02f else 1f
            scaleY = if (dragged) 1.02f else 1f
            shadowElevation = if (dragged) 12f else 0f
        }
