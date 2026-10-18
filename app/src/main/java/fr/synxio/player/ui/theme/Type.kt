package fr.synxio.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import fr.synxio.player.R

/*
 * Typographie Synxio « Lumen ».
 *
 * Deux voix :
 *  - Bricolage Grotesque pour ce qui s'affiche (titres d'écran, lecteur, sections) :
 *    une grotesque expressive, un peu imprimée, qui évoque une pochette plus qu'une app
 *    de bureau ;
 *  - Manrope pour ce qui se lit (listes, réglages, durées) : très lisible en petit,
 *    chiffres nets.
 *
 * Les polices viennent du fournisseur Google Fonts des services Play : rien n'alourdit
 * l'APK. Sans services Play, ou avant le premier téléchargement, Compose retombe tout
 * seul sur la police système : l'app reste lisible, seule la voix change.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun family(name: String, vararg weights: FontWeight): FontFamily {
    val font = GoogleFont(name)
    return FontFamily(weights.map { Font(googleFont = font, fontProvider = provider, weight = it) })
}

object SynxioFonts {
    val display: FontFamily = family(
        "Bricolage Grotesque",
        FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
    )
    val body: FontFamily = family(
        "Manrope",
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
    )
}

private val Display = SynxioFonts.display
private val Body = SynxioFonts.body

/**
 * Échelle typographique : titres serrés et denses façon pochette, corps aéré pour rester
 * lisible sur les fonds sombres et teintés.
 */
val SynxioTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 54.sp,
        lineHeight = 56.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.05.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * Surtitre en capitales espacées (« EN LECTURE », « ALBUM », « POUR VOUS ») : la petite
 * étiquette qui précède un grand titre. Toujours le même, partout.
 */
val EyebrowTextStyle = TextStyle(
    fontFamily = Body,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.8.sp,
)

/** Chiffres à chasse fixe (durées, compteurs) : rien ne tressaute quand ils changent. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** Style des paroles synchronisées : gros, très lisible en un coup d'œil. */
val LyricsTextStyle = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Bold,
    fontSize = 27.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp,
    textAlign = TextAlign.Start,
)
