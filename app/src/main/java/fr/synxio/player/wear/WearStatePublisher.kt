package fr.synxio.player.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.wear.protocol.WearPaths
import fr.synxio.player.wear.protocol.WearState
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Dépose l'état de lecture là où la montre le lit. Interface pour tester `WearBridge` sans Play Services. */
interface WearStatePublisher {
    /**
     * Publie [state]. [art] et [artBlurred] sont des JPEG prêts à envoyer, ou `null`
     * quand la pochette ne change pas depuis la dernière publication (l'ancienne reste
     * attachée côté montre) ou n'existe pas.
     */
    suspend fun publish(state: WearState, art: ByteArray?, artBlurred: ByteArray?)
}

/**
 * Implémentation Data Layer : un `DataItem` urgent sur [WearPaths.STATE].
 *
 * Les `Asset` ne transitent par Bluetooth que si leurs octets changent : le Data Layer
 * les compare par empreinte. Réattacher les mêmes octets ne coûte donc rien.
 */
@Singleton
class DataLayerStatePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WearStatePublisher {

    private var lastArt: ByteArray? = null
    private var lastArtBlurred: ByteArray? = null

    override suspend fun publish(state: WearState, art: ByteArray?, artBlurred: ByteArray?) {
        if (state.isEmpty) {
            lastArt = null
            lastArtBlurred = null
        } else {
            if (art != null) lastArt = art
            if (artBlurred != null) lastArtBlurred = artBlurred
        }
        val request = PutDataMapRequest.create(WearPaths.STATE).apply {
            dataMap.putString(WearPaths.KEY_STATE, state.toJson())
            lastArt?.let { dataMap.putAsset(WearPaths.KEY_ART, Asset.createFromBytes(it)) }
            lastArtBlurred?.let { dataMap.putAsset(WearPaths.KEY_ART_BLURRED, Asset.createFromBytes(it)) }
        }.asPutDataRequest().setUrgent()
        try {
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            // Play Services absents ou montre jamais appairée : la lecture continue sans montre.
            Log.w(TAG, "État non publié vers la montre", e)
        }
    }

    private companion object {
        const val TAG = "SynxioWear"
    }
}
