package fr.synxio.player.data.model

/**
 * Empreinte sonore 2 d'un titre : ce que l'analyse de fond a compris de sa musique.
 *
 * @param bpm tempo, 0 si inconnu ; @param keyIndex 0–11 majeur (do…si), 12–23 mineur, −1 inconnu ;
 * @param energy 0–1 ; @param introMs premier instant où ça joue vraiment ; @param outroMs début de la
 *   queue silencieuse (ou durée du titre).
 */
data class TrackTraits(
    val bpm: Float,
    val bpmConfidence: Float,
    val keyIndex: Int,
    val keyConfidence: Float,
    val energy: Float,
    val introMs: Long,
    val outroMs: Long,
) {
    val hasBpm: Boolean get() = bpm > 0f
    val hasKey: Boolean get() = keyIndex in 0..23
    val keyName: String? get() = if (hasKey) KeyNames.french(keyIndex) else null
    val camelot: String? get() = if (hasKey) KeyNames.camelot(keyIndex) else null

    companion object {
        /** Valeur de `analysisVersion` écrite quand ces traits sont calculés. */
        const val VERSION = 2
    }
}

/** Noms de tonalités : français (« la mineur ») et roue de Camelot (« 8A »). Pur. */
object KeyNames {
    private val pitch = listOf("do", "do♯", "ré", "ré♯", "mi", "fa", "fa♯", "sol", "sol♯", "la", "la♯", "si")

    fun french(keyIndex: Int): String =
        if (keyIndex < 12) "${pitch[keyIndex]} majeur" else "${pitch[keyIndex - 12]} mineur"

    /** Majeur = B, mineur = A ; le relatif mineur partage le numéro (la mineur = 8A, do majeur = 8B). */
    fun camelot(keyIndex: Int): String {
        val major = keyIndex < 12
        val pc = if (major) keyIndex else (keyIndex - 12 + 3) % 12
        val number = ((pc * 7) % 12 + 7) % 12 + 1
        return "$number${if (major) "B" else "A"}"
    }

    /** Résout « la mineur », « La mineur », « 8A », « 8a » ; null sinon. */
    fun parse(text: String): Int? {
        val t = text.trim().lowercase()
        Regex("""^(\d{1,2})([ab])$""").matchEntire(t)?.let { m ->
            val number = m.groupValues[1].toInt()
            val major = m.groupValues[2] == "b"
            if (number !in 1..12) return null
            return (0 until 24).firstOrNull { camelot(it).lowercase() == "$number${if (major) "b" else "a"}" }
        }
        return (0 until 24).firstOrNull { french(it) == t }
    }

    /** Distance Camelot : 0 même case, 1 voisin ou relatif, 2 à deux cases, 3 au-delà. */
    fun distance(a: Int, b: Int): Int {
        if (a == b) return 0
        val na = camelot(a); val nb = camelot(b)
        val numA = na.dropLast(1).toInt(); val numB = nb.dropLast(1).toInt()
        val sameLetter = na.last() == nb.last()
        val steps = minOf((numA - numB + 12) % 12, (numB - numA + 12) % 12)
        return when {
            steps == 0 -> 1            // relatif majeur/mineur
            steps == 1 && sameLetter -> 1
            steps <= 2 -> 2
            else -> 3
        }
    }
}
