package fr.synxio.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.isDarkSurface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Composants signature de Synxio « Lumen ».
 *
 * Ce sont les briques que les écrans assemblent : un écran n'invente pas son propre
 * bouton lecture, sa propre puce ou son propre squelette de chargement. Toutes
 * respectent « Réduire les animations » et les effets lumineux désactivables.
 */

// ---------------------------------------------------------------------------------------
// Lecture / pause morphologique
// ---------------------------------------------------------------------------------------

/**
 * Icône lecture/pause qui se métamorphose : le triangle se scinde en deux moitiés qui
 * deviennent les deux barres de la pause, et inversement. Dessinée, pas une image : la
 * transition est continue et interruptible (un double toucher rapide repart d'où elle en
 * est, sans saut).
 */
@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val reduce = LocalReduceMotion.current
    val p by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (reduce) snap() else spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "playPauseMorph",
    )
    val path = remember { Path() }
    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun lerp(a: Float, b: Float) = a + (b - a) * p

        // Chaque moitié : quatre sommets, (lecture) -> (pause).
        fun quad(play: FloatArray, pause: FloatArray) {
            path.reset()
            for (i in 0 until 4) {
                val x = ox + s * lerp(play[i * 2], pause[i * 2])
                val y = oy + s * lerp(play[i * 2 + 1], pause[i * 2 + 1])
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color)
        }
        quad(PLAY_LEFT, PAUSE_LEFT)
        quad(PLAY_RIGHT, PAUSE_RIGHT)
    }
}

// Triangle de lecture légèrement décalé à droite : centré optiquement, pas géométriquement.
private val PLAY_LEFT = floatArrayOf(0.32f, 0.20f, 0.58f, 0.35f, 0.58f, 0.65f, 0.32f, 0.80f)
private val PLAY_RIGHT = floatArrayOf(0.58f, 0.35f, 0.84f, 0.50f, 0.84f, 0.50f, 0.58f, 0.65f)
private val PAUSE_LEFT = floatArrayOf(0.26f, 0.22f, 0.43f, 0.22f, 0.43f, 0.78f, 0.26f, 0.78f)
private val PAUSE_RIGHT = floatArrayOf(0.57f, 0.22f, 0.74f, 0.22f, 0.74f, 0.78f, 0.57f, 0.78f)

/**
 * Le bouton lecture signature : disque au dégradé Lumen, lueur colorée, icône
 * morphologique. Il s'enfonce sous le doigt avant même que l'audio réponde, et un anneau
 * tourne autour pendant la mise en mémoire tampon.
 */
