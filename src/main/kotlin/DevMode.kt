package de.serup

/**
 * True only when running in the development sandbox IDE started via `./gradlew runIde`
 * (see the `de.serup.devMode` system property set on that task in build.gradle.kts),
 * never in a real installation - the equivalent of Angular's `isDevMode()`.
 */
object DevMode {
    val isActive: Boolean = System.getProperty("de.serup.devMode") != null
}
