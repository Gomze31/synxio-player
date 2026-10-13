package fr.synxio.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape

/**
 * Pochette d'album. MediaStore renvoie souvent `null` : dans ce cas on affiche un
 * aplat tonal avec une note de musique discrète plutôt qu'un carré gris tristounet.
 *
 * La requête Coil est mémorisée par modèle : sans ça, chaque recomposition d'une ligne
 * (le titre en cours qui change, par exemple) reconstruisait une requête neuve.
 * Le fondu enchaîné est coupé quand l'utilisateur a demandé moins d'animations.
 */
@Composable
fun Artwork(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = SynxioShape.artworkMd,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val request = remember(model, reduceMotion) {
        model?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(if (reduceMotion) 0 else Motion.MEDIUM)
                .build()
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Mosaïque 2×2 pour les playlists et genres, à la façon des dossiers de photos. */
@Composable
fun ArtworkMosaic(
    models: List<Any?>,
    modifier: Modifier = Modifier,
    shape: Shape = SynxioShape.artworkMd,
) {
    if (models.size < 2) {
        Artwork(models.firstOrNull(), modifier, shape)
        return
    }
    Column(modifier.clip(shape)) {
        Row(Modifier.weight(1f)) {
            Artwork(models.getOrNull(0), Modifier.weight(1f).fillMaxSize(), RectangleShape)
            Artwork(models.getOrNull(1), Modifier.weight(1f).fillMaxSize(), RectangleShape)
        }
        Row(Modifier.weight(1f)) {
            Artwork(models.getOrNull(2) ?: models.getOrNull(0), Modifier.weight(1f).fillMaxSize(), RectangleShape)
            Artwork(models.getOrNull(3) ?: models.getOrNull(1), Modifier.weight(1f).fillMaxSize(), RectangleShape)
        }
    }
}

/**
 * Titre de section : `titleLarge`, annoncé comme en-tête par TalkBack pour qu'on puisse
 * sauter de section en section. L'action « Tout voir » reste un `TextButton` à droite.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val hasAction = actionLabel != null && onAction != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Space.gutter,
                // Le TextButton porte déjà son propre rembourrage horizontal.
                end = if (hasAction) Space.sm else Space.gutter,
                top = Space.xl,
                bottom = Space.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics { heading() },
        )
        if (hasAction) {
            TextButton(onClick = onAction!!) { Text(actionLabel!!) }
        }
    }
}

/**
 * Texte qui défile tout seul s'il est trop long — indispensable pour les titres.
 * Le défilement est une animation ambiante : il s'arrête (ellipse) quand les animations
 * sont réduites.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
    animate: Boolean = true,
) {
    val scroll = animate && !LocalReduceMotion.current
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (scroll) modifier.basicMarquee(iterations = Int.MAX_VALUE) else modifier,
    )
}

/**
 * État vide (ou introuvable, ou erreur) commun à tous les écrans : icône dans un rond
 * `primaryContainer`, titre, explication et action optionnelle. Un seul composant pour
 * que tous les « rien ici » de l'app se ressemblent.
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.MusicNote,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.xxl, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = scheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(Space.xl))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            FilledTonalButton(onClick = onAction, shape = SynxioShape.pill) { Text(actionLabel) }
        }
    }
}

/** Rayon des barres de l'indicateur : demi-largeur, pour des barres en gélule. */
private val BarShape = RoundedCornerShape(percent = 50)

/** Hauteurs figées : on lit encore « ça joue » sans le moindre mouvement. */
private val FrozenBarFractions = floatArrayOf(0.55f, 1f, 0.7f, 0.4f)

/**
 * Barres animées indiquant qu'un titre est en cours de lecture. En pause, elles se
 * couchent ; animations réduites, elles restent figées à des hauteurs variées.
 * Purement décoratif : l'état est annoncé par la ligne elle-même (`stateDescription`).
 */
@Composable
fun PlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val reduceMotion = LocalReduceMotion.current
    val barHeight = 16.dp
    val barWidth = 3.dp

    Row(
        modifier = modifier.height(barHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        when {
            !isPlaying -> repeat(4) {
                Box(Modifier.width(barWidth).height(barHeight * 0.25f).clip(BarShape).background(color))
            }
            reduceMotion -> repeat(4) { index ->
                Box(
                    Modifier
                        .width(barWidth)
                        .height(barHeight * FrozenBarFractions[index])
                        .clip(BarShape)
                        .background(color)
                )
            }
            else -> {
                val transition = rememberInfiniteTransition(label = "bars")
                repeat(4) { index ->
                    val fraction by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 380 + index * 120,
                                easing = FastOutSlowInEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "bar$index",
                    )
                    Box(
                        Modifier
                            .width(barWidth)
                            .height(barHeight * fraction)
                            .clip(BarShape)
                            .background(color)
                    )
                }
            }
        }
    }
}
