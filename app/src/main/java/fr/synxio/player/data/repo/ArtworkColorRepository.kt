package fr.synxio.player.data.repo

import android.net.Uri
import fr.synxio.player.data.db.ArtworkColorDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache persistant des couleurs de pochettes (table `artwork_colors`).
 *
 * L'extraction à la volée qui vivait ici n'avait plus d'appelant : les couleurs de
 * l'interface viennent de `ui/theme/ArtworkColors.kt` (décodage 128 px via Coil), celles
 * des widgets de `WidgetArtRenderer`, celles des cartes de partage de
 * `ShareCardRepository`, toutes sur des bitmaps sous-échantillonnés (`ArtBitmaps`). La
 * table est conservée : les couleurs persistantes par titre reviennent avec l'étape 5
 * (empreinte sonore 2) ; le réglage « Vider le cache des couleurs » la vide.
 */
@Singleton
class ArtworkColorRepository @Inject constructor(
    private val artworkColorDao: ArtworkColorDao,
) {

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        artworkColorDao.clear()
    }

    suspend fun clearColor(uri: Uri) = withContext(Dispatchers.IO) {
        artworkColorDao.delete(uri.toString())
    }

    suspend fun cleanupUnusedColors(usedUris: Set<String>) = withContext(Dispatchers.IO) {
        (artworkColorDao.getAllUris().toSet() - usedUris).forEach { artworkColorDao.delete(it) }
    }

    suspend fun getAllCachedUris(): Set<String> = withContext(Dispatchers.IO) {
        runCatching { artworkColorDao.getAllUris().toSet() }.getOrDefault(emptySet())
    }
}
