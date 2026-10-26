package fr.synxio.player.buds

import android.bluetooth.BluetoothCodecConfig
import android.view.KeyEvent
import fr.synxio.player.playback.AudioOutputs

/** Modèles de Galaxy Buds que l'on sait reconnaître, et leur courbe d'égaliseur. */
enum class BudsModel(val displayName: String, val curveId: String?) {
    BUDS3_PRO("Galaxy Buds3 Pro", "galaxy_buds3_pro"),
    BUDS_FE("Galaxy Buds FE", "galaxy_buds_fe"),
    /** Autres Galaxy Buds : gestes et carte, sans correction dédiée. */
    OTHER("Galaxy Buds", null),
}

/** Action d'un toucher des Buds, à la place de suivant (double) ou précédent (triple). */
enum class BudsTapAction(val label: String) {
    DEFAULT("Par défaut"),
    FAVORITE("Favori"),
    SEEK_FORWARD("+10 s"),
    SEEK_BACK("−10 s"),
    RESTART("Début du titre"),
    SHUFFLE("Aléatoire"),
}

/** Ce que lance la connexion des Buds quand rien ne joue. */
enum class BudsConnectSource(val label: String) {
    NONE("Rien"),
    DAILY_MIX("Mix du jour"),
    FAVORITES("Favoris"),
    SHUFFLE("Aléatoire"),
}

/** Types de codec absents des constantes publiques. */
object BudsCodecs {
    /** `BluetoothCodecConfig.SOURCE_CODEC_TYPE_INVALID`, aussi rendu pour les codecs constructeur. */
    const val INVALID = 1_000_000
}

/**
 * Reconnaissance des Galaxy Buds par leur nom Bluetooth.
 *
 * Samsung nomme ses écouteurs « Galaxy Buds3 Pro (A1B2) » ; le nom est la seule chose
 * qu'Android donne sans permission. Les autres marques reprennent souvent « Buds »
 * (Pixel Buds, Redmi Buds) : on exige « Galaxy », ou « Buds » en tête de nom.
 */
object GalaxyBuds {

    fun detect(name: String?): BudsModel? {
        val normalized = name.orEmpty().lowercase()
            .replace("+", " plus ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isEmpty()) return null
        val words = normalized.split(' ')
        val budsWord = words.indexOfFirst { it.startsWith("buds") }
        if (budsWord < 0) return null
        val galaxy = "galaxy" in words
        if (!galaxy && budsWord != 0) return null

        val compact = normalized.replace(Regex("buds (\\d)"), "buds$1")
        return when {
            Regex("\\bbuds3 pro\\b").containsMatchIn(compact) -> BudsModel.BUDS3_PRO
            Regex("\\bbuds fe\\b").containsMatchIn(compact) -> BudsModel.BUDS_FE
            else -> BudsModel.OTHER
        }
    }

    /**
     * Nom court du codec Bluetooth. [extendedName] vient de `BluetoothCodecType` (Android 14+),
     * le seul à nommer les codecs constructeur comme le Samsung Seamless Codec.
     */
    @Suppress("DEPRECATION")
    fun codecName(type: Int, extendedName: String?): String? {
        extendedName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return when (type) {
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> "SBC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3 -> "LC3"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_OPUS -> "Opus"
            else -> null
        }
    }
}

/**
 * Touchers des Buds. Le firmware tranche lui-même double et triple toucher et envoie
 * « suivant » ou « précédent » : on ne peut remapper que ces deux touches-là.
 */
object BudsGestures {

    /** Action à exécuter à la place de la touche ; null : traitement habituel. */
    fun remap(keyCode: Int, onBuds: Boolean, doubleTap: BudsTapAction, tripleTap: BudsTapAction): BudsTapAction? {
        if (!onBuds) return null
        val action = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> doubleTap
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tripleTap
            else -> return null
        }
        return action.takeIf { it != BudsTapAction.DEFAULT }
    }
}

/** Alerte de batterie faible des écouteurs. */
object BudsAlerts {

    private val THRESHOLDS = listOf(15, 5)

    /**
     * Seuil franchi en descendant entre deux mesures, le plus bas s'il y en a plusieurs.
     * La première mesure ne déclenche rien : chaque reconnexion alerterait sinon.
     */
    fun crossed(previous: Int?, current: Int?): Int? {
        if (previous == null || current == null) return null
        return THRESHOLDS.filter { current <= it && previous > it }.minOrNull()
    }
}

/** Ce que fait la connexion des écouteurs. */
object BudsConnect {

    enum class Action { NOTHING, RESUME, START_SOURCE }

    /**
     * La reprise de la file l'emporte quand elle est demandée et possible ; sinon la source
     * choisie démarre. Jamais pendant un appel ni par-dessus une lecture.
     */
    fun decide(
        resumeEnabled: Boolean,
        source: BudsConnectSource,
        hasQueue: Boolean,
        playWhenReady: Boolean,
        inCall: Boolean,
    ): Action = when {
        AudioOutputs.shouldResumeOnConnect(resumeEnabled, hasQueue, playWhenReady, inCall) -> Action.RESUME
        source != BudsConnectSource.NONE && !playWhenReady && !inCall -> Action.START_SOURCE
        else -> Action.NOTHING
    }
}
