package fr.synxio.player.data.discord

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Liaison au service RPC du client Discord Android.
 *
 * ## Le transport, établi par observation
 *
 * Discord annonce depuis le SDK Social 1.10 un « transport RPC non authentifié » sur
 * Android sans en documenter le point d'entrée. Trois hypothèses ont été confrontées à un
 * appareil réel, client Discord 344.13 en cours d'exécution :
 *
 *  - socket locale abstraite `discord-ipc-N`, comme sur bureau → **absente** ;
 *  - port TCP local dans la plage 6463-6472 du RPC bureau → **aucun** ;
 *  - service lié Android → **trouvé**, exporté sans permission.
 *
 * ## Ce que cette implémentation a d'officieux
 *
 * Les méthodes d'un binder s'adressent par **code de transaction positionnel**. Discord
 * livre la définition AIDL avec son SDK, que nous n'avons pas : les codes utilisés ici
 * viennent de l'observation du service et de travaux communautaires, pas d'une source
 * officielle.
 *
 * Conséquence assumée : une réorganisation de l'interface côté Discord casserait la
 * publication. L'échec est cependant sans gravité — [connect] renvoie `false`, l'écran
 * l'annonce, et la lecture n'est pas affectée. Aucune donnée n'est en jeu.
 *
 * Aucun jeton de compte n'intervient : seul l'identifiant d'application est transmis. On
 * ne tombe donc pas sous l'interdiction des self-bots.
 */
