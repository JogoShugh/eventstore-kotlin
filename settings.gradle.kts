plugins {
    // Resolves (and downloads when missing) the Java 21 toolchains for compiling and for the
    // Gradle daemon itself; see gradle/gradle-daemon-jvm.properties.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "eventstore"
