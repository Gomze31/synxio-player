package fr.synxio.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion

/**
 * Note de 1 à 5 étoiles. Toucher la note déjà donnée l'efface : c'est à l'appelant de
 * traduire ce second toucher (voir `AppViewModel.rateCurrent`).
 *
 * Chaque étoile est une cible de 40 dp et un choix d'un groupe, annoncé « 3 étoiles,
 * sélectionné » par le lecteur d'écran.
 */
@Composable
fun StarRating(
    rating: Int,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 22.dp,
) {
    val reduce = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .selectableGroup()
            .semantics { contentDescription = if (rating == 0) "Pas de note" else "Note : $rating sur 5" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (stars in 1..5) {
            val filled = stars <= rating
            val scale by animateFloatAsState(
                targetValue = if (filled && !reduce) 1.08f else 1f,
                animationSpec = Motion.press(),
                label = "star$stars",
            )
            Icon(
                imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = if (stars == 1) "1 étoile" else "$stars étoiles",
                tint = if (filled) scheme.primary else scheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier
                    .selectable(selected = stars == rating, role = Role.RadioButton) { onRate(stars) }
                    .size(starSize + 18.dp)
                    .scale(scale)
                    .padding(9.dp),
            )
        }
    }
}
