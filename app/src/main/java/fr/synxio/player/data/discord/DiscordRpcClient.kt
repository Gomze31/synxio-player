package fr.synxio.player.data.discord

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Client RPC local du client Discord Android.
 *
 * ## Le protocole
 *
 * Identique au RPC de bureau, documenté par Discord : des trames composées d'un en-tête
 * de huit octets — opcode puis longueur, entiers 32 bits **petit-boutiste** — suivi d'une
 * charge utile JSON.
 *
 * ```
 * [4 o : opcode] [4 o : longueur] [JSON]
 * ```
 *
 * Seul un identifiant d'application est nécessaire. Aucun jeton de compte n'entre en jeu,
 * donc aucune automatisation de compte : on ne tombe pas sous l'interdiction des self-bots.
 *
 * ## Le transport, et ce qu'il a d'incertain
 *
 * Discord annonce depuis le SDK 1.10 un « transport RPC non authentifié équivalent à celui
 * du bureau » sur Android, mais ne documente pas le point d'entrée. Sur bureau, c'est une
 * socket nommée (`discord-ipc-0`). L'équivalent Android est une socket locale en espace de
 * noms **abstrait**, et c'est ce que tente cette implémentation.
 *
 * C'est une hypothèse, pas un fait établi : si elle est fausse, [connect] renvoie `false`
 * sans rien casser, et il faudra passer par le SDK natif de Discord.
 */
class DiscordRpcClient(private val applicationId: String) {

    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    /**
     * Ouvre la socket et effectue la poignée de main.
     *
     * Plusieurs clients Discord peuvent coexister, numérotés de 0 à 9 sur bureau : on
     * balaie la même plage plutôt que de supposer le premier libre.
     */
    fun connect(): Boolean {
        if (isConnected) return true

        for (index in 0 until MAX_SOCKETS) {
            val name = "$SOCKET_PREFIX$index"
            val candidate = runCatching {
                LocalSocket().apply {
                    connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                }
            }.getOrNull() ?: continue

            socket = candidate
            input = DataInputStream(candidate.inputStream)

            val handshake = JSONObject().apply {
                put("v", RPC_VERSION)
                put("client_id", applicationId)
            }

            if (send(OP_HANDSHAKE, handshake) && awaitReady()) {
                Log.i(TAG, "Connecté au client Discord via $name")
                return true
            }

            close()
        }

        Log.i(TAG, "Aucune socket RPC Discord atteignable")
        return false
    }

    /** Publie l'activité. `null` efface le statut. */
    fun setActivity(activity: JSONObject?): Boolean {
        val args = JSONObject().apply {
            // Discord identifie l'émetteur par son pid, comme sur bureau.
            put("pid", Process.myPid())
            if (activity != null) put("activity", activity) else put("activity", JSONObject.NULL)
        }
        val frame = JSONObject().apply {
            put("cmd", "SET_ACTIVITY")
            put("nonce", UUID.randomUUID().toString())
            put("args", args)
        }
        return send(OP_FRAME, frame)
    }

    fun close() {
        runCatching { input?.close() }
        runCatching { socket?.close() }
        input = null
        socket = null
    }

    // --- Trames ------------------------------------------------------------------------

    private fun send(opcode: Int, payload: JSONObject): Boolean {
        val out = socket?.outputStream ?: return false
        val body = payload.toString().toByteArray(Charsets.UTF_8)

        // Petit-boutiste explicite : l'ordre natif d'ARM l'est déjà, mais s'en remettre
        // au hasard de l'architecture rendrait le format dépendant de l'appareil.
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode)
            .putInt(body.size)
            .array()

        return runCatching {
            out.write(header)
            out.write(body)
            out.flush()
            true
        }.getOrElse {
            Log.w(TAG, "Écriture RPC impossible", it)
            close()
            false
        }
    }

    /** Attend la réponse à la poignée de main : un événement READY. */
    private fun awaitReady(): Boolean = runCatching {
        val response = readFrame() ?: return false
        response.optString("evt") == "READY"
    }.getOrElse {
        Log.w(TAG, "Poignée de main sans réponse exploitable", it)
        false
    }

    private fun readFrame(): JSONObject? {
        val stream = input ?: return null
        val header = ByteArray(HEADER_SIZE)
        stream.readFully(header)

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.int // opcode, non utilisé ici
        val length = buffer.int
        if (length <= 0 || length > MAX_FRAME_BYTES) throw IOException("Trame de taille $length")

        val body = ByteArray(length)
        stream.readFully(body)
        return JSONObject(String(body, Charsets.UTF_8))
    }

    private companion object {
        const val TAG = "DiscordRpc"
        const val SOCKET_PREFIX = "discord-ipc-"
        const val MAX_SOCKETS = 10
        const val RPC_VERSION = 1
        const val OP_HANDSHAKE = 0
        const val OP_FRAME = 1
        const val HEADER_SIZE = 8
        /** Garde-fou : une trame RPC légitime est de l'ordre du kilo-octet. */
        const val MAX_FRAME_BYTES = 64 * 1024
    }
}
