pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Gradle fetches the JDK 17 toolchain itself: this machine has no `java` on PATH
// at all, only Android Studio's bundled JBR and a JDK under ~/tools.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fitbit-air-health"

// One module on purpose. The whole app is an OAuth handshake plus a paged GET,
// and the networking layer is plain Kotlin, so Android unit tests exercise it on
// the JVM without a second module to hold it.
include(":app")
