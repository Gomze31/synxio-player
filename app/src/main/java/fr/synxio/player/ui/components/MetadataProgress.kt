package fr.synxio.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.data.meta.EnrichPhase
import fr.synxio.player.data.meta.EnrichProgress
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass

/**
 * Récupération d'infos en cours, posée au-dessus du mini-lecteur.
 *
 * Verre Lumen, pastille d'icône au dégradé de la marque (geste explicite de
 * l'utilisateur) et barre de progression de la signature. La progression glisse en
 * effet court, sans animation infinie : rien ne tourne en boucle si l'on a demandé moins
 * d'animations.
 */
@Composable
fun MetadataProgressPill(progress: EnrichProgress, modifier: Modifier = Modifier) {
    val reduceMotion = SynxioTheme.reduceMotion
    val shown by animateFloatAsState(
        targetValue = progress.overallFraction(),
        animationSpec = if (reduceMotion) snap() else Motion.effects(),
        label = "metadata-progress",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.xs)
            .clip(SynxioShape.card)
            .glass(SynxioShape.card, strength = 0.75f)
            .padding(horizontal = Space.md, vertical = Space.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SynxioTheme.colors.brandBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ImageSearch,
                contentDescription = null,
                tint = SynxioTheme.colors.onBrand,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = progress.phase.label.uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = progress.label.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (progress.total > 1) {
                    Text(
                        text = "${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
            BrandProgressBar(progress = shown)
        }
    }
}

/**
 * Avancement global : la lecture locale compte pour un tiers, la recherche en ligne, bien
 * plus lente (une requête par seconde), pour le reste.
 */
internal fun EnrichProgress.overallFraction(): Float = when (phase) {
    EnrichPhase.IDLE -> 0f
    EnrichPhase.LOCAL -> fraction / 3f
    EnrichPhase.ONLINE -> 1f / 3f + fraction * 2f / 3f
}
