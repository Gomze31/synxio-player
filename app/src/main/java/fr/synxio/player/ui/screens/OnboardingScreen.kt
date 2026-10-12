package fr.synxio.player.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.synxio.player.R
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premier lancement : ce que fait Synxio, en trois points, et une seule action.
 *
 * Pas de bouton retour : il n'y a nulle part où revenir, et l'ancien retour masquait
 * l'écran sans le marquer comme vu — il réapparaissait au lancement suivant.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val reveal = remember { List(4) { Animatable(if (reduce) 1f else 0f) } }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        // Apparitions décalées : chaque bloc part 70 ms après le précédent.
        reveal.forEach { anim ->
            launch { anim.animateTo(1f, Motion.enter(Motion.LONG)) }
            delay(70)
        }
    }
    fun Modifier.revealed(index: Int) = graphicsLayer {
        val v = reveal[index].value
        alpha = v
        translationY = (1f - v) * 16.dp.toPx()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl, vertical = Space.xxl),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(96.dp)
                    .revealed(0),
            )
            Spacer(Modifier.height(Space.lg))
            Text(
                "Ta musique,\nrien qu'à toi.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .semantics { heading() }
                    .revealed(0),
            )
            Spacer(Modifier.height(Space.md))
            Text(
                "Synxio lit les fichiers audio de ton téléphone. Aucun compte, aucune publicité. Les services en ligne (paroles, Last.fm, Discord) sont optionnels.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.revealed(1),
            )

            Spacer(Modifier.height(Space.xxl))

            Column(
                verticalArrangement = Arrangement.spacedBy(Space.lg),
                modifier = Modifier.revealed(2),
            ) {
                Feature(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Un son à ton goût",
                    body = "Égaliseur, normalisation du volume, fondu enchaîné et mode karaoké.",
                )
                Feature(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Une app qui prend la couleur de ta musique",
                    body = "L'interface se teinte selon la pochette du titre en cours.",
                )
                Feature(
                    icon = Icons.Rounded.LockOpen,
                    title = "Tes données restent ici",
                    body = "Favoris, playlists et statistiques sont stockés sur l'appareil.",
                )
            }

            Spacer(Modifier.height(Space.xxxl))

            Button(
                onClick = onFinish,
                shape = SynxioShape.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .revealed(3),
            ) {
                Text("Commencer", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(SynxioShape.pill)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Space.xxs))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