@Composable
fun BrandPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    isBuffering: Boolean = false,
) {
    val colors = SynxioTheme.colors
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.9f else 1f,
        animationSpec = Motion.press(),
        label = "brandPlayPress",
    )
    Box(modifier.size(size + 14.dp), contentAlignment = Alignment.Center) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(size + 12.dp),
                strokeWidth = 2.dp,
                color = colors.brandEnd,
            )
        }
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .glowShadow(CircleShape, colors.brandEnd, elevation = if (isPlaying) 26.dp else 14.dp)
                .clip(CircleShape)
                .background(colors.brandBrush)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = colors.onBrand),
                    role = Role.Button,
                    onClickLabel = if (isPlaying) "Mettre en pause" else "Lire",
                    onClick = onClick,
                )
                .semantics { stateDescription = if (isPlaying) "Lecture en cours" else "En pause" },
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseIcon(
                isPlaying = isPlaying,
                color = colors.onBrand,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Favori
// ---------------------------------------------------------------------------------------

/**
 * Cœur favori : à l'ajout, il se contracte puis éclate en huit étincelles qui
 * s'éloignent et s'éteignent. Au retrait, il se dégonfle simplement : retirer ne mérite
 * pas de fête. État annoncé aux lecteurs d'écran.
 */
@Composable
fun HeartButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    idleTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val reduce = LocalReduceMotion.current
    val favorite = SynxioTheme.colors.favorite
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    // Ignore le premier passage : un titre déjà favori ne doit pas éclater à l'ouverture.
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(isFavorite) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (reduce) return@LaunchedEffect
        if (isFavorite) {
            burst.snapTo(0f)
            pop.snapTo(0.6f)
            coroutineScope {
                launch { pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
                launch { burst.animateTo(1f, tween(560, easing = Motion.Decelerate)) }
            }
        } else {
            pop.snapTo(0.82f)
            pop.animateTo(1f, Motion.press())
        }
    }
    val tint by animateColorAsState(
        if (isFavorite) favorite else idleTint,
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "heartTint",
    )
    Box(
        modifier
            .size(Space.touchTarget)
            .drawBehind {
                val b = burst.value
                if (b >= 1f) return@drawBehind
                val alpha = (1f - b).coerceIn(0f, 1f)
                val radius = size.minDimension * (0.3f + 0.45f * b)
                repeat(8) { i ->
                    val angle = (i / 8f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    val c = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    val dot = if (i % 2 == 0) 3.2.dp.toPx() else 2.2.dp.toPx()
                    drawCircle(
                        color = if (i % 2 == 0) favorite else SynxioThemeBurstAlt,
                        radius = dot * (1f - 0.5f * b),
                        center = c,
                        alpha = alpha,
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Button,
                onClick = onToggle,
            )
            .semantics { stateDescription = if (isFavorite) "dans les favoris" else "pas dans les favoris" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}

/** Étincelles secondaires du cœur : ambre, pour un éclat chaud. */
private val SynxioThemeBurstAlt = Color(0xFFFFB547)

// ---------------------------------------------------------------------------------------
// Bascules (aléatoire, répétition…)
// ---------------------------------------------------------------------------------------

/**
 * Bouton icône à deux états : actif, l'icône prend l'accent, rebondit et un point
 * lumineux apparaît dessous ; inactif, elle redevient discrète. Le point porte l'état
 * même sans percevoir la couleur.
 */
@Composable
fun ToggleIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateLabel: String = if (active) "activé" else "désactivé",
    iconSize: Dp = 24.dp,
) {
    val reduce = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        if (active) scheme.primary else scheme.onSurface.copy(alpha = 0.66f),
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "toggleTint",
    )
    val bump = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(active, icon) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (reduce) return@LaunchedEffect
        bump.snapTo(0.75f)
        bump.animateTo(1f, Motion.spatialBouncy())
    }
    Column(
        modifier
            .size(Space.touchTarget + 4.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { stateDescription = stateLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                },
        )
        Spacer(Modifier.height(3.dp))
        AnimatedVisibility(
            visible = active,
            enter = scaleIn(Motion.spatialBouncy()) + fadeIn(Motion.enter(Motion.SHORT)),
            exit = scaleOut(Motion.exit()) + fadeOut(Motion.exit()),
        ) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(scheme.primary)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Puces
// ---------------------------------------------------------------------------------------

/**
 * Puce de filtre Lumen : en verre au repos, remplie de la signature quand elle est
 * choisie. Le remplissage apparaît en fondu, le texte bascule de couleur en même temps.
 */
@Composable
fun LumenChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val reduce = LocalReduceMotion.current
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    val fill by animateFloatAsState(
        if (selected) 1f else 0f,
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "chipFill",
    )
    val content by animateColorAsState(
        if (selected) colors.onBrand else scheme.onSurface,
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "chipContent",
    )
    Row(
        modifier
            .pressBounce(0.94f)
            .heightIn(min = 36.dp)
            .clip(SynxioShape.pill)
            .glass(SynxioShape.pill, strength = 0.5f)
            .drawBehind { if (fill > 0f) drawRect(colors.brandBrushHorizontal, alpha = fill) }
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .semantics { stateDescription = if (selected) "sélectionné" else "non sélectionné" }
            .padding(horizontal = 14.dp, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------
// Boutons
// ---------------------------------------------------------------------------------------

/** Bouton d'action principale : pilule à la signature Lumen, une seule par écran. */
@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = SynxioTheme.colors
    Row(
        modifier
            .pressBounce(0.95f)
            .heightIn(min = Space.touchTarget)
            .glowShadow(SynxioShape.pill, colors.brandEnd, elevation = 14.dp)
            .clip(SynxioShape.pill)
            .background(colors.brandBrushHorizontal, alpha = if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onBrand, maxLines = 1)
    }
}

/** Bouton secondaire : pilule de verre, même gabarit que [BrandButton]. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .pressBounce(0.95f)
            .heightIn(min = Space.touchTarget)
            .clip(SynxioShape.pill)
            .glass(SynxioShape.pill, strength = 0.8f)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------
// Cartes, en-têtes
// ---------------------------------------------------------------------------------------

/** Carte de verre : le conteneur standard des groupes (réglages, statistiques…). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Space.lg,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressBounce(0.98f) else Modifier)
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.7f)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) { content() }
}

/**
 * En-tête d'écran Lumen : surtitre en capitales lumineuses, grand titre en Bricolage,
 * sous-titre optionnel, actions à droite. Remplace la barre Material des écrans
 * principaux, plus éditoriale qu'utilitaire.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.sm, top = Space.lg, bottom = Space.md),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Space.xs))
            }
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ---------------------------------------------------------------------------------------
// Progression, chargement
// ---------------------------------------------------------------------------------------

/** Barre de progression fine à la signature Lumen (mini-lecteur, widgets in-app…). */
@Composable
fun BrandProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
) {
    val brush = SynxioTheme.colors.brandBrushHorizontal
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(SynxioShape.pill)
            .background(trackColor)
    ) {
        val f = progress.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .clip(SynxioShape.pill)
                    .background(brush)
            )
        }
    }
}

/**
 * Reflet qui balaie un squelette de chargement. Immobile (simple voile) quand les
 * animations sont réduites. N'est composé que tant que le squelette est à l'écran.
 */
fun Modifier.shimmer(): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val dark = isDarkSurface
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.07f else 0.06f)
    val light = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.14f else 0.11f)
    if (reduce) return@composed background(base)
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    drawBehind {
        drawRect(base)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, light, Color.Transparent),
                start = Offset(w * x - w * 0.5f, 0f),
                end = Offset(w * x, size.height),
            )
        )
    }
}

