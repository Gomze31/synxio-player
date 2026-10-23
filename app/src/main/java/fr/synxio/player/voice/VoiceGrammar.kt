package fr.synxio.player.voice

/** Ce que la voix peut demander au lecteur. */
sealed interface VoiceCommand {
    data object Play : VoiceCommand
    data object Pause : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object Restart : VoiceCommand
    data class SeekBy(val deltaMs: Long) : VoiceCommand
    /** `null` : bascule ; sinon active ou désactive. */
    data class Shuffle(val enabled: Boolean?) : VoiceCommand
    data object CycleRepeat : VoiceCommand
    data object VolumeUp : VoiceCommand
    data object VolumeDown : VoiceCommand
    data object Mute : VoiceCommand
    data object Unmute : VoiceCommand
    data class SetVolume(val percent: Int) : VoiceCommand
    data class SleepTimer(val minutes: Int) : VoiceCommand
    data object CancelSleepTimer : VoiceCommand
}

/** Lecture d'un résultat de reconnaissance. */
sealed interface VoiceHeard {
    /** Le mot-clé seul (ou suivi de bruit) : on ouvre une courte fenêtre d'écoute. */
    data object Wake : VoiceHeard
    /** Une commande complète ; [afterWake] si elle suivait le mot-clé dans la même phrase. */
    data class Command(val command: VoiceCommand, val afterWake: Boolean) : VoiceHeard
    data object Nothing : VoiceHeard
}

/**
 * Grammaire fermée du mode vocal, et son interprétation.
 *
 * Le petit modèle français de Vosk ne connaît pas « Synxio » ; prononcé, il le reconnaît
 * comme « synchro », qui sert donc de mot-clé. La grammaire ne contient que des mots du
 * modèle : Vosk ignorerait les autres.
 *
 * Même contraint, Vosk peut rendre une suite de mots de la grammaire qui ne forme aucune
 * phrase (« dix-sept son morceau répète » sur une conversation) : on n'agit que sur une
 * phrase reconnue en entier, et hors fenêtre d'écoute seulement si elle commence par
 * le mot-clé.
 */
object VoiceGrammar {

    const val WAKE_WORD = "synchro"
    private const val UNKNOWN = "[unk]"

    private val numbers = linkedMapOf(
        "un" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5, "six" to 6,
        "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10, "onze" to 11, "douze" to 12,
        "treize" to 13, "quatorze" to 14, "quinze" to 15, "seize" to 16, "dix-sept" to 17,
        "dix-huit" to 18, "dix-neuf" to 19, "vingt" to 20, "vingt-cinq" to 25, "trente" to 30,
        "trente-cinq" to 35, "quarante" to 40, "quarante-cinq" to 45, "cinquante" to 50,
        "soixante" to 60, "soixante-dix" to 70, "soixante-quinze" to 75, "quatre-vingt" to 80,
        "quatre-vingt-dix" to 90, "cent" to 100,
    )

    private val fixed: Map<String, VoiceCommand> = buildMap {
        listOf("pause", "stop", "arrête", "silence").forEach { put(it, VoiceCommand.Pause) }
        listOf("reprends", "reprends la lecture", "lecture", "lance la lecture", "joue")
            .forEach { put(it, VoiceCommand.Play) }
        listOf("suivant", "suivante", "passe", "chanson suivante", "titre suivant", "morceau suivant")
            .forEach { put(it, VoiceCommand.Next) }
        listOf("précédent", "précédente", "chanson précédente", "titre précédent", "morceau précédent")
            .forEach { put(it, VoiceCommand.Previous) }
        listOf("recommence", "du début", "reprends du début").forEach { put(it, VoiceCommand.Restart) }
        listOf("avance", "avance de trente secondes").forEach { put(it, VoiceCommand.SeekBy(30_000)) }
        put("avance d'une minute", VoiceCommand.SeekBy(60_000))
        listOf("recule", "recule de trente secondes").forEach { put(it, VoiceCommand.SeekBy(-30_000)) }
        put("recule d'une minute", VoiceCommand.SeekBy(-60_000))
        listOf("aléatoire", "mélange").forEach { put(it, VoiceCommand.Shuffle(null)) }
        put("active le mode aléatoire", VoiceCommand.Shuffle(true))
        put("active l'aléatoire", VoiceCommand.Shuffle(true))
        put("désactive le mode aléatoire", VoiceCommand.Shuffle(false))
        put("désactive l'aléatoire", VoiceCommand.Shuffle(false))
        listOf("répète", "répétition").forEach { put(it, VoiceCommand.CycleRepeat) }
        listOf("plus fort", "monte le son", "monte le volume").forEach { put(it, VoiceCommand.VolumeUp) }
        listOf("moins fort", "baisse le son", "baisse le volume").forEach { put(it, VoiceCommand.VolumeDown) }
        listOf("coupe le son", "mute").forEach { put(it, VoiceCommand.Mute) }
        put("remets le son", VoiceCommand.Unmute)
        listOf("annule le minuteur", "arrête le minuteur").forEach { put(it, VoiceCommand.CancelSleepTimer) }
        listOf("arrête dans une heure", "minuteur une heure").forEach { put(it, VoiceCommand.SleepTimer(60)) }
    }

    /** Durées proposées au minuteur : celles qu'on dit vraiment, pas chaque minute. */
    private val timerMinutes = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 75, 90)
    private val volumeLevels = listOf(10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100)

    private val commands: Map<String, VoiceCommand> = buildMap {
        putAll(fixed)
        numbers.forEach { (word, n) ->
            if (n in timerMinutes) {
                put("arrête dans $word minutes", VoiceCommand.SleepTimer(n))
                put("minuteur $word minutes", VoiceCommand.SleepTimer(n))
            }
            if (n in volumeLevels) put("volume $word", VoiceCommand.SetVolume(n))
        }
    }

    /**
     * Phrases à donner à Vosk. Chaque commande existe seule (dans la fenêtre d'écoute) et
     * précédée du mot-clé ; `[unk]` absorbe tout le reste au lieu de le forcer vers une
     * commande.
     */
    fun phrases(): List<String> = buildList {
        add(WAKE_WORD)
        addAll(commands.keys)
        commands.keys.forEach { add("$WAKE_WORD $it") }
        add(UNKNOWN)
    }

    /** Tous les mots employés, pour vérifier qu'ils figurent dans le modèle. */
    fun words(): Set<String> = phrases().filter { it != UNKNOWN }.flatMap { it.split(' ') }.toSet()

    fun parse(text: String): VoiceHeard {
        val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return VoiceHeard.Nothing
        if (words.first() == WAKE_WORD) {
            val rest = words.drop(1).joinToString(" ")
            val command = commands[rest] ?: return VoiceHeard.Wake
            return VoiceHeard.Command(command, afterWake = true)
        }
        val command = commands[words.joinToString(" ")] ?: return VoiceHeard.Nothing
        return VoiceHeard.Command(command, afterWake = false)
    }
}
