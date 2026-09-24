plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.guardian"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

sourceSets {
    main {
        kotlin.srcDir("android/app/src/main/kotlin")
        // official_domains.txt lives with the training data so Python and
        // Kotlin read the exact same file instead of two hand-synced copies.
        resources.srcDir("model/data")
        // The trained model artifact is also a runtime resource so Kotlin uses
        // the same model parameters that Python training/evaluation generated.
        resources.srcDir("model/artifacts")
        resources.exclude("**/.gitkeep")
    }
    test {
        kotlin.srcDir("android/app/src/test/kotlin")
    }
}

tasks.test {
    useJUnitPlatform()
}
