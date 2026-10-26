package fr.synxio.player.data.repo

/**
 * Règle d'acceptation d'une mise à jour, en empreintes SHA-256 de certificats.
 *
 * Même signataire : accepté. Clé changée par rotation (lignée APK Signature Scheme v3) :
 * accepté si la lignée de l'APK téléchargé contient le signataire actuel de l'appli
 * installée — c'est aussi la règle d'Android. Un APK signé par l'ancienne clé seule,
 * proposé à une appli déjà passée sur la nouvelle, est refusé.
 */
object UpdateSignatures {

    fun compatible(
        installedCurrent: Set<String>,
        downloadedCurrent: Set<String>,
        downloadedLineage: List<String>,
    ): Boolean {
        if (installedCurrent.isEmpty() || downloadedCurrent.isEmpty()) return false
        if (installedCurrent == downloadedCurrent) return true
        // La lignée n'existe qu'avec un signataire unique.
        val installed = installedCurrent.singleOrNull() ?: return false
        return downloadedCurrent.size == 1 && installed in downloadedLineage
    }
}
