package fr.synxio.player.data.repo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Décode une pochette réduite : son plus grand côté reste au moins égal à [targetPx],
 * sans jamais dépasser le double.
 *
 * Deux passes : la première ne lit que les dimensions, la seconde décode en
 * sous-échantillonnant. Décoder une pochette 1500×1500 en pleine taille alloue neuf
 * mégaoctets pour en extraire quelques couleurs ou la réduire aussitôt ; répété à chaque
 * changement de piste, c'est une pression mémoire inutile et des saccades.
 *
 * Le facteur reste une puissance de deux : c'est le seul que `inSampleSize` honore
 * exactement, d'où la borne « au plus le double » plutôt qu'une taille précise.
 *
 * Retourne null si l'image est illisible.
 */
fun ContentResolver.decodeSampledArtwork(uri: Uri, targetPx: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / (sample * 2) >= targetPx) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()
