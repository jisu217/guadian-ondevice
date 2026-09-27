/*
 * Root build file. Modules: :app (Android UI and Shield), :core:judge (pure
 * Kotlin judgment engine), :core:pagecontent (WebView page inspection). Model
 * training is managed outside Gradle.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
