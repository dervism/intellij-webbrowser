import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "no.dervis.webbrowser"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Build against the IntelliJ IDEA already installed on this machine, so we don't
// download a ~1 GB IDE distribution. The path is overridable so it isn't baked in:
//   * -PlocalIdePath=/path/to/IDE on the command line
//   * localIdePath=... in this project's or your ~/.gradle/gradle.properties
//   * LOCAL_IDE_PATH=... environment variable
// Falls back to the default macOS install location.
val localIdePath: String =
    providers.gradleProperty("localIdePath").orNull
        ?: providers.environmentVariable("LOCAL_IDE_PATH").orNull
        ?: "/Applications/IntelliJ IDEA.app"

if (!file(localIdePath).exists()) {
    logger.warn(
        "IntelliJ-WebBrowser: configured IDE path does not exist: $localIdePath — " +
            "override it with -PlocalIdePath=/path/to/IDE, a localIdePath gradle property, " +
            "or the LOCAL_IDE_PATH environment variable.",
    )
}

dependencies {
    intellijPlatform {
        local(localIdePath)
    }

    // Arrow functional core (Either / Option / raise DSL). The Kotlin stdlib it
    // depends on is already provided by the IntelliJ Platform at runtime, so it's
    // excluded to avoid bundling a duplicate.
    implementation("io.arrow-kt:arrow-core:2.2.2.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

intellijPlatform {
    // Skip building searchable options: it launches a headless IDE and is not
    // needed for a single settings field. Speeds up `buildPlugin` noticeably.
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            // 261 == 2026.1. untilBuild is set deliberately wide so the plugin
            // keeps loading across future IDE upgrades (personal-use tradeoff).
            sinceBuild = "261"
            untilBuild = "299.*"
        }
    }
}

kotlin {
    compilerOptions {
        // Target Java 21 bytecode: it runs on the IDE's JBR 25 and stays broadly
        // compatible, while compiling fine with the JDK 24/25 on this machine.
        jvmTarget = JvmTarget.JVM_21
    }
}
