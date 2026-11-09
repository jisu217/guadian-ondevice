/*
 * llama.cpp inside the app: the native build plus the LlmRuntime that calls it.
 * Separate from :core:llm because it needs the Android NDK; everything testable
 * on the JVM (prompt, chat template, answer reading) stays in :core:llm.
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.guadian.ondevice.llamacpp"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // Phones and Apple-silicon emulators are arm64.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                // Inference is unusably slow unoptimized, so even debug builds compile llama.cpp in release mode.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    externalNativeBuild {
        cmake { path("src/main/cpp/CMakeLists.txt") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:llm"))
    implementation(libs.kotlinx.coroutines.android)
}
