# Media3 / ExoPlayer embarque ses propres règles consommateur : garder tout le paquet
# gonflait l'APK et empêchait R8 d'optimiser le chemin de lecture.
-dontwarn androidx.media3.**

# jaudiotagger (édition de tags) utilise de la réflexion + des logs JUL
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
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
