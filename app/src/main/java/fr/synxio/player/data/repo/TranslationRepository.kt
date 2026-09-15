package fr.synxio.player.data.repo

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import fr.synxio.player.data.model.LyricLine
import fr.synxio.player.data.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor() {

    private val TAG = "TranslationRepository"
    
    // Cache basique en mémoire : Map de "id/chemin_morceau + langue_cible" vers Lyrics traduites
    private val translationCache = mutableMapOf<String, Lyrics>()

    suspend fun translateLyrics(
        lyrics: Lyrics,
        sourceLang: String = TranslateLanguage.ENGLISH,
        targetLang: String = TranslateLanguage.FRENCH,
        cacheKey: String
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        val key = "${cacheKey}_${sourceLang}_${targetLang}"
        translationCache[key]?.let {
            return@withContext Result.success(it)
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
            
        val translator = Translation.getClient(options)
        
        try {
            val conditions = DownloadConditions.Builder().build()
            // Télécharge le modèle si nécessaire
            translator.downloadModelIfNeeded(conditions).await()
            
            // Traduire chaque ligne
            val translatedLines = mutableListOf<LyricLine>()
            
            for (line in lyrics.lines) {
                if (line.text.isBlank() || line.text.startsWith("[")) {
                    translatedLines.add(line)
                    continue
                }
                
                val translatedText = translator.translate(line.text).await()
                translatedLines.add(LyricLine(line.timeMs, translatedText))
            }
            
            val translatedLyrics = Lyrics(
                lines = translatedLines,
                synced = lyrics.synced,
                source = lyrics.source + " (Traduit en \${targetLang})"
            )
            
            translationCache[key] = translatedLyrics
            Result.success(translatedLyrics)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la traduction", e)
            Result.failure(e)
        } finally {
            translator.close()
        }
    }
}
