package fr.synxio.player.core.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/** `3:07` ou `1:02:33` selon la durée. */
fun Long.asDuration(): String {
    if (this <= 0L) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

/** Durée « longue » pour les en-têtes : `2 h 14 min`, `47 min`. */
fun Long.asLongDuration(): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(this)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
}

fun Long.asFileSize(): String {
    if (this < 1024) return "$this o"
    val kb = this / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.0f Ko", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f Mo", mb)
    return String.format(Locale.getDefault(), "%.2f Go", mb / 1024.0)
}

fun Int.pluralSongs(): String = if (this <= 1) "$this titre" else "$this titres"
fun Int.pluralAlbums(): String = if (this <= 1) "$this album" else "$this albums"

/**
 * Normalise pour la recherche : minuscules, accents retirés.
 * `Édith Piaf` doit matcher `edith`.
 */
fun String.normalizeForSearch(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.getDefault())

/**
 * Score de correspondance floue : 0 = aucun match.
 * Un préfixe vaut plus qu'un mot interne, qui vaut plus qu'une sous-chaîne quelconque.
 */
fun fuzzyScore(haystack: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    val h = haystack.normalizeForSearch()
    val n = needle.normalizeForSearch()
    return when {
        h == n -> 1000
        h.startsWith(n) -> 800 - (h.length - n.length).coerceAtMost(200)
        h.split(' ', '-', '_', '(', ')').any { it.startsWith(n) } -> 600
        h.contains(n) -> 400
        subsequenceMatch(h, n) -> 150
        else -> 0
    }
}

/** Toutes les lettres de [needle] apparaissent dans l'ordre dans [haystack]. */
private fun subsequenceMatch(haystack: String, needle: String): Boolean {
    var i = 0
    for (c in haystack) {
        if (i < needle.length && c == needle[i]) i++
    }
    return i == needle.length
}
