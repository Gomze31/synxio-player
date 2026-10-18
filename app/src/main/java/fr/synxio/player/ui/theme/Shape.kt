package fr.synxio.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formes Material alignées sur [Radius] : dialogues, feuilles, menus et champs prennent
 * les mêmes arrondis que les composants Synxio, sans réglage écran par écran.
 */
val SynxioShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

val ArtworkShape = RoundedCornerShape(Radius.artworkMd)
val MiniPlayerShape = RoundedCornerShape(Radius.lg)
val SheetShape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)
