package fr.synxio.player.data.discord

import android.util.Log
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

/** Ce que l'application sait de sa liaison avec le client Discord. */
enum class PresenceStatus {
    /** Jamais tenté, ou désactivé dans les réglages. */
    IDLE,
    CONNECTED,
    /** Le client Discord n'expose pas de socket RPC atteignable. */
    UNREACHABLE,
    /** Aucun identifiant d'application compilé. */
    NOT_CONFIGURED,
}

/**
 * Statut d'activité Discord : « Synxio — titre / artiste » sur le profil.
 *
 * Passe par [DiscordRpcClient], donc par le client Discord installé sur l'appareil, avec
 * le seul identifiant d'application. Aucun jeton de compte, aucune automatisation de
 * compte utilisateur.
 *
 * À savoir sur l'affichage : Discord décide seul du verbe. Le type 2 demande « Écoute »,
 * mais le RPC des applications tierces est historiquement rendu en « Joue à ». Le verbe
 * exact n'est pas sous notre contrôle.
 */
@Singleton
class DiscordPresenceRepository @Inject constructor(
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
                // En pause, le morceau reste affiché mais sans barre de progression :
                // laisser une barre courir alors que rien ne joue serait faux.
                rpc.setActivity(activityFor(song, positionMs, isPlaying))
            }
        }

    /** Efface le statut : arrêt de la lecture, ou désactivation de l'option. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            client?.takeIf { it.isConnected }?.setActivity(null)
        }
    }

    /** Ferme la liaison. Appelé quand le service de lecture s'arrête. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            client?.setActivity(null)
            client?.close()
            client = null
            _status.value = PresenceStatus.IDLE
        }
    }

    private fun ensureConnected(): DiscordRpcClient? {
        client?.takeIf { it.isConnected }?.let { return it }

        val fresh = DiscordRpcClient(BuildConfig.DISCORD_APPLICATION_ID)
        return if (fresh.connect()) {
            client = fresh
            _status.value = PresenceStatus.CONNECTED
            fresh
        } else {
            client = null
            _status.value = PresenceStatus.UNREACHABLE
            Log.i(TAG, "Client Discord injoignable — statut d'activité indisponible")
            null
        }
    }

    /**
     * Construit l'activité.
     *
     * Les horodatages sont donnés en **secondes** — Discord ignore silencieusement des
     * millisecondes, et le statut s'affiche alors sans progression. `start` est calculé
     * à rebours depuis la position courante pour que la barre reflète l'endroit réel du
     * morceau, et pas un début fictif au moment de la connexion.
     */
    private fun activityFor(song: Song, positionMs: Long, isPlaying: Boolean): JSONObject {
        val activity = JSONObject().apply {
            put("type", TYPE_LISTENING)
            put("details", song.title.take(MAX_FIELD))
            put("state", song.displayArtist.take(MAX_FIELD))
        }

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
