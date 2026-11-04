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
 * Les opérations Android (bibliothèque, réseau) sont injectées pour rester testable.
 */
class YtDlpUpdater(
    private val binary: File,
    private val runLibraryUpdate: () -> LibraryOutcome,
    private val installedVersion: () -> String?,
    private val fetchSums: (version: String) -> String?,
) {
    enum class LibraryOutcome { UPDATED, ALREADY_UP_TO_DATE }

    sealed interface Result {
        data class Updated(val version: String) : Result
        data object AlreadyUpToDate : Result
        data class Rejected(val reason: String) : Result
    }

    fun update(): Result {
        if (!binary.isFile) return Result.Rejected("binaire yt-dlp introuvable")
        val backup = File(binary.parentFile, "${binary.name}.bak")
        binary.copyTo(backup, overwrite = true)

        val outcome = try {
            runLibraryUpdate()
        } catch (e: Throwable) {
            restore(backup)
            return Result.Rejected("mise à jour échouée : ${e.message ?: e.javaClass.simpleName}")
        }
        if (outcome == LibraryOutcome.ALREADY_UP_TO_DATE) {
            backup.delete()
            return Result.AlreadyUpToDate
        }

        val version = installedVersion()?.takeIf { it.isNotBlank() }
            ?: return reject(backup, "version installée inconnue")
        val sums = fetchSums(version)
            ?: return reject(backup, "sommes de contrôle inaccessibles pour $version")
        val expected = ShaSums.digestFor(sums, binary.name)
            ?: return reject(backup, "aucune somme publiée pour ${binary.name} ($version)")
        val actual = sha256(binary)
        return if (actual == expected) {
            backup.delete()
            Result.Updated(version)
        } else {
            reject(backup, "somme de contrôle différente ($version)")
        }
    }

    private fun reject(backup: File, reason: String): Result {
        restore(backup)
        return Result.Rejected(reason)
    }

    private fun restore(backup: File) {
        if (backup.isFile) {
            backup.copyTo(binary, overwrite = true)
            backup.delete()
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
