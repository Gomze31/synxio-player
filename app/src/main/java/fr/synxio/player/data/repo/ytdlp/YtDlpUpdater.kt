package fr.synxio.player.data.repo.ytdlp

import java.io.File
import java.security.MessageDigest

/**
 * Mise à jour de yt-dlp avec filet : copie de l'ancien binaire, mise à jour par la
 * bibliothèque, vérification du SHA-256 contre les sommes publiées de la release,
 * restauration au moindre doute.
 *
 * Ce que ça protège : asset substitué, téléchargement tronqué, release renommée.
 * Ce que ça ne protège pas : une release yt-dlp elle-même compromise, puisque les sommes
 * viennent de la même release.
 *
 * La copie de sauvegarde [backup] doit se trouver hors du dossier du binaire : la
 * bibliothèque youtubedl-android 0.18.1 supprime ce dossier entier avant d'y copier le
 * nouveau binaire, et une sauvegarde rangée à côté disparaîtrait avec lui.
 *
 * Les opérations Android (bibliothèque, réseau) sont injectées pour rester testable.
 */
class YtDlpUpdater(
    private val binary: File,
    private val backup: File,
    private val runLibraryUpdate: () -> LibraryOutcome,
    private val installedVersion: () -> String?,
    private val fetchSums: (version: String) -> String?,
) {
    enum class LibraryOutcome { UPDATED, ALREADY_UP_TO_DATE }

    sealed interface Result {
        data class Updated(val version: String) : Result
        data object AlreadyUpToDate : Result

        /**
         * Mise à jour refusée. [lost] vaut `true` quand l'ancien binaire n'a pas pu être
         * remis en place : le binaire non vérifié a été supprimé ou vidé, et l'appelant doit
         * réinstaller le binaire embarqué.
         */
        data class Rejected(val reason: String, val lost: Boolean = false) : Result
    }

    fun update(): Result {
        // Reprise après interruption : une sauvegarde restante contient l'ancien binaire sain.
        if (backup.exists() && !restore(binary, backup)) return lostResult()
        if (!binary.isFile) return Result.Rejected("binaire yt-dlp introuvable")
        try {
            backup.parentFile?.mkdirs()
            binary.copyTo(backup, overwrite = true)
        } catch (e: Exception) {
            backup.delete()
            return Result.Rejected("sauvegarde impossible")
        }

        val outcome = try {
            runLibraryUpdate()
        } catch (e: Exception) {
            return reject("mise à jour échouée : ${e.message ?: e.javaClass.simpleName}")
        }
        if (outcome == LibraryOutcome.ALREADY_UP_TO_DATE) {
            backup.delete()
            return Result.AlreadyUpToDate
        }

        return try {
            val version = installedVersion()?.takeIf { it.isNotBlank() }
                ?: return reject("version installée inconnue")
            val sums = fetchSums(version)
                ?: return reject("sommes de contrôle inaccessibles pour $version")
            val expected = ShaSums.digestFor(sums, binary.name)
                ?: return reject("aucune somme publiée pour ${binary.name} ($version)")
            if (sha256(binary) == expected) {
                backup.delete()
                Result.Updated(version)
            } else {
                reject("somme de contrôle différente ($version)")
            }
        } catch (e: Exception) {
            reject("vérification impossible : ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun reject(reason: String): Result =
        if (restore(binary, backup)) Result.Rejected(reason) else lostResult()

    private fun lostResult(): Result = Result.Rejected(LOST, lost = true)

    companion object {
        const val LOST = "restauration impossible : le binaire embarqué est réinstallé"

        /**
         * Reprise d'une mise à jour interrompue : si [backup] existe, il contient le dernier
         * binaire sain et repasse par-dessus [binary]. Rend `true` si une reprise a eu lieu
         * et réussi, `false` s'il n'y avait rien à reprendre ou si la reprise a échoué (le
         * binaire non vérifié est alors supprimé ou vidé).
         */
        fun recoverInterrupted(binary: File, backup: File): Boolean =
            backup.exists() && restore(binary, backup)

        /**
         * Remet l'ancien binaire en place (renommage, sinon copie). Si c'est impossible, le
         * binaire non vérifié est supprimé, ou vidé si la suppression échoue, plutôt que
         * laissé exécutable.
         */
        private fun restore(binary: File, backup: File): Boolean {
            try {
                if (backup.isFile) {
                    binary.parentFile?.mkdirs()
                    if (backup.renameTo(binary)) return true
                    backup.copyTo(binary, overwrite = true)
                    backup.delete()
                    return true
                }
            } catch (e: Exception) {
                // repli ci-dessous
            }
            if (binary.exists() && !binary.delete()) {
                try {
                    binary.writeText("")
                } catch (e: Exception) {
                    // rien de plus à tenter : le dossier est inaccessible en écriture
                }
            }
            return false
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
