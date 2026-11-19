package fr.synxio.player.wear

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.playback.art.ArtBitmaps
import fr.synxio.player.wear.protocol.WearPaths
import fr.synxio.player.widget.WidgetArtRenderer
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Pochette et accents prêts pour la montre. */
data class WearArt(
    val accent: Int,
    val accentLight: Int,
    /** JPEG carré de [WearPaths.ART_PIXELS] px, ou `null` sans pochette. */
    val art: ByteArray?,
    /** Même image, floutée, pour le fond du lecteur. */
    val artBlurred: ByteArray?,
) {
    companion object {
        val NONE = WearArt(accent = 0, accentLight = 0, art = null, artBlurred = null)
    }
}

/** Interface pour tester `WearBridge` sans décoder d'image. */
interface WearArtPreparer {
    /** [artUri] : URI de la pochette, ou `null`. [key] identifie la pochette pour le cache des accents. */
    fun prepare(artUri: String?, key: String): WearArt
}

/**
 * Implémentation réelle : accents via [WidgetArtRenderer] (déjà calculés et mis en cache
 * pour les widgets), JPEG 160 px via [ArtBitmaps], flou par réduction à 16 px puis
 * remontée filtrée.
 */
@Singleton
class BitmapWearArtPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: WidgetArtRenderer,
) : WearArtPreparer {

    override fun prepare(artUri: String?, key: String): WearArt {
        val uri = artUri?.takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val prepared = runCatching { renderer.prepare(uri, key) }.getOrNull()
        val source = uri?.let { ArtBitmaps.decode(context, it, WearPaths.ART_PIXELS) }
        val square = source?.let { squared(it, WearPaths.ART_PIXELS) }
        return WearArt(
            accent = prepared?.accentDark ?: 0,
            accentLight = prepared?.accentLight ?: 0,
            art = square?.let(::jpeg),
            artBlurred = square?.let { jpeg(blurred(it)) },
        )
    }

    private fun squared(src: Bitmap, side: Int): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        val cropped = Bitmap.createBitmap(src, x, y, size, size)
        return if (size == side) cropped else Bitmap.createScaledBitmap(cropped, side, side, true)
    }

    private fun blurred(square: Bitmap): Bitmap {
        val tiny = Bitmap.createScaledBitmap(square, BLUR_PIXELS, BLUR_PIXELS, true)
        return Bitmap.createScaledBitmap(tiny, square.width, square.height, true)
    }

    private fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }

    private companion object {
        const val BLUR_PIXELS = 16
        const val JPEG_QUALITY = 80
    }
}
