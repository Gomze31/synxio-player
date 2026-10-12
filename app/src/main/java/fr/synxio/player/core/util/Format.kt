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
 * Marques diacritiques combinantes, isolées par la décomposition NFD.
 *
 * Compilée une seule fois : la recompiler à chaque appel coûtait plus cher que la
 * normalisation elle-même, et la recherche l'appelle pour chaque morceau.
 */
private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

/** Séparateurs de mots reconnus par [fuzzyScore] pour le bonus « début de mot ». */
private const val WORD_SEPARATORS = " -_()"

/**
 * Normalise pour la recherche : minuscules, accents retirés, ligatures dépliées.
 * `Édith Piaf` doit matcher `edith`, `Cœur` doit matcher `coeur`, `Straße` `strasse`.
 *
 * `Locale.ROOT` et non la locale du téléphone : en turc, `I` deviendrait `ı` et
 * `IRIS` ne matcherait plus `iris`.
 */
fun String.normalizeForSearch(): String {
    if (isEmpty()) return this
    // Chemin rapide : l'immense majorité des titres sont en ASCII pur, inutile de
    // passer par Normalizer et la regex pour eux.
    if (all { it.code < 0x80 }) return lowercase(Locale.ROOT)
    val stripped = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
    // Œ, Æ et ẞ sont déjà passés en minuscules à ce stade.
    if (stripped.none { it == 'œ' || it == 'æ' || it == 'ß' }) return stripped
    return buildString(stripped.length + 4) {
        for (c in stripped) when (c) {
            'œ' -> append("oe")
            'æ' -> append("ae")
            'ß' -> append("ss")
            else -> append(c)
        }
    }
}

/**
 * Score de correspondance floue : 0 = aucun match.
 * Un préfixe vaut plus qu'un mot interne, qui vaut plus qu'une sous-chaîne quelconque.
 *
 * Normalise les deux chaînes à chaque appel : pour classer toute une bibliothèque,
 * préférer [fuzzyScoreNormalized] avec des clés normalisées une fois pour toutes.
 */
fun fuzzyScore(haystack: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    return fuzzyScoreNormalized(haystack.normalizeForSearch(), needle.normalizeForSearch())
}

/**
 * Variante de [fuzzyScore] pour des chaînes déjà passées par [normalizeForSearch].
 *
 * Même barème, sans aucune allocation : le découpage en mots se fait en parcourant
 * [h] plutôt qu'en créant une liste de sous-chaînes par morceau examiné.
 */
fun fuzzyScoreNormalized(h: String, n: String): Int {
    if (n.isEmpty()) return 0
    return when {
        h == n -> 1000
        h.startsWith(n) -> 800 - (h.length - n.length).coerceAtMost(200)
        wordStartMatch(h, n) -> 600
        h.contains(n) -> 400
        subsequenceMatch(h, n) -> 150
        else -> 0
    }
}

/**
 * Un mot de [haystack] commence par [needle].
 *
 * Équivaut à `haystack.split(séparateurs).any { it.startsWith(needle) }` : un mot ne
 * contient jamais de séparateur, donc une aiguille qui en contient ne peut pas matcher.
 */
private fun wordStartMatch(haystack: String, needle: String): Boolean {
    if (needle.any { it in WORD_SEPARATORS }) return false
    for (i in haystack.indices) {
        if ((i == 0 || haystack[i - 1] in WORD_SEPARATORS) && haystack.startsWith(needle, i)) {
            return true
        }
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

/**
 * Collator de tri pour la locale courante, insensible à la casse et aux accents.
 *
 * `Collator` n'est pas thread-safe : on en crée un par tri, ce qui reste négligeable
 * devant le tri lui-même. Avec `lowercase()`, « Édith » se rangeait après « Zazie ».
 */
fun newSortCollator(locale: Locale = Locale.getDefault()): java.text.Collator =
    java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.PRIMARY }