class DiscordRpcClient(
    private val context: Context,
    private val applicationId: Long,
) {

    /** Binder du service, obtenu à la liaison. */
    private var service: IBinder? = null

    /** Binder de la connexion, retourné par la transaction de poignée de main. */
    private var connection: IBinder? = null

    private var serviceConnection: ServiceConnection? = null

    @Volatile
    private var ready = false

    @Volatile
    var lastError: String? = null
        private set

    val isReady: Boolean get() = ready && connection?.isBinderAlive == true

    /**
     * Reçoit les trames de Discord : `READY` à l'établissement, `ERROR` en cas de refus.
     *
     * Discord appelle ce binder ; il doit donc répondre correctement à
     * `INTERFACE_TRANSACTION`, sinon le service considère l'interface invalide.
     */
    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(CALLBACK_DESCRIPTOR)
                    return true
                }

                TX_FRAME -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    handleFrame(data.readString())
                    reply?.writeNoException()
                    return true
                }

                TX_CLOSE -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val closeCode = data.readInt()
                    val message = data.readString().orEmpty()
                    ready = false
                    connection = null
                    lastError = "Discord a fermé la connexion ($closeCode) $message".trim()
                    Log.w(TAG, lastError!!)
                    reply?.writeNoException()
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    /** Se lie au service puis effectue la poignée de main. */
    suspend fun connect(): Boolean {
        if (isReady) return true

        val binder = bindService() ?: return false
        service = binder

        val handshake = runCatching { handshake(binder) }.getOrElse {
            lastError = "Poignée de main refusée : ${it.message}"
            Log.w(TAG, lastError!!, it)
            null
        }

        if (handshake == null) {
            disconnect()
            return false
        }

        connection = handshake
        // READY arrive de façon asynchrone par le callback : on l'attend pour ne pas
        // annoncer une liaison opérationnelle qui ne l'est pas encore.
        val ok = withTimeoutOrNull(READY_TIMEOUT_MS) {
            while (!ready && lastError == null) kotlinx.coroutines.delay(50)
            ready
        } ?: false

        if (!ok) {
            Log.w(TAG, "Pas de READY : ${lastError ?: "délai dépassé"}")
            disconnect()
            return false
        }

        Log.i(TAG, "RPC Discord opérationnel")
        return true
    }

    private suspend fun bindService(): IBinder? {
        val intent = Intent(SERVICE_ACTION).setPackage(DISCORD_PACKAGE)
        val ready = CompletableDeferred<IBinder?>()

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                ready.complete(binder)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                this@DiscordRpcClient.ready = false
                connection = null
                service = null
            }

            /** Le service existe mais refuse la liaison : distinct d'une absence. */
            override fun onNullBinding(name: ComponentName?) {
                ready.complete(null)
            }
        }

        val requested = runCatching {
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }.getOrElse {
            lastError = "bindService impossible : ${it.message}"
            false
        }

        if (!requested) {
            runCatching { context.unbindService(conn) }
            lastError = lastError ?: "Service RPC Discord introuvable"
            return null
        }

        serviceConnection = conn
        val binder = withTimeoutOrNull(BIND_TIMEOUT_MS) { ready.await() }
        if (binder == null) lastError = "Liaison au service Discord sans réponse"
        return binder
    }

    /**
     * Transaction de poignée de main : identifiant d'application, version du protocole,
     * et le binder par lequel Discord nous répondra. Retourne le binder de connexion.
     */
    private fun handshake(binder: IBinder): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.writeLong(applicationId)
            data.writeString(PROTOCOL_VERSION)
            data.writeStrongBinder(callback)
            if (!binder.transact(TX_FRAME, data, reply, 0)) {
                error("Discord a rejeté la transaction de connexion")
            }
            reply.readException()
            reply.readStrongBinder()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Envoie une trame RPC. Le JSON est celui du RPC de bureau. */
    fun sendFrame(frame: String): Boolean {
        val target = connection ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CONNECTION_DESCRIPTOR)
            data.writeString(frame)
            if (!target.transact(TX_FRAME, data, reply, 0)) {
                error("Discord a rejeté la trame")
            }
            reply.readException()
            true
        } catch (error: Throwable) {
            connection = null
            ready = false
            lastError = "Envoi impossible : ${error.message}"
            Log.w(TAG, lastError!!, error)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun disconnect() {
        connection?.let { target ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            runCatching {
                data.writeInterfaceToken(CONNECTION_DESCRIPTOR)
                target.transact(TX_CLOSE, data, reply, 0)
                reply.readException()
            }
            reply.recycle()
            data.recycle()
        }
        serviceConnection?.let { conn -> runCatching { context.unbindService(conn) } }
        serviceConnection = null
        connection = null
        service = null
        ready = false
    }

    private fun handleFrame(frame: String?) {
        if (frame == null) return
        runCatching {
            val json = JSONObject(frame)
            when (json.optString("evt")) {
                "READY" -> {
                    ready = true
                    lastError = null
                    Log.i(TAG, "READY reçu de Discord")
                }

                "ERROR" -> {
                    val details = json.optJSONObject("data")
                    val code = details?.optString("code").orEmpty()
                    val message = details?.optString("message").orEmpty()
                    ready = false
                    lastError = "Discord a refusé : $code $message".trim()
                    Log.w(TAG, lastError!!)
                }

                else -> Log.d(TAG, "Trame Discord : $frame")
            }
        }.onFailure { Log.w(TAG, "Trame illisible : $frame", it) }
    }

    private companion object {
        const val TAG = "DiscordRpc"
        const val DISCORD_PACKAGE = "com.discord"
        const val SERVICE_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
        const val SERVICE_DESCRIPTOR = "com.discord.socialsdk.rpc.IDiscordRpcService"
        const val CONNECTION_DESCRIPTOR = "com.discord.socialsdk.rpc.IDiscordRpcConnection"
        const val CALLBACK_DESCRIPTOR = "com.discord.socialsdk.rpc.IDiscordRpcCallback"

        /** Première méthode de chaque interface : connexion, trame, ou trame entrante. */
        const val TX_FRAME = 1
        /** Seconde méthode : fermeture. */
        const val TX_CLOSE = 2

        const val PROTOCOL_VERSION = "1"
        const val BIND_TIMEOUT_MS = 5_000L
        const val READY_TIMEOUT_MS = 5_000L
    }
}
