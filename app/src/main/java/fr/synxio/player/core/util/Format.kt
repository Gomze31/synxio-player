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
        else -> tokenScore(h, n).takeIf { it > 0 }
            ?: if (subsequenceMatch(h, n)) 150 else 0
    }
}

/**
 * Chaque mot de la requête se retrouve dans [h], dans n'importe quel ordre, au besoin
 * avec une faute de frappe : « season stick », « kendij girac », « stik ».
 *
 * 300 si tous les mots sont présents tels quels, moins 40 par mot corrigé ; 0 si un mot
 * reste introuvable. Les mots courts (moins de 4 lettres) doivent être exacts : tolérer
 * une faute sur « le » ferait tout correspondre.
 */
private fun tokenScore(h: String, n: String): Int {
    var typos = 0
    var tokens = 0
    var start = 0
    while (start < n.length) {
        while (start < n.length && n[start] in WORD_SEPARATORS) start++
        if (start >= n.length) break
        var end = start
        while (end < n.length && n[end] !in WORD_SEPARATORS) end++
        val token = n.substring(start, end)
        start = end
        tokens++
        if (h.contains(token)) continue
        if (token.length < 4 || !hasWordWithinTypo(h, token)) return 0
        typos++
    }
    if (tokens == 0) return 0
    return (300 - 40 * typos).coerceAtLeast(160)
}

/**
 * Un mot de [h] (ou son début, pour une saisie en cours) est à une ou deux fautes de
 * [token] : lettre remplacée, ajoutée, oubliée ou deux lettres inversées.
 */
private fun hasWordWithinTypo(h: String, token: String): Boolean {
    val allowed = if (token.length >= 8) 2 else 1
    var i = 0
    while (i < h.length) {
        while (i < h.length && h[i] in WORD_SEPARATORS) i++
        if (i >= h.length) break
        var j = i
        while (j < h.length && h[j] !in WORD_SEPARATORS) j++
        val wordLength = j - i
        if (wordLength >= token.length - allowed) {
            // Mot entier, ou son préfixe de la longueur de la saisie (« kendj » → « kendji »).
            val full = h.substring(i, j)
            if (osaDistance(token, full, allowed) <= allowed) return true
            if (wordLength > token.length &&
                osaDistance(token, full.substring(0, token.length), allowed) <= allowed
            ) return true
        }
        i = j
    }
    return false
}

/**
 * Distance d'édition « optimal string alignment » (Damerau restreint), arrêtée dès
 * qu'elle dépasse [limit] : seule compte la réponse « proche ou pas ».
 */
internal fun osaDistance(a: String, b: String, limit: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
    val cols = b.length + 1
    var prevPrev = IntArray(cols)
    var prev = IntArray(cols) { it }
    var cur = IntArray(cols)
    for (i in 1..a.length) {
        cur[0] = i
        var rowMin = cur[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                v = minOf(v, prevPrev[j - 2] + 1)
            }
            cur[j] = v
            if (v < rowMin) rowMin = v
        }
        if (rowMin > limit) return limit + 1
        val tmp = prevPrev
        prevPrev = prev
        prev = cur
        cur = tmp
    }
    return prev[b.length]
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

/**
 * Toutes les lettres de [needle] apparaissent dans l'ordre dans [haystack], groupées :
 * l'écart entre la première et la dernière ne dépasse pas deux fois la saisie.
 *
 * Sans cette fenêtre, « kendij » trouvait « Take mE home couNtry roaDs… » : des lettres
 * éparpillées sur tout le titre ne sont pas une faute de frappe.
 */
private fun subsequenceMatch(haystack: String, needle: String): Boolean {
    if (needle.isEmpty()) return true
    val window = needle.length * 2 + 1
    var start = haystack.indexOf(needle[0])
    while (start >= 0) {
        var i = 0
        var pos = start
        val limit = minOf(haystack.length, start + window)
        while (pos < limit && i < needle.length) {
            if (haystack[pos] == needle[i]) i++
            pos++
        }
        if (i == needle.length) return true
        start = haystack.indexOf(needle[0], start + 1)
    }
    return false
}

/**
 * Collator de tri pour la locale courante, insensible à la casse et aux accents.
 *
 * `Collator` n'est pas thread-safe : on en crée un par tri, ce qui reste négligeable
 * devant le tri lui-même. Avec `lowercase()`, « Édith » se rangeait après « Zazie ».
 */
fun newSortCollator(locale: Locale = Locale.getDefault()): java.text.Collator =
    java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.PRIMARY }
