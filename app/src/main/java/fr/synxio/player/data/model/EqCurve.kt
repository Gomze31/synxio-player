package fr.synxio.player.data.model

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Courbe d'égalisation définie en points (fréquence Hz → gain dB).
 *
 * On ne stocke pas des niveaux par bande : chaque téléphone expose un nombre de bandes
 * et des fréquences centrales différents (5 bandes ici, 10 ailleurs). Une courbe
 * indépendante du matériel est interpolée à la volée sur les bandes réelles.
 */
data class EqCurve(
    val id: String,
    val name: String,
    val description: String,
    val category: EqCategory,
    /** Points triés par fréquence croissante. */
    val points: List<Point>,
) {
    data class Point(val hz: Int, val gainDb: Float)

    /**
     * Convertit la courbe en niveaux de bandes, en millibels, bornés aux capacités
     * de l'appareil.
     *
     * L'interpolation se fait en échelle **logarithmique** : l'oreille perçoit les
     * fréquences ainsi, et une interpolation linéaire écraserait tout le grave.
     */
    fun toBandLevels(bandFrequenciesHz: List<Int>, minMb: Short, maxMb: Short): List<Short> =
        bandFrequenciesHz.map { hz ->
            val gainDb = gainAt(hz)
            (gainDb * 100).roundToInt().coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
        }

    private fun gainAt(hz: Int): Float {
        if (points.isEmpty()) return 0f
        val first = points.first()
        val last = points.last()
        if (hz <= first.hz) return first.gainDb
        if (hz >= last.hz) return last.gainDb

        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (hz in a.hz..b.hz) {
                val span = ln(b.hz.toDouble()) - ln(a.hz.toDouble())
                if (span <= 0.0) return a.gainDb
                val ratio = (ln(hz.toDouble()) - ln(a.hz.toDouble())) / span
                return (a.gainDb + (b.gainDb - a.gainDb) * ratio).toFloat()
            }
        }
        return 0f
    }
}

enum class EqCategory(val label: String) {
    SIGNATURE("Signature sonore"),
    HEADPHONES("Casques et écouteurs"),
    CONTENT("Type de contenu"),
}

private fun p(hz: Int, db: Float) = EqCurve.Point(hz, db)

/**
 * Profils intégrés.
 *
 * ⚠️ Les courbes « casques » sont des **corrections d'écoute** que j'ai dérivées de la
 * signature connue de chaque modèle — ce ne sont pas des préréglages officiels des
 * constructeurs, qui ne publient pas leurs courbes. Elles compensent les excès
 * mesurés du casque ; à ajuster à l'oreille, tout est modifiable ensuite.
 */
object EqCurves {

    /** Sony WH-1000XM3 : grave très généreux, médiums hauts en retrait, aigu adouci. */
    val SONY_WH1000XM3 = EqCurve(
        id = "sony_wh1000xm3",
        name = "Sony WH-1000XM3",
        description = "Dompte le grave surdimensionné et rouvre les voix",
        category = EqCategory.HEADPHONES,
        points = listOf(
            p(32, -4.5f), p(64, -3.5f), p(125, -2f), p(250, -0.5f),
            p(500, 0f), p(1000, 1f), p(2000, 2.5f), p(4000, 2f),
            p(8000, 1.5f), p(16000, 2f),
        ),
    )

    /** HONOR Earbuds 3 Pro : signature en V, grave appuyé et aigu brillant. */
    val HONOR_EARBUDS_3_PRO = EqCurve(
        id = "honor_earbuds_3_pro",
        name = "HONOR Earbuds 3 Pro",
        description = "Adoucit le V : moins de grave et de brillance, médiums remontés",
        category = EqCategory.HEADPHONES,
        points = listOf(
            p(32, -3f), p(64, -2.5f), p(125, -1.5f), p(250, 0.5f),
            p(500, 1.5f), p(1000, 2f), p(2000, 1.5f), p(4000, -0.5f),
            p(8000, -2f), p(16000, -1.5f),
        ),
    )

    /** Écouteurs filaires génériques : creux dans le bas-médium à combler. */
    val EARBUDS_GENERIC = EqCurve(
        id = "earbuds_generic",
        name = "Écouteurs filaires",
        description = "Compense le manque de corps des petits transducteurs",
        category = EqCategory.HEADPHONES,
        points = listOf(
            p(32, 3f), p(64, 2.5f), p(125, 1.5f), p(250, 0f),
            p(500, 0f), p(1000, 0.5f), p(2000, 1f), p(4000, 1f),
            p(8000, 0.5f), p(16000, 0f),
        ),
    )

