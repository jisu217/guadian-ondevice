// AGP 9+ has built-in Kotlin support enabled by default, so no separate
// org.jetbrains.kotlin.android plugin is applied here (applying both fails
// with "Cannot add extension with name 'kotlin'").
plugins {
    id("com.android.application")
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // src/main, src/test, src/androidTest are all standard AGP locations now
    // that this module actually lives at app/ — no manual srcDirs needed,
    // except for the two resource folders that intentionally live outside
    // this module so Python training can read the exact same files.
    sourceSets {
        getByName("main") {
            // official_domains.txt and the trained model artifact live with the
            // training data (repo root, one level up) so Python and Kotlin read
            // the exact same files instead of hand-synced copies.
            resources.srcDirs("../model/data", "../model/artifacts")
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

    // Lets JudgeActivity signal Espresso when a judge() call is in flight, so
    // instrumented tests wait for the real callback (sync for rule/model/RAG,
    // async for the remote executor) instead of guessing with Thread.sleep.
    implementation("androidx.test.espresso:espresso-idling-resource:3.7.0")

    // Explicit versions instead of the kotlin("test") DSL shorthand: that
    // shorthand infers its version from an applied org.jetbrains.kotlin.*
    // plugin, and this project has none now that AGP 9's built-in Kotlin
    // support replaced it (see the plugins block above).
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.21")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // org.json.* ships as part of the Android platform on a real device or
    // emulator, but local `test` unit tests run against a stub android.jar
    // whose org.json methods are no-ops. This standalone build of the same
    // API gives local unit tests a real implementation; it's test-only and
    // never ships in the APK, so there's no conflict with the platform copy.
    testImplementation("org.json:json:20260814")

    // Robolectric gives AccessibilityNodeInfo/Bundle a real, working
    // implementation (unlike the no-op stub android.jar used above), so
    // WebAccessibilityNode's node-reading functions can run as local JVM
    // tests instead of only being verifiable by hand on an emulator.
    // Robolectric's own @RunWith(RobolectricTestRunner::class) is JUnit4, so
    // it runs through the vintage engine alongside the project's JUnit5
    // tests on the same useJUnitPlatform() test task.
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
