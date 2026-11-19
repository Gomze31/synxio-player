package fr.synxio.player.watch.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Reçoit chaque nouvel état du téléphone, même appli fermée, et le range dans le store.
 *
 * `onDataChanged` tourne sur un thread de Play Services : la lecture des `Asset` y est
 * bloquante par conception (le service est libéré au retour).
 */
@AndroidEntryPoint
class WearStateListener : WearableListenerService() {

    @Inject lateinit var store: WearStateStore

    override fun onDataChanged(events: DataEventBuffer) {
        val reader = DataLayerStateReader(this, store)
        var changed = false
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            runBlocking { reader.apply(event.dataItem) }
            changed = true
        }
        if (changed) WearSurfaces.refreshAll(this)
    }
}
