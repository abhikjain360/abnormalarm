import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // NOTE: AGP 9 has built-in Kotlin support — do NOT apply org.jetbrains.kotlin.android
    // (it collides: "Cannot add extension with name 'kotlin'"). The Compose compiler
    // plugin is still required and applied below.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
).all { !releaseSigningProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.abhikjain360.abnormalarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abhikjain360.abnormalarm"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Room schema export location (good practice; enables migration validation later).
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    // Under AGP 9 built-in Kotlin, kotlin.compilerOptions.jvmTarget defaults to
    // compileOptions.targetCompatibility (17 above), so no explicit kotlin{} block is needed.
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Persistence: Room (KSP) + DataStore for global settings.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Background: WorkManager (daily calendar safety-net worker).
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines (explicit; transitively present but pinned for clarity).
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Calendar API authorization. REST calls are made directly to keep the APK small.
    implementation(libs.play.services.auth)

    // Pure-JVM unit tests (the nextOccurrence engine lives here — no Android deps).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
