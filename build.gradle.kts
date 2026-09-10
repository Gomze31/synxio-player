plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Le projet vit dans OneDrive, qui verrouille les fichiers pendant sa synchronisation :
// KSP et AGP échouent alors à nettoyer `build/`. Renseigner `buildDirRoot` dans
// gradle.properties déplace tous les dossiers de build hors du périmètre synchronisé.
providers.gradleProperty("buildDirRoot").orNull?.let { root ->
    allprojects {
        val name = project.path.trim(':').replace(':', '-').ifEmpty { "root" }
        layout.buildDirectory.set(File(root, name))
    }
}
