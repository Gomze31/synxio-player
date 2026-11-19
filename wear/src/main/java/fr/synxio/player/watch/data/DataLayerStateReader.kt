package fr.synxio.player.watch.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import fr.synxio.player.wear.protocol.WearPaths
import fr.synxio.player.wear.protocol.WearState
import kotlinx.coroutines.tasks.await

/**
 * Traduit le `DataItem` `/synxio/state` en [WearStateStore].
 *
 * Les `Asset` ne sont lus que lorsque le titre change : le téléphone les réattache à
 * chaque publication, mais leurs octets sont déjà dans le store.
 */
class DataLayerStateReader(
    private val context: Context,
    private val store: WearStateStore,
) {
    private val dataClient: DataClient get() = Wearable.getDataClient(context)

    suspend fun apply(item: DataItem) {
        if (item.uri.path != WearPaths.STATE) return
        val map = DataMapItem.fromDataItem(item).dataMap
        val state = map.getString(WearPaths.KEY_STATE)?.let(WearState::fromJson) ?: return
        Log.d(TAG, "État reçu : ${state.title} lecture=${state.isPlaying} v=${state.protocolVersion}")
        val needArt = !state.isEmpty && (store.current().state?.mediaId != state.mediaId || store.current().art == null)
        val art = if (needArt) map.getAsset(WearPaths.KEY_ART)?.let { read(it) } else null
        val blurred = if (needArt) map.getAsset(WearPaths.KEY_ART_BLURRED)?.let { read(it) } else null
        store.save(state, art, blurred)
    }

    /** Relit le `DataItem` courant (démarrage de l'appli, listener manqué). */
    suspend fun refreshFromDataLayer() {
        try {
            val buffer = dataClient.getDataItems(Uri.parse("wear://*${WearPaths.STATE}"), DataClient.FILTER_LITERAL).await()
            try {
                Log.d(TAG, "DataItems ${WearPaths.STATE} : ${buffer.count}")
                // Un seul téléphone appairé : le dernier item publié gagne.
                buffer.lastOrNull()?.let { apply(it) }
            } finally {
                buffer.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "État du téléphone illisible", e)
        }
    }

    private suspend fun read(asset: Asset): ByteArray? = try {
        dataClient.getFdForAsset(asset).await().inputStream.use { it.readBytes() }
    } catch (e: Exception) {
        Log.w(TAG, "Pochette illisible", e)
        null
    }

    private companion object {
        const val TAG = "SynxioWear"
    }
}
