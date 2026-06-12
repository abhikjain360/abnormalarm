// Top-level build file. Plugins are declared (apply false) here and applied per-module.
plugins {
    // AGP 9 provides built-in Kotlin; the standalone kotlin-android plugin is intentionally absent.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