    /** Haut-parleur du téléphone : inutile de pousser un grave qu'il ne peut pas rendre. */
    val PHONE_SPEAKER = EqCurve(
        id = "phone_speaker",
        name = "Haut-parleur du téléphone",
        description = "Concentre l'énergie là où le petit haut-parleur sait jouer",
        category = EqCategory.HEADPHONES,
        points = listOf(
            p(32, -6f), p(64, -5f), p(125, -2f), p(250, 1f),
            p(500, 2.5f), p(1000, 2.5f), p(2000, 2f), p(4000, 1f),
            p(8000, 0f), p(16000, -1f),
        ),
    )

    val FLAT = EqCurve(
        id = "flat",
        name = "Neutre",
        description = "Aucune correction",
        category = EqCategory.SIGNATURE,
        points = listOf(p(32, 0f), p(16000, 0f)),
    )

    val BASS_BOOST = EqCurve(
        id = "bass_boost",
        name = "Grave renforcé",
        description = "Du poids sans noyer les voix",
        category = EqCategory.SIGNATURE,
        points = listOf(
            p(32, 6f), p(64, 5f), p(125, 3f), p(250, 1f),
            p(500, 0f), p(1000, 0f), p(2000, 0f), p(4000, 0.5f),
            p(8000, 1f), p(16000, 1f),
        ),
    )

    val VOCAL = EqCurve(
        id = "vocal",
        name = "Voix en avant",
        description = "Met le chant devant l'instrumentation",
        category = EqCategory.SIGNATURE,
        points = listOf(
            p(32, -3f), p(64, -2f), p(125, -1f), p(250, 1f),
            p(500, 3f), p(1000, 4f), p(2000, 3.5f), p(4000, 2f),
            p(8000, 0f), p(16000, -1f),
        ),
    )

    val TREBLE = EqCurve(
        id = "treble",
        name = "Aigu détaillé",
        description = "Ouvre le haut du spectre",
        category = EqCategory.SIGNATURE,
        points = listOf(
            p(32, 0f), p(64, 0f), p(125, 0f), p(250, 0f),
            p(500, 0.5f), p(1000, 1f), p(2000, 2.5f), p(4000, 4f),
            p(8000, 5f), p(16000, 4.5f),
        ),
    )

    val LOUDNESS = EqCurve(
        id = "loudness",
        name = "Écoute à bas volume",
        description = "Courbe Fletcher-Munson : rétablit grave et aigu quand on baisse le son",
        category = EqCategory.CONTENT,
        points = listOf(
            p(32, 6f), p(64, 4.5f), p(125, 2.5f), p(250, 0.5f),
            p(500, 0f), p(1000, 0f), p(2000, 1f), p(4000, 3f),
            p(8000, 4.5f), p(16000, 5f),
        ),
    )

    val PODCAST = EqCurve(
        id = "podcast",
        name = "Podcast et voix parlée",
        description = "Coupe le grondement, clarifie l'articulation",
        category = EqCategory.CONTENT,
        points = listOf(
            p(32, -8f), p(64, -6f), p(125, -3f), p(250, 0f),
            p(500, 2f), p(1000, 3.5f), p(2000, 4f), p(4000, 3f),
            p(8000, 1f), p(16000, -2f),
        ),
    )

    val LATE_NIGHT = EqCurve(
        id = "late_night",
        name = "Écoute nocturne",
        description = "Resserre la dynamique pour ne réveiller personne",
        category = EqCategory.CONTENT,
        points = listOf(
            p(32, -5f), p(64, -3f), p(125, -1f), p(250, 0.5f),
            p(500, 1.5f), p(1000, 2f), p(2000, 1.5f), p(4000, 0.5f),
            p(8000, -1f), p(16000, -3f),
        ),
    )

    val ALL: List<EqCurve> = listOf(
        FLAT, BASS_BOOST, VOCAL, TREBLE,
        SONY_WH1000XM3, HONOR_EARBUDS_3_PRO, EARBUDS_GENERIC, PHONE_SPEAKER,
        LOUDNESS, PODCAST, LATE_NIGHT,
    )

    fun byCategory(category: EqCategory): List<EqCurve> = ALL.filter { it.category == category }

    fun byId(id: String): EqCurve? = ALL.firstOrNull { it.id == id }
}
