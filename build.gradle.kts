import java.time.Instant

plugins {
    kotlin("jvm") version "2.0.21"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    `java-library`
}

group = "org.starbornag"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

val coroutinesVersion = "1.8.1"
val jacksonVersion = "2.17.2"
val testcontainersVersion = "1.21.3"
val cucumberVersion = "7.34.6"

dependencies {
    api("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutinesVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-suite")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    // Lets step classes share per-scenario state (a World) through constructor injection.
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")

    testImplementation("com.lemonappdev:konsist:0.17.3")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("com.willowtreeapps.assertk:assertk:0.28.1")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Appends a CUCUMBER_RUN fact to the repo's .claude/tdd-events.log for the pre-commit
// Gherkin-first ordering check. finalizedBy, not doLast: it must also run when tests fail.
val logCucumberRun = tasks.register("logCucumberRun") {
    group = "verification"
    description = "Appends a CUCUMBER_RUN fact to .claude/tdd-events.log for the pre-commit TDD-ordering check."
    // Gradle 9 writes one JUnit XML per feature file, so read Cucumber's own JSON report instead.
    val reportFile = layout.buildDirectory.file("reports/cucumber/report.json")
    val logFile = rootDir.resolve("../.claude/tdd-events.log")
    doLast {
        val json = reportFile.get().asFile.takeIf { it.exists() }?.readText() ?: return@doLast
        fun count(pattern: String) = Regex(pattern).findAll(json).count()
        val scenarios = count(""""type"\s*:\s*"scenario"""")
        // Undefined or pending steps count as red too: a new scenario without step code has not passed.
        val notPassed = count(""""status"\s*:\s*"(failed|undefined|pending|ambiguous)"""")
        val status = if (notPassed == 0) "PASS" else "FAIL"
        logFile.parentFile.mkdirs()
        logFile.appendText(
            "${Instant.now()} CUCUMBER_RUN $status module=eventstore scenarios=$scenarios notPassedSteps=$notPassed\n"
        )
    }
}

tasks.test {
    finalizedBy(logCucumberRun)
}

detekt {
    buildUponDefaultConfig = true
}

tasks.check {
    dependsOn(tasks.named("detekt"))
}
