package fr.synxio.player.watch.ui

import androidx.compose.runtime.compositionLocalOf

/** Vrai en mode ambiant : version plate du lecteur, aucun rafraîchissement périodique. */
val LocalAmbient = compositionLocalOf { false }
