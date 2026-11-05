package fr.synxio.player.playback.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** Décodage réduit et recadrage carré, partagés par le widget, le fournisseur de pochettes et la mosaïque. */
object ArtBitmaps {

    /** Décode en réduisant avant lecture : jamais plus de deux fois [target] de côté. Null si illisible. */
    fun decode(context: Context, uri: Uri, target: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    /** Carré central de [src], ramené à [side] de côté s'il est plus grand. */
    fun centerCrop(src: Bitmap, side: Int): Bitmap {
        val min = minOf(src.width, src.height)
        val x = (src.width - min) / 2
        val y = (src.height - min) / 2
        val cropped = Bitmap.createBitmap(src, x, y, min, min)
        if (min <= side) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, side, side, true)
        if (cropped !== src) cropped.recycle()
        return scaled
    }
}
