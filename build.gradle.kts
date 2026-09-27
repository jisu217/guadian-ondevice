/*
 * Root build file. Modules: :app (Android UI and Shield), :core:judge (pure
 * Kotlin judgment engine: rules, store, local knowledge), :core:pagecontent
 * (WebView page inspection), :core:llm (on-device LLM
 * stage), :core:llamacpp (llama.cpp runtime). Model training is in ml/.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
