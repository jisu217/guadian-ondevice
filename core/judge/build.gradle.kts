import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The judgment engine as a plain Kotlin/JVM library: no Android dependency,
 * so an accidental `android.*` import fails to compile and its tests run on
 * the JVM without an emulator or Robolectric.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

sourceSets {
    main {
        /*
         * The runtime files this module reads, shared with Python training. Each
         * module ships only its own; the LLM brand data lives with :core:llm.
         */
        resources {
            srcDir("../../ml/runtime")
            include("official_domains.txt", "public_suffix_list.dat", "blocklist.bloom", "verdict_table.json")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    /*
     * Android ships org.json in the platform, and bundling a second copy into
     * the APK clashes with it; the JVM only needs it to compile and test.
     */
    compileOnly(libs.orgjson)
    testImplementation(libs.orgjson)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
