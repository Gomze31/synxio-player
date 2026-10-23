package fr.synxio.player.core.util

import java.text.Normalizer
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
 * Compilée une seule fois : la recherche appelle [normalizeForSearch] sur chaque titre,
 * artiste et album de la bibliothèque à chaque frappe. Recompiler l'expression à chaque
 * appel coûtait plus cher que la normalisation elle-même.
 */
private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

/**
 * Normalise pour la recherche : minuscules, accents retirés.
 * `Édith Piaf` doit matcher `edith`.
 *
 * Une chaîne purement ASCII — l'immense majorité des titres — n'a pas d'accent à
 * retirer : on se contente alors de la passer en minuscules, sans décomposition Unicode.
 */
fun String.normalizeForSearch(): String {
    if (isAscii()) return lowercase(Locale.ROOT)
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
}

private fun String.isAscii(): Boolean {
    for (c in this) if (c.code > 0x7F) return false
    return true
}

/**
 * Score de correspondance floue : 0 = aucun match.
 * Un préfixe vaut plus qu'un mot interne, qui vaut plus qu'une sous-chaîne quelconque.
 */
fun fuzzyScore(haystack: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    return fuzzyScoreNormalized(haystack.normalizeForSearch(), needle.normalizeForSearch())
}

/**
 * Variante pour une requête déjà normalisée.
 *
 * La recherche compare une même requête à des milliers de chaînes : la normaliser
 * une fois en amont, plutôt qu'à chaque comparaison, divise le travail par deux.
 */
fun fuzzyScoreNormalized(haystack: String, normalizedNeedle: String): Int {
    val n = normalizedNeedle
    if (n.isEmpty()) return 0
    val h = haystack.normalizeForSearch()
    return when {
        h == n -> 1000
        h.startsWith(n) -> 800 - (h.length - n.length).coerceAtMost(200)
        wordStartsWith(h, n) -> 600
        h.contains(n) -> 400
        subsequenceMatch(h, n) -> 150
        else -> 0
    }
}

/** Un mot de [haystack] (séparé par espace, tiret, soulignement ou parenthèse) commence par [needle]. */
private fun wordStartsWith(haystack: String, needle: String): Boolean {
    var wordStart = true
    for (i in haystack.indices) {
        val c = haystack[i]
        if (wordStart && haystack.startsWith(needle, i)) return true
        wordStart = c == ' ' || c == '-' || c == '_' || c == '(' || c == ')'
    }
    return false
}

/** Toutes les lettres de [needle] apparaissent dans l'ordre dans [haystack]. */
private fun subsequenceMatch(haystack: String, needle: String): Boolean {
    var i = 0
    for (c in haystack) {
        if (i < needle.length && c == needle[i]) i++
    }
    return i == needle.length
}
