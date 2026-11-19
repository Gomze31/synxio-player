package fr.synxio.player.watch.data

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

enum class Connectivity { UNKNOWN, PHONE_REACHABLE, PHONE_UNREACHABLE, PLAY_SERVICES_MISSING }

/** Le téléphone vu de la montre : est-il joignable, et comment lui parler. */
interface PhoneLink {
    val connectivity: StateFlow<Connectivity>
    /** Relit la capacité et le `DataItem` courant. À appeler au premier plan. */
    suspend fun refresh()
    suspend fun send(command: WearCommand): Result<Unit>
}

class DataLayerPhoneLink(
    private val context: Context,
    private val reader: DataLayerStateReader,
) : PhoneLink {

    private val _connectivity = MutableStateFlow(Connectivity.UNKNOWN)
    override val connectivity: StateFlow<Connectivity> = _connectivity.asStateFlow()

    private var phoneNodeId: String? = null
    private var listening = false

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info -> update(info) }

    override suspend fun refresh() {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            _connectivity.value = Connectivity.PLAY_SERVICES_MISSING
            return
        }
        try {
            val client = Wearable.getCapabilityClient(context)
            update(client.getCapability(WearPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await())
            if (!listening) {
                client.addListener(capabilityListener, WearPaths.CAPABILITY_PHONE)
                listening = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Capacité du téléphone illisible", e)
            _connectivity.value = Connectivity.PHONE_UNREACHABLE
        }
        reader.refreshFromDataLayer()
    }

    private fun update(info: CapabilityInfo) {
        val node = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
        phoneNodeId = node?.id
        _connectivity.value = if (node != null) Connectivity.PHONE_REACHABLE else Connectivity.PHONE_UNREACHABLE
    }

    override suspend fun send(command: WearCommand): Result<Unit> {
        val node = phoneNodeId ?: return Result.failure(IllegalStateException("Aucun téléphone joignable"))
        return runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(node, WearPaths.COMMAND, command.encode().toByteArray(Charsets.UTF_8))
                .await()
            Unit
        }.onFailure { Log.w(TAG, "Commande non envoyée : $command", it) }
    }

    private companion object {
        const val TAG = "SynxioWear"
    }
}