/** Ligne fantôme d'une liste de titres pendant le chargement. */
@Composable
fun SkeletonSongRow(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(SynxioShape.artworkSm).shimmer())
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.62f).height(14.dp).clip(SynxioShape.pill).shimmer())
            Spacer(Modifier.height(Space.sm))
            Box(Modifier.fillMaxWidth(0.38f).height(11.dp).clip(SynxioShape.pill).shimmer())
        }
    }
}

/** Carte fantôme d'une grille d'albums pendant le chargement. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier.padding(Space.sm)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(SynxioShape.artworkMd)
                .shimmer()
        )
        Spacer(Modifier.height(Space.sm))
        Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(SynxioShape.pill).shimmer())
        Spacer(Modifier.height(Space.xs))
        Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(SynxioShape.pill).shimmer())
    }
}

// ---------------------------------------------------------------------------------------
// Feuilles
// ---------------------------------------------------------------------------------------

/**
 * Poignée des feuilles modales : une gélule à la signature, plus courte et plus franche
 * que la poignée grise de Material. Passer en `dragHandle = { SheetHandle() }`.
 */
@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = Space.md, bottom = Space.sm)
            .size(width = 40.dp, height = 5.dp)
            .clip(SynxioShape.pill)
            .background(SynxioTheme.colors.brandBrushHorizontal, alpha = 0.85f)
    )
}

// ---------------------------------------------------------------------------------------
// Confirmations
// ---------------------------------------------------------------------------------------

/**
 * Snackbar Lumen : pilule de verre dense flottant au-dessus du mini-lecteur, avec un
 * liseré de signature à gauche. Discrète, elle confirme sans interrompre.
 */
@Composable
fun SynxioSnackbar(data: SnackbarData) {
    val scheme = MaterialTheme.colorScheme
    val brand = SynxioTheme.colors.brandBrush
    val glow = LocalGlowEffects.current
    Snackbar(
        modifier = Modifier
            .padding(horizontal = Space.md, vertical = Space.xs)
            .then(if (glow) Modifier.glowShadow(SynxioShape.cardSm, SynxioTheme.colors.brandEnd, 10.dp) else Modifier)
            .drawBehind {
                drawRect(brand, size = size.copy(width = 4.dp.toPx()))
            },
        shape = SynxioShape.cardSm,
        containerColor = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        action = data.visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = data::performAction) {
                    Text(label, color = scheme.inversePrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
    }
}
