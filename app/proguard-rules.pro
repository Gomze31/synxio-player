# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# jaudiotagger (Édition de tags) utilise de la réflexion + des logs JUL
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-keep class org.jcodec.** { *; }
-dontwarn org.jcodec.**
-dontwarn java.awt.**
-dontwarn javax.swing.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class fr.synxio.player.** {
    *** Companion;
}
-keepclasseswithmembers class fr.synxio.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Journaux : en release, R8 supprime les appels Log.v / Log.d / Log.i (titres, chemins,
# trames réseau…). Log.w et Log.e sont conservés pour le diagnostic des erreurs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Widgets Glance : les actions sont retrouvées par leur nom de classe et instanciées
# par réflexion au clic. Renommées ou sans constructeur vide, les boutons du widget ne
# feraient plus rien en release.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class fr.synxio.player.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }

# Téléchargement YouTube (youtubedl-android). Au premier lancement, la bibliothèque
# décompresse Python avec commons-compress, qui instancie ses classes « extra field »
# par réflexion : R8 retirait leurs constructeurs et l'app plantait au téléchargement
# (« ExceptionInInitializerError … is not a concrete class »). Jackson lit les réponses
# de yt-dlp dans les classes de la bibliothèque, par réflexion lui aussi.
-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# Commande vocale (Vosk). JNA relie les fonctions natives aux méthodes Java par leur nom
# et lit ses structures par réflexion : renommées par R8, le chargement du modèle échoue.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
