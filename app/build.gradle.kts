import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Signature de publication. Les secrets vivent dans keystore.properties (non versionné) :
// jamais dans le dépôt. Sans ce fichier, seul le build debug est possible.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "fr.synxio.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.synxio.player"
        // 26 = Android 8.0 : icônes adaptatives + canaux de notification natifs
        minSdk = 26
        // 36 = Android 16. L'API 37 n'existe qu'en canal preview du SDK : on ne cible pas
        // un SDK canary, ce serait impubliable et instable.
        targetSdk = 36
        versionCode = 20260929
        versionName = "3.1.4"
        vectorDrawables.useSupportLibrary = true

        // Le dépôt devient public, l'app peut donc chercher ses mises à jour ici directement
        buildConfigField("String", "UPDATE_REPO", "\"Gomze31/synxio-player\"")

        // Identifiant d'application Discord. Contrairement au client secret, il est
        // public par conception : il est embarqué dans tout client qui l'utilise.
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"1547937346253492415\"")

        // Scrobbling Last.fm : renseigne lastfmApiKey / lastfmSecret dans
        // ~/.gradle/gradle.properties. Vides = fonctionnalité masquée dans les réglages.
        buildConfigField("String", "LASTFM_API_KEY", "\"${properties["lastfmApiKey"] ?: ""}\"")
        buildConfigField("String", "LASTFM_SECRET", "\"${properties["lastfmSecret"] ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
            if (storePath != null && rootProject.file(storePath).exists()) {
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                // Keystore de release officiel embarqué pour garantir une signature
                // 100% identique entre GitHub Actions, les APKs et les mises à jour in-app.
                storeFile = file("synxio-release.keystore")
                storePassword = "synxioplayer"
                keyAlias = "synxio"
                keyPassword = "synxioplayer"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            // UnstableApi de Media3 n'est pas un marqueur @RequiresOptIn : c'est du lint,
            // inutile de l'ajouter ici.
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }

    lint {
        disable.add("UnsafeOptInUsageError")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Analytics & Crashlytics
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // ML Kit Translate
    implementation("com.google.mlkit:translate:17.0.3")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.cast)
    implementation(libs.play.services.cast.framework)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.jaudiotagger)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    
    // Synxio Party (Local Web Server & QR Code)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.2")

    testImplementation(libs.junit)
}
