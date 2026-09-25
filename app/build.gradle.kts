import java.util.Properties

/*
 * AGP 9 provides Kotlin support here; applying a separate Kotlin Android
 * plugin would create a duplicate extension.
 */
plugins {
    alias(libs.plugins.android.application)
}

// Remote fallback endpoint, e.g. `guadian.remoteJudgeUrl=https://...` in
// local.properties or `-Pguadian.remoteJudgeUrl=...`. Empty disables the
// remote layer: RemoteJudge then answers UNKNOWN without touching the network.
val remoteJudgeUrl: String = run {
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    (findProperty("guadian.remoteJudgeUrl") as String?)
        ?: local.getProperty("guadian.remoteJudgeUrl")
        ?: ""
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
        buildConfigField("String", "REMOTE_JUDGE_URL", "\"$remoteJudgeUrl\"")
    }

    buildFeatures {
        buildConfig = true
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

    testOptions {
        unitTests {
            all { it.useJUnitPlatform() }
        }
    }
}

dependencies {
    implementation(project(":core:judge"))
    implementation(project(":core:pagecontent"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // Robolectric gives accessibility tree helpers a real JVM-testable Android API.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
