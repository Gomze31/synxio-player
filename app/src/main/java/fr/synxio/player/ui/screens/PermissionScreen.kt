package fr.synxio.player.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow

/**
 * Demande d'accès aux fichiers audio, dans la grammaire des états vides Lumen : disque de
 * verre cerclé de la signature sur un halo, titre, explication, une seule action.
 *
 * Le fond est l'aurore de la racine : rien d'opaque ici.
 */
@Composable
fun PermissionScreen(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
    permanentlyDenied: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    val glow = LocalGlowEffects.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.enterCascade(0, key = "perm-icon")) {
            if (glow) {
                Box(
                    Modifier
                        .size(220.dp)
                        .background(
                            Brush.radialGradient(listOf(colors.brandEnd.copy(alpha = 0.30f), Color.Transparent))
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .glowShadow(CircleShape, colors.brandEnd, elevation = 24.dp)
                    .clip(CircleShape)
                    .glass(CircleShape, strength = 1f, border = false)
                    .border(2.dp, colors.brandBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // L'icône change si l'accès a été refusé pour de bon : on ne demande plus la
                // musique, on montre le chemin vers les réglages.
                AnimatedContent(
                    targetState = permanentlyDenied,
                    transitionSpec = {
                        fadeIn(Motion.enter(if (reduce) Motion.XS else Motion.MEDIUM)) togetherWith
                            fadeOut(Motion.exit(Motion.XS))
                    },
                    label = "permIcon",
                ) { denied ->
                    Icon(
                        imageVector = if (denied) Icons.Rounded.Settings else Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xl))
        Text(
            text = if (permanentlyDenied) "ACCÈS REFUSÉ" else "BIENVENUE",
            style = EyebrowTextStyle,
            color = scheme.primary,
            modifier = Modifier.enterCascade(1, key = "perm-eyebrow"),
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Synxio",
            style = MaterialTheme.typography.displayMedium.copy(brush = colors.brandBrushHorizontal),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .enterCascade(1, key = "perm-title"),
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = "Ta musique locale, en beau.",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.enterCascade(2, key = "perm-tagline"),
        )
        Spacer(Modifier.height(Space.lg))
        Text(
            text = if (permanentlyDenied) {
                "L'accès a été refusé. Ouvre les réglages de l'app, puis Autorisations > " +
                    "Musique et audio, pour que Synxio puisse lire ta bibliothèque."
            } else {
                "Synxio a besoin d'accéder aux fichiers audio de ton appareil pour " +
                    "construire ta bibliothèque. Elle reste sur ton téléphone : pas de " +
                    "compte, pas de traceur, pas de publicité."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.enterCascade(3, key = "perm-body"),
        )

        Spacer(Modifier.height(Space.lg))
        // Promesse résumée en une pilule : ce qui rassure se lit d'un coup d'œil.
        Row(
            modifier = Modifier
                .enterCascade(4, key = "perm-promise")
                .clip(SynxioShape.pill)
                .glass(SynxioShape.pill, strength = 0.5f)
                .padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
            Text(
                "Sans compte · sans pub · hors ligne",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
            )
        }

        Spacer(Modifier.height(Space.xxl))
        BrandButton(
            text = if (permanentlyDenied) "Ouvrir les réglages" else "Autoriser l'accès",
            icon = if (permanentlyDenied) Icons.Rounded.Settings else Icons.Rounded.LibraryMusic,
            onClick = {
                if (permanentlyDenied) {
                    // La demande système ne s'affiche plus après « Ne plus demander » :
                    // seuls les réglages de l'app permettent encore d'accorder l'accès.
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    onRequest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .enterCascade(5, key = "perm-cta"),
        )
    }
}
