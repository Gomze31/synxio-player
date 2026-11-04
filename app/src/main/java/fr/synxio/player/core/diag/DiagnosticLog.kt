package fr.synxio.player.core.diag

import java.io.File

/**
 * Journal de diagnostic : les [maxLines] dernières lignes en mémoire, et un fichier
 * `synxio.log` avec une seule rotation vers `synxio.log.1` au-delà de [maxFileBytes].
 *
 * Volontairement synchrone : seules quelques zones de l'app écrivent ici, quelques lignes
 * par événement. Un verrou suffit, pas de dispatcher dédié.
 */
class DiagnosticLog(
    private val dir: File,
    private val maxLines: Int = 500,
    private val maxFileBytes: Long = 256L * 1024,
) {
    init {
        require(maxLines > 0) { "maxLines doit être strictement positif (reçu : $maxLines)" }
    }

    private val lock = Any()
    private val lines = ArrayDeque<String>(maxLines)

    private val current get() = File(dir, "synxio.log")
    private val previous get() = File(dir, "synxio.log.1")

    fun append(line: String) {
        synchronized(lock) {
            if (lines.size == maxLines) lines.removeFirst()
            lines.addLast(line)
            runCatching {
                dir.mkdirs()
                if (current.exists() && current.length() > maxFileBytes) {
                    rotate()
                }
                current.appendText(line + "\n")
            }
        }
    }

    /**
     * Archive le fichier courant dans `synxio.log.1`. Si le renommage échoue (verrou Windows,
     * poignée ouverte), on copie puis on vide le courant : la borne de taille reste respectée.
     */
    private fun rotate() {
        previous.delete()
        if (current.renameTo(previous)) return
        runCatching { current.copyTo(previous, overwrite = true) }
        if (!current.delete()) runCatching { current.writeText("") }
    }

    fun recent(limit: Int): List<String> = synchronized(lock) { lines.takeLast(limit) }

    /** Contenu du fichier précédent puis du courant, dans l'ordre chronologique. */
    fun snapshot(): String = synchronized(lock) {
        buildString {
            if (previous.exists()) append(previous.readText())
            if (current.exists()) append(current.readText())
        }
    }

    fun sizeBytes(): Long = synchronized(lock) {
        (if (previous.exists()) previous.length() else 0L) + (if (current.exists()) current.length() else 0L)
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            previous.delete()
            current.delete()
        }
    }
}
