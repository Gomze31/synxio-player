# Wearable Data Layer : services instanciés par Play Services.
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
-dontwarn com.google.android.gms.wearable.**

# Tuile et complication : instanciées par le système.
-keep class * extends androidx.wear.tiles.TileService { *; }
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class fr.synxio.player.** {
    *** Companion;
}
-keepclasseswithmembers class fr.synxio.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
