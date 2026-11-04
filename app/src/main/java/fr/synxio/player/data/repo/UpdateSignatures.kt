package fr.synxio.player.data.repo

/**
 * Règle d'acceptation d'une mise à jour, en empreintes SHA-256 de certificats.
 *
 * Deux conditions cumulatives :
 * 1. le signataire de l'APK téléchargé est l'empreinte épinglée [PINNED_SIGNER_SHA256],
 *    celle de la clé de release privée ;
 * 2. sa lignée (APK Signature Scheme v3) contient le signataire de l'appli installée,
 *    ce qui est aussi la règle d'Android. Une appli encore signée par l'ancienne clé
 *    accepte donc la première version signée par la nouvelle.
 *
 * Un APK signé par l'ancienne clé seule n'est jamais accepté, même par une appli qui
 * l'utilise encore : c'est le but de la rotation.
 */
object UpdateSignatures {

    /**
     * Empreinte SHA-256 du certificat de release, hexadécimal sans séparateur.
     * Vide tant que la clé n'est pas générée : aucune mise à jour n'est alors acceptée.
     */
    const val PINNED_SIGNER_SHA256 = ""

    fun compatible(
        installedCurrent: Set<String>,
        downloadedCurrent: Set<String>,
        downloadedLineage: List<String>,
        pinned: String = PINNED_SIGNER_SHA256,
    ): Boolean {
        if (pinned.isBlank()) return false
        val downloaded = downloadedCurrent.singleOrNull() ?: return false
        if (!downloaded.equals(pinned, ignoreCase = true)) return false
        val installed = installedCurrent.singleOrNull() ?: return false
        if (installed.equals(downloaded, ignoreCase = true)) return true
        return downloadedLineage.any { it.equals(installed, ignoreCase = true) }
    }
}
