package fr.synxio.player.data.repo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.palette.graphics.Palette
import fr.synxio.player.data.db.ArtworkColorDao
import fr.synxio.player.data.db.ArtworkColorEntity
import fr.synxio.player.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour gérer le cache des couleurs extraites des pochettes.
 */
@Singleton
class ArtworkColorRepository @Inject constructor(
    private val artworkColorDao: ArtworkColorDao,
    @ApplicationContext private val context: android.content.Context,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val memoryCache = mutableMapOf<String, PaletteColors>()

    fun getColors(artworkUri: Uri?): Flow<PaletteColors?> {
        if (artworkUri == null) return flowOf(null)
        
        val uriString = artworkUri.toString()
        
        if (uriString in memoryCache) {
            return flowOf(memoryCache[uriString])
        }
        
        return flow {
            val cachedEntity = withContext(Dispatchers.IO) {
                artworkColorDao.get(uriString)
            }
            
            if (cachedEntity != null) {
                val colors = PaletteColors(
                    primary = cachedEntity.primaryColor,
                    secondary = cachedEntity.secondaryColor,
                    background = cachedEntity.backgroundColor,
                    onPrimary = cachedEntity.onPrimaryColor,
                    onSecondary = cachedEntity.onSecondaryColor,
                    onBackground = cachedEntity.onBackgroundColor
                )
                memoryCache[uriString] = colors
                emit(colors)
                return@flow
            }
            
            val colors = withContext(Dispatchers.IO) {
                extractColors(artworkUri)
            }
            
            colors?.let { paletteColors ->
                memoryCache[uriString] = paletteColors
                artworkColorDao.put(
                    ArtworkColorEntity(
                        artworkUri = uriString,
                        primaryColor = paletteColors.primary,
                        secondaryColor = paletteColors.secondary,
                        backgroundColor = paletteColors.background,
                        onPrimaryColor = paletteColors.onPrimary,
                        onSecondaryColor = paletteColors.onSecondary,
                        onBackgroundColor = paletteColors.onBackground
                    )
                )
            }
            
            emit(colors)
        }
    }

    private suspend fun extractColors(uri: Uri): PaletteColors? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = getBitmapFromUri(uri) ?: return@runCatching null
            extractColorsFromBitmap(bitmap)
        }.getOrNull()
    }

    private fun extractColorsFromBitmap(bitmap: Bitmap): PaletteColors {
        val palette = Palette.from(bitmap).generate()
        
        val primary = palette.getDominantColor(AndroidColor.parseColor("#673AB7"))
        val secondary = palette.getVibrantColor(primary) ?: primary
        val background = palette.getLightMutedColor(AndroidColor.WHITE) ?: AndroidColor.WHITE
        
        val onPrimary = getContrastColor(primary)
        val onSecondary = getContrastColor(secondary)
        val onBackground = getContrastColor(background)
        
        return PaletteColors(
            primary = primary,
            secondary = secondary,
            background = background,
            onPrimary = onPrimary,
            onSecondary = onSecondary,
            onBackground = onBackground
        )
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getContrastColor(color: Int): Int {
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return if (luminance > 0.5) AndroidColor.BLACK else AndroidColor.WHITE
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        memoryCache.clear()
        artworkColorDao.clear()
    }

    suspend fun clearColor(uri: Uri) = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        memoryCache.remove(uriString)
        artworkColorDao.delete(uriString)
    }

    suspend fun cleanupUnusedColors(usedUris: Set<String>) = withContext(Dispatchers.IO) {
        val cachedUris = memoryCache.keys + artworkColorDao.getAllUris()
        val toRemove = cachedUris - usedUris
        toRemove.forEach { uri ->
            memoryCache.remove(uri)
            artworkColorDao.delete(uri)
        }
    }

    suspend fun getAllCachedUris(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            (memoryCache.keys + artworkColorDao.getAllUris()).toSet()
        }.getOrDefault(emptySet())
    }

    data class PaletteColors(
        val primary: Int,
        val secondary: Int,
        val background: Int,
        val onPrimary: Int,
        val onSecondary: Int,
        val onBackground: Int
    )
}
