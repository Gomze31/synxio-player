package fr.synxio.player.data.discord

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.BuildConfig
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Ce que l'application sait de sa liaison au client Discord. */
enum class PresenceStatus {
    /** Jamais tenté, ou désactivé dans les réglages. */
    IDLE,

    /** Aucun identifiant d'application compilé. */
    NOT_CONFIGURED,

    /** Client Discord absent, ou service RPC injoignable. */
    UNREACHABLE,

    /** Discord a refusé la connexion — souvent une application non approuvée. */
    REFUSED,

    /** Statut publié sur le profil. */
    PUBLISHING,
}

/**
 * Statut d'activité Discord : le morceau en cours sur le profil.
 *
 * Passe par le service RPC du client Discord installé, avec le seul identifiant
 * d'application. Aucun jeton de compte, donc aucune automatisation de compte
 * utilisateur — on ne tombe pas sous l'interdiction des self-bots.
 *
 * Sur le verbe affiché : Discord décide seul. Le type 2 demande « Écoute », mais le RPC
 * des applications tierces est historiquement rendu en « Joue à ». Ce n'est pas de notre
 * ressort.
 */
@Singleton
class DiscordPresenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val _status = MutableStateFlow(PresenceStatus.IDLE)
    val status: StateFlow<PresenceStatus> = _status.asStateFlow()

    private var client: DiscordRpcClient? = null

    /** Les publications viennent du service de lecture ; une seule à la fois. */
    private val mutex = Mutex()

    val isConfigured: Boolean get() = BuildConfig.DISCORD_APPLICATION_ID.isNotBlank()

    /** Publie le morceau en cours. Sans effet si l'option est désactivée. */
    suspend fun update(song: Song, positionMs: Long, isPlaying: Boolean) =
        withContext(Dispatchers.IO) {
            if (!settings.settings.first().discordPresenceEnabled) return@withContext
            if (!isConfigured) {
                _status.value = PresenceStatus.NOT_CONFIGURED
                return@withContext
            }

            mutex.withLock {
                val rpc = ensureConnected() ?: return@withLock
                publish(rpc, activityFor(song, positionMs, isPlaying))
            }
        }

    /** Efface le statut : arrêt de la lecture, ou désactivation de l'option. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            client?.takeIf { it.isReady }?.let { publish(it, null) }
        }
    }

    /** Ferme la liaison. Appelé quand le service de lecture s'arrête. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            client?.takeIf { it.isReady }?.let { publish(it, null) }
            client?.disconnect()
            client = null
            _status.value = PresenceStatus.IDLE
        }
    }

    private suspend fun ensureConnected(): DiscordRpcClient? {
        client?.takeIf { it.isReady }?.let { return it }

        val id = BuildConfig.DISCORD_APPLICATION_ID.toLongOrNull()
        if (id == null) {
            _status.value = PresenceStatus.NOT_CONFIGURED
            return null
        }

        val fresh = DiscordRpcClient(context, id)
        return if (fresh.connect()) {
            client = fresh
            fresh
        } else {
            client = null
            // Un refus explicite de Discord n'est pas une absence de client : le premier
            // se corrige dans le portail développeur, le second en installant Discord.
            _status.value = if (fresh.lastError?.contains("refusé") == true) {
                PresenceStatus.REFUSED
            } else {
                PresenceStatus.UNREACHABLE
            }
            null
        }
    }

    /**
     * Transmet l'activité, dans une trame RPC identique à celle du bureau.
     *
     * `activity` à `null` efface le statut — c'est la convention de Discord, et non un
     * oubli : il n'existe pas de commande dédiée à l'effacement.
     */
    private fun publish(client: DiscordRpcClient, activity: JSONObject?): Boolean {
        val args = JSONObject().apply {
            put("pid", android.os.Process.myPid())
            put("activity", activity ?: JSONObject.NULL)
        }
        val frame = JSONObject().apply {
            put("cmd", "SET_ACTIVITY")
            put("nonce", java.util.UUID.randomUUID().toString())
            put("args", args)
        }

        val sent = client.sendFrame(frame.toString())
        _status.value = when {
            sent && activity != null -> PresenceStatus.PUBLISHING
            sent -> PresenceStatus.IDLE
            else -> PresenceStatus.UNREACHABLE
        }
        return sent
    }

    /**
     * Construit l'activité.
     *
     * Horodatages en **secondes** : Discord ignore silencieusement des millisecondes et
     * affiche alors le statut sans progression. `start` est calculé à rebours depuis la
     * position courante, pour que la barre reflète l'endroit réel du morceau et non un
     * début fictif au moment de la liaison.
     */
    private fun activityFor(song: Song, positionMs: Long, isPlaying: Boolean): JSONObject {
        val activity = JSONObject().apply {
            put("type", TYPE_LISTENING)
            put("details", song.title.take(MAX_FIELD))
            put("state", song.displayArtist.take(MAX_FIELD))
        }

        // En pause, pas d'horodatage : laisser une barre courir alors que rien ne joue
        // afficherait une progression fausse.
        if (isPlaying && song.durationMs > 0) {
            val nowSec = System.currentTimeMillis() / 1000
            val startSec = nowSec - positionMs / 1000
            activity.put(
                "timestamps",
                JSONObject().apply {
                    put("start", startSec)
                    put("end", startSec + song.durationMs / 1000)
                }
            )
        }

        return activity
    }

    private companion object {
        const val TAG = "DiscordPresence"
        /** 2 = « Écoute » dans la nomenclature Discord. */
        const val TYPE_LISTENING = 2
        /** Discord tronque au-delà de 128 caractères ; on le fait proprement avant lui. */
        const val MAX_FIELD = 128
    }
}
