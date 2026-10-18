package fr.synxio.player.core.util

import kotlin.math.absoluteValue

/**
 * Couleurs des pochettes générées quand un titre n'en a pas.
 *
 * Partagées par l'application (Compose) et les widgets (bitmap) : un album sans image a
 * le même dégradé partout. Le choix dépend du modèle de la pochette (son URI), stable
 * d'un lancement à l'autre.
 */
object PlaceholderPalette {

    /** Paires ARGB début → fin : profondes, jamais criardes. */
    val pairs: List<Pair<Int, Int>> = listOf(
        0xFF5B3CC4.toInt() to 0xFF1E1440.toInt(),
        0xFFB0457E.toInt() to 0xFF2E1230.toInt(),
        0xFF2F7F9E.toInt() to 0xFF10233A.toInt(),
        0xFFC0743C.toInt() to 0xFF34161A.toInt(),
        0xFF3E8F6E.toInt() to 0xFF0F2A2A.toInt(),
        0xFF7A4FD8.toInt() to 0xFF3A1A4F.toInt(),
        0xFFD0566A.toInt() to 0xFF3A1430.toInt(),
        0xFF4A63C9.toInt() to 0xFF161B3E.toInt(),
    )

    fun pairFor(seed: Any?): Pair<Int, Int> =
        pairs[(seed?.hashCode() ?: 0).absoluteValue % pairs.size]
}
