package fr.synxio.player.ui.screens

/**
 * Carte « Course » de la section « Flow » de l'accueil (spec 6.0 §10.2) : seulement si
 * l'appareil a un podomètre et qu'une course a déjà été lancée. Sans podomètre la course
 * n'a pas de sens ; sans première course, la carte de la bibliothèque suffit à la découvrir.
 */
internal fun showRunCard(hasStepDetector: Boolean, runDone: Boolean): Boolean =
    hasStepDetector && runDone
