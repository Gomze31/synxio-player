package fr.synxio.player.data.discord

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Liaison au service RPC du client Discord Android.
 *
 * ## Le transport, établi par observation
 *
 * Discord annonce depuis le SDK Social 1.10 un « transport RPC non authentifié » sur
 * Android sans en documenter le point d'entrée. Trois hypothèses ont été confrontées à
 * un appareil réel, client Discord 344.13 en cours d'exécution :
 *
 *  - socket locale abstraite `discord-ipc-N`, comme sur bureau → **absente** ;
 *  - port TCP local dans la plage 6463-6472 du RPC bureau → **aucun** ;
 *  - service lié Android → **trouvé**, et c'est celui-ci.
 *
 * ```
 * com.discord.socialsdk.rpc.IDiscordRpcService
 *   → com.discord/.socialrpc.DiscordRpcService
 * ```
 *
 * Le service est exporté sans permission : n'importe quelle application peut s'y lier.
 *
 * ## Ce qui manque encore
 *
 * Se lier au service donne un [IBinder], mais l'appeler demande la définition AIDL :
 * les méthodes d'un binder sont adressées par **code de transaction positionnel**, pas
 * par nom. Cette définition est livrée avec le SDK officiel de Discord.
 *
 * L'extraire de l'APK de Discord serait possible mais fragile : les codes de transaction
 * changent au gré des versions, et une mise à jour de Discord casserait la fonctionnalité
 * sans le moindre signal. [transact] reste donc à implémenter avec l'AIDL officiel.
 */
class DiscordRpcClient(private val context: Context) {

    private var binder: IBinder? = null
    private var connection: ServiceConnection? = null

    val isBound: Boolean get() = binder?.isBinderAlive == true

    /** Descripteur annoncé par le service, une fois lié. Sert à vérifier l'interface. */
    var interfaceDescriptor: String? = null
        private set

    /**
     * Se lie au service RPC de Discord.
     *
     * Renvoie `false` si Discord n'est pas installé, si le service a disparu d'une
     * version à l'autre, ou si la liaison n'aboutit pas dans le délai imparti.
     */
    suspend fun bind(): Boolean {
        if (isBound) return true

        val intent = Intent(RPC_ACTION).setPackage(DISCORD_PACKAGE)
        val ready = CompletableDeferred<IBinder?>()

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                ready.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
                interfaceDescriptor = null
            }

            /** Le service existe mais refuse la liaison : à distinguer d'une absence. */
            override fun onNullBinding(name: ComponentName?) {
                Log.w(TAG, "Service Discord lié mais sans binder")
                ready.complete(null)
            }
        }

        val requested = runCatching {
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }.getOrElse {
            Log.w(TAG, "bindService a échoué — la déclaration <queries> est-elle présente ?", it)
            false
        }

        if (!requested) {
            runCatching { context.unbindService(conn) }
            Log.i(TAG, "Service RPC Discord introuvable")
            return false
        }

        val service = withTimeoutOrNull(BIND_TIMEOUT_MS) { ready.await() }
        if (service == null) {
            runCatching { context.unbindService(conn) }
            Log.i(TAG, "Liaison au service Discord sans réponse")
            return false
        }

        binder = service
        connection = conn
        interfaceDescriptor = runCatching { service.interfaceDescriptor }.getOrNull()
        Log.i(TAG, "Lié au service Discord, interface = $interfaceDescriptor")
        return true
    }

    fun unbind() {
        connection?.let { conn -> runCatching { context.unbindService(conn) } }
        connection = null
        binder = null
        interfaceDescriptor = null
    }

    private companion object {
        const val TAG = "DiscordRpc"
        const val DISCORD_PACKAGE = "com.discord"
        const val RPC_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
        const val BIND_TIMEOUT_MS = 5_000L
    }
}
