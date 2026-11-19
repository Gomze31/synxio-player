import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Modèle et constantes partagés entre le téléphone (:app) et la montre (:wear).
// JVM pur : aucune dépendance Android, testable sans émulateur.
// Cible 17 comme :app, compilé avec le JDK courant (pas de toolchain : aucun JDK 17
// installé à part, et aucun dépôt de téléchargement configuré).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
