package fr.synxio.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Coins généreux : l'app doit respirer, pas ressembler à un tableur. */
val SynxioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

val ArtworkShape = RoundedCornerShape(20.dp)
val MiniPlayerShape = RoundedCornerShape(22.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
