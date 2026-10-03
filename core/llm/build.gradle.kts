import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The on-device LLM judgment layer. Plain Kotlin/JVM like :core:judge, so the
 * prompt, parsing, and grounding logic is tested on the JVM; the model runtime
 * that needs Android (llama.cpp) will live outside this module.
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
         * Brand data for the prompt, written by `python -m guadian_ml.resources.llm_brands`,
         * and the page inspection script the collector runs too.
         */
        resources {
            srcDir("../../ml/runtime")
            include("llm_brands.json", "page_inspection.js")
        }
    }
}

dependencies {
    api(project(":core:judge"))

    // As in :core:judge: Android ships org.json; the JVM needs it to compile and test.
    compileOnly(libs.orgjson)
    testImplementation(libs.orgjson)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
