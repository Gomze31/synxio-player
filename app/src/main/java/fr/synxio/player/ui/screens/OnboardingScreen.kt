package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import fr.synxio.player.R
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.theme.AmbientBackdrop
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Premier lancement : quatre plans, comme le générique d'un album. L'aurore dérive en
 * fond, chaque plan pose un visuel, un grand titre et une phrase ; la pagination à la
 * signature s'étire vers le plan courant en suivant le doigt.
 *
 * Pas de bouton retour : il n'y a nulle part où revenir, et l'ancien retour masquait
 * l'écran sans le marquer comme vu — il réapparaissait au lancement suivant. « Passer »
 * et « Commencer » terminent tous deux l'accueil (et le marquent comme vu).
 *
 * Cet écran est affiché hors de la racine de l'app : il dessine donc lui-même l'aurore.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember { onboardingPages() }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val isLast by remember { derivedStateOf { pager.currentPage == pages.lastIndex } }

    Box(Modifier.fillMaxSize()) {
        // L'aurore dérive seulement ici, écran visible : elle s'arrête d'elle-même avec
        // « Réduire les animations » ou sans effets lumineux.
        AmbientBackdrop(drifting = true)

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .padding(horizontal = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SYNXIO",
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = Space.md)
                        .weight(1f),
                )
                AnimatedVisibility(
                    visible = !isLast,
                    enter = fadeIn(Motion.enter(Motion.SHORT)),
                    exit = fadeOut(Motion.exit()),
                ) {
                    TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = Space.touchTarget)) {
                        Text("Passer", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { index ->
                OnboardingPageContent(
                    page = pages[index],
                    index = index,
                    count = pages.size,
                    pager = pager,
                )
            }

            PagerIndicator(
                pager = pager,
                count = pages.size,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = Space.lg),
            )

            BrandButton(
                text = if (isLast) "Commencer" else "Suivant",
                icon = if (isLast) null else Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch {
                            val target = pager.currentPage + 1
                            if (reduce) pager.animateScrollToPage(target, animationSpec = snap())
                            else pager.animateScrollToPage(target)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xl)
                    .padding(bottom = Space.xl)
                    .heightIn(min = 56.dp),
            )
        }
    }
}

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    /** `null` : logo de l'app sur la signature (premier plan). */
    val icon: ImageVector?,
)

private fun onboardingPages() = listOf(
    OnboardingPage(
        eyebrow = "Bienvenue",
        title = "Ta musique,\nrien qu'à toi.",
        body = "Synxio lit les fichiers audio de ton téléphone. Aucun compte, aucune publicité. " +
            "Les services en ligne (paroles, Last.fm, Discord) sont optionnels.",
        icon = null,
    ),
    OnboardingPage(
        eyebrow = "Le son",
        title = "Un son\nà ton goût.",
        body = "Égaliseur, normalisation du volume, fondu enchaîné et mode karaoké.",
        icon = Icons.Rounded.GraphicEq,
    ),
    OnboardingPage(
        eyebrow = "La lumière",
        title = "L'app prend\nla couleur\nde ta musique.",
        body = "L'interface se teinte selon la pochette du titre en cours, comme éclairée par elle.",
        icon = Icons.Rounded.AutoAwesome,
    ),
    OnboardingPage(
        eyebrow = "Tes données",
        title = "Tout reste\nici.",
        body = "Favoris, playlists et statistiques sont stockés sur l'appareil. Rien ne part sans toi.",
        icon = Icons.Rounded.LockOpen,
    ),
)

/**
 * Un plan : le contenu glisse un peu moins vite que la page et s'estompe en la quittant,
 * ce qui donne de la profondeur (parallaxe) sans aucune animation propre. Animations
 * réduites : la page défile simplement, sans parallaxe.
 */
@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    index: Int,
    count: Int,
    pager: PagerState,
) {
    val reduce = LocalReduceMotion.current
    fun offset(): Float = (pager.currentPage - index) + pager.currentPageOffsetFraction

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (reduce) return@graphicsLayer
                    val o = offset()
                    // Le visuel recule et rétrécit en quittant le plan.
                    translationX = o * size.width * 0.35f
                    val s = lerp(1f, 0.78f, abs(o).coerceIn(0f, 1f))
                    scaleX = s
                    scaleY = s
                    alpha = 1f - abs(o).coerceIn(0f, 1f) * 0.8f
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            OnboardingVisual(page.icon)
        }

        Spacer(Modifier.height(Space.xxl))

        Column(
            Modifier.graphicsLayer {
                if (reduce) return@graphicsLayer
                val o = offset()
                translationX = o * size.width * 0.15f
                alpha = 1f - abs(o).coerceIn(0f, 1f)
            }
        ) {
            Text(
                "${(index + 1).toString().padStart(2, '0')} / ${count.toString().padStart(2, '0')} · ${page.eyebrow.uppercase()}",
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                page.title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.lg))
            Text(
                page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Visuel d'un plan. Le premier porte le logo sur la signature (le geste le plus
 * important : on fait connaissance) ; les suivants un disque de verre cerclé de la
 * signature, posé sur un halo.
 */
@Composable
private fun OnboardingVisual(icon: ImageVector?) {
    val colors = SynxioTheme.colors
    val glow = LocalGlowEffects.current
    Box(contentAlignment = Alignment.Center) {
        if (glow) {
            Box(
                Modifier
                    .size(200.dp)
                    .background(Brush.radialGradient(listOf(colors.brandEnd.copy(alpha = 0.32f), Color.Transparent)))
            )
        }
        if (icon == null) {
            val shape = RoundedCornerShape(Radius.xl)
            Box(
                Modifier
                    .size(120.dp)
                    .glowShadow(shape, colors.brandEnd, elevation = 32.dp)
                    .clip(shape)
                    .background(colors.brandBrush),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.onBrand),
                    modifier = Modifier.size(120.dp),
                )
            }
        } else {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .glass(CircleShape, strength = 1f, border = false)
                    .border(2.dp, colors.brandBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
            }
        }
    }
}

/**
 * Pagination : des points discrets, et le point courant qui s'étire en pilule remplie de
 * la signature. La largeur suit la position réelle du pager (pas un état discret), donc
 * la pilule glisse d'un point à l'autre au rythme du doigt.
 */
@Composable
private fun PagerIndicator(pager: PagerState, count: Int, modifier: Modifier = Modifier) {
    val brand = SynxioTheme.colors.brandBrushHorizontal
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val dot = 8.dp
    val active = 28.dp
    val gap = 8.dp
    Box(
        modifier
            .width(active + (dot + gap) * (count - 1))
            .height(dot)
            .semantics { stateDescription = "Page ${pager.currentPage + 1} sur $count" }
            .drawBehind {
                val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
                val d = dot.toPx()
                val a = active.toPx()
                val g = gap.toPx()
                val r = CornerRadius(d / 2f, d / 2f)
                var x = 0f
                for (i in 0 until count) {
                    // Proximité au plan courant : 1 sur le plan, 0 à un plan d'écart.
                    val near = (1f - abs(position - i)).coerceIn(0f, 1f)
                    val w = lerp(d, a, near)
                    drawRoundRect(color = idle, topLeft = Offset(x, 0f), size = Size(w, d), cornerRadius = r)
                    if (near > 0f) {
                        drawRoundRect(
                            brush = brand,
                            topLeft = Offset(x, 0f),
                            size = Size(w, d),
                            cornerRadius = r,
                            alpha = near,
                        )
                    }
                    x += w + g
                }
            }
    )
}
