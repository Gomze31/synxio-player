package fr.synxio.player.core.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.diag.Diagnostics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secrets de l'utilisateur (session Last.fm, webhook Discord), chiffrés par une clé qui
 * ne sort pas du Keystore matériel. Le fichier `synxio_secrets.xml` est exclu de la
 * sauvegarde Android : après une restauration, ces valeurs sont à ressaisir.
 *
 * Jamais de repli en clair : si le Keystore est indisponible, on lit vide et on refuse
 * d'écrire.
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context,
    private val diagnostics: Diagnostics,
) {
    enum class Key(val prefName: String) {
        DISCORD_WEBHOOK_URL("discord_webhook_url"),
        LASTFM_SESSION("lastfm_session"),
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cache = ConcurrentHashMap<Key, String>()

    private val _changes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1).apply { tryEmit(Unit) }
    /** Émet à chaque écriture ; replay = 1 pour que `combine` démarre sans attendre. */
    val changes: SharedFlow<Unit> = _changes

    private val lock = Any()

    /** Mis en cache uniquement après un succès : un échec transitoire du Keystore est retenté. */
    @Volatile
    private var codec: SecretCodec? = null

    private fun codecOrNull(): SecretCodec? {
        codec?.let { return it }
        return synchronized(lock) {
            codec ?: runCatching { SecretCodec(loadOrCreateKey()) }
                .onFailure { diagnostics.log(TAG, Diagnostics.Level.E, "Keystore indisponible : secrets inaccessibles", it) }
                .getOrNull()
                ?.also { codec = it }
        }
    }

    fun get(key: Key): String? {
        cache[key]?.let { return it }
        return synchronized(lock) {
            cache[key] ?: run {
                val stored = prefs.getString(key.prefName, null) ?: return@run null
                val codec = codecOrNull() ?: return@run null
                runCatching { codec.decrypt(stored) }
                    .onFailure { diagnostics.log(TAG, Diagnostics.Level.W, "Secret ${key.name} illisible, ignoré", it) }
                    .getOrNull()
                    ?.also { cache[key] = it }
            }
        }
    }

    /** `null` ou vide supprime le secret. Lève `IllegalStateException` si le Keystore est indisponible ou échoue. */
    fun put(key: Key, value: String?) {
        synchronized(lock) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(key.prefName).apply()
                cache.remove(key)
            } else {
                val codec = codecOrNull()
                    ?: throw IllegalStateException("Keystore indisponible : impossible d'enregistrer ${key.name}")
                val encrypted = try {
                    codec.encrypt(value)
                } catch (e: Exception) {
                    val message = "Chiffrement impossible : ${key.name} non enregistré"
                    diagnostics.log(TAG, Diagnostics.Level.E, message, e)
                    throw IllegalStateException(message, e)
                }
                prefs.edit().putString(key.prefName, encrypted).apply()
                cache[key] = value
            }
        }
        _changes.tryEmit(Unit)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) {
            val entry = keyStore.getEntry(ALIAS, null)
            // Jamais de régénération : cela orphelinerait les secrets déjà chiffrés.
            return (entry as? KeyStore.SecretKeyEntry)?.secretKey
                ?: error("L'alias $ALIAS existe mais n'est pas une clé secrète")
        }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val TAG = "SecretStore"
        const val PREFS = "synxio_secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "synxio.secrets"
    }
}
