package fr.synxio.player.data.repo

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import fr.synxio.player.data.model.LyricLine
import fr.synxio.player.data.model.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Les paroles sont déjà dans la langue demandée : rien à traduire. */
class AlreadyInTargetLanguage : Exception("Paroles déjà dans la langue cible")

@Singleton
class TranslationRepository @Inject constructor() {

    private val TAG = "TranslationRepository"

    // Cache en mémoire : "chemin du morceau + langue cible" vers les paroles traduites.
    private val translationCache = ConcurrentHashMap<String, Lyrics>()

    /**
     * Traduit [lyrics] vers [targetLang].
     *
     * Sans [sourceLang], la langue est détectée sur le texte : traduire depuis l'anglais
     * des paroles déjà en français les déformait. Échoue avec [AlreadyInTargetLanguage]
     * quand il n'y a rien à faire, et ne lève jamais : ML Kit peut être indisponible
     * (services Google absents, modèle non téléchargeable) sans que l'écran plante.
     */
    suspend fun translateLyrics(
        lyrics: Lyrics,
        targetLang: String = TranslateLanguage.FRENCH,
        cacheKey: String,
        sourceLang: String? = null,
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        val key = "${cacheKey}_$targetLang"
        translationCache[key]?.let { return@withContext Result.success(it) }

        var translator: Translator? = null
        try {
            val source = sourceLang ?: detectLanguage(lyrics)
                ?: error("Langue des paroles non reconnue")
            if (source == targetLang) throw AlreadyInTargetLanguage()

            translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(targetLang)
                    .build()
            )
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()

            val translatedLines = lyrics.lines.map { line ->
                if (line.text.isBlank() || line.text.startsWith("[")) line
                else LyricLine(line.timeMs, translator.translate(line.text).await())
            }
            val translated = Lyrics(
                lines = translatedLines,
                synced = lyrics.synced,
                source = "${lyrics.source} (traduit)",
            )
            translationCache[key] = translated
            Result.success(translated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AlreadyInTargetLanguage) {
            Result.failure(e)
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur lors de la traduction", e)
            Result.failure(e)
        } finally {
            translator?.close()
        }
    }

    /** Code de langue ML Kit des paroles, ou `null` si inconnu ou non traduisible. */
    private suspend fun detectLanguage(lyrics: Lyrics): String? {
        val sample = lyrics.lines.asSequence()
            .map { it.text }
            .filter { it.isNotBlank() }
            .take(40)
            .joinToString("\n")
        if (sample.isBlank()) return null
        val identifier = LanguageIdentification.getClient()
        return try {
            val tag = identifier.identifyLanguage(sample).await()
            if (tag == "und") null else TranslateLanguage.fromLanguageTag(tag)
        } finally {
            identifier.close()
        }
    }
}
