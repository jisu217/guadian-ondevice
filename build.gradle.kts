// AGP 9+ has built-in Kotlin support enabled by default, so no separate
// org.jetbrains.kotlin.android plugin is applied here (applying both fails
// with "Cannot add extension with name 'kotlin'").
plugins {
    id("com.android.application") version "9.4.1"
}

android {
    namespace = "com.guardian.ondevice"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.guardian.ondevice"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("android/app/src/main/AndroidManifest.xml")
            kotlin.srcDirs("android/app/src/main/kotlin")
            res.srcDirs("android/app/src/main/res")
            // official_domains.txt and the trained model artifact live with the
            // training data so Python and Kotlin read the exact same files
            // instead of hand-synced copies.
            resources.srcDirs("model/data", "model/artifacts")
        }
        getByName("test") {
            kotlin.srcDirs("android/app/src/test/kotlin")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all { it.useJUnitPlatform() }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Explicit versions instead of the kotlin("test") DSL shorthand: that
    // shorthand infers its version from an applied org.jetbrains.kotlin.*
    // plugin, and this project has none now that AGP 9's built-in Kotlin
    // support replaced it (see the plugins block above).
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.21")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}
