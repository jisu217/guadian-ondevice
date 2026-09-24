// Root build file: only declares plugin versions for subprojects to apply
// without a version (see app/build.gradle.kts). The actual Android app
// module lives at :app — this repo is otherwise Kotlin/JVM tooling plus a
// Python model workspace and FastAPI server, none of which need a Gradle
// plugin here.
plugins {
    id("com.android.application") version "9.4.1" apply false
}
