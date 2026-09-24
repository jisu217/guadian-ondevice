/*
 * AGP 9 provides Kotlin support here; applying a separate Kotlin Android
 * plugin would create a duplicate extension.
 */
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

    sourceSets {
        getByName("main") {
            // Shared with Python training so runtime and training use one source.
            resources.directories.addAll(listOf("../model/data", "../model/artifacts"))
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

    // Lets instrumented tests wait for JudgeActivity callbacks without sleeps.
    implementation("androidx.test.espresso:espresso-idling-resource:3.7.0")

    // Explicit versions because this module does not apply a Kotlin plugin id.
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.21")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // Local JVM tests need a real org.json implementation; android.jar stubs it.
    testImplementation("org.json:json:20260814")

    // Robolectric gives accessibility tree helpers a real JVM-testable Android API.
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
