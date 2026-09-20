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
