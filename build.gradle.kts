import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
    jacoco
}

group = "no.dervis.webbrowser"
version = "0.5.7"

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

        // JetBrains Plugin Verifier — used by the `verifyPlugin` Gradle task to
        // check binary compatibility against the IDE builds listed below.
        pluginVerifier()

        // IntelliJ Platform test framework — gives us BasePlatformTestCase, light
        // project fixtures, mockable services, etc. for tests that need a real
        // Application/Project.
        testFramework(TestFrameworkType.Platform)
    }

    // Arrow functional core (Either / Option / raise DSL). The Kotlin stdlib it
    // depends on is already provided by the IntelliJ Platform at runtime, so it's
    // excluded to avoid bundling a duplicate.
    implementation("io.arrow-kt:arrow-core:2.2.2.1") {
        exclude(group = "org.jetbrains.kotlin")
    }

    // Unit-test stack: kotlin.test wired to JUnit 5 for the pure domain tests.
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")

    // JUnit 4 (which also exposes junit.framework.TestCase — the JUnit 3 superclass
    // BasePlatformTestCase extends) plus the vintage engine so those JUnit 3/4-style
    // platform tests run under the same JUnit Platform invocation as the JUnit 5 tests.
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

// The IntelliJ platform test task uses `-Djava.system.class.loader=PathClassLoader`,
// which bypasses java.lang.instrument and prevents the JaCoCo agent from seeing
// any classes load — coverage is silently zero on every class. So:
//   * `test` runs the IntelliJ-bound platform tests (no coverage).
//   * `domainTest` runs the pure-Kotlin domain tests in a plain JVM where the
//     agent works normally, and feeds the JaCoCo report.
tasks.test {
    useJUnitPlatform()
    filter { excludeTestsMatching("no.dervis.webbrowser.domain.*") }
}

val domainTest by tasks.registering(Test::class) {
    description = "Pure domain tests (no IntelliJ classloader) — JaCoCo measures coverage here."
    group = "verification"
    useJUnitPlatform()
    filter { includeTestsMatching("no.dervis.webbrowser.domain.*") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    finalizedBy(tasks.jacocoTestReport)
}

tasks.named("check") { dependsOn(domainTest) }

jacoco {
    // 0.8.13 adds experimental Java 25 support — required because the Gradle
    // daemon and test JVM both run on JDK 25; older releases silently no-op on
    // the newer JVM and the report comes back at 0% coverage.
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(domainTest)
    executionData(domainTest.get())
    reports {
        xml.required = true
        html.required = true
    }
}

intellijPlatform {
    // Skip building searchable options: it launches a headless IDE and is not
    // needed for a single settings field. Speeds up `buildPlugin` noticeably.
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            // 261 == 2026.1. No untilBuild — the plugin stays loadable on all
            // newer IDE versions; the JetBrains Plugin Verifier (configured
            // below) is the actual guard against cross-version breakage.
            sinceBuild = "261"
        }
    }

    // Configure the JetBrains Plugin Verifier targets. `recommended()` derives a
    // sensible IDE set from the since/until-build range; trade in `ide(...)`
    // entries below for an explicit list once we want tighter control. Run with:
    //   ./gradlew verifyPlugin
    // (Note: first run downloads the target IDE distributions — multi-GB; cached.)
    pluginVerification {
        ides {
            recommended()
        }
    }
}

kotlin {
    compilerOptions {
        // Target Java 21 bytecode: runs on the IDE's JBR 25 and stays broadly
        // compatible, while compiling fine with JDK 21 or newer.
        jvmTarget = JvmTarget.JVM_21
        // Use the JVM's native default-method dispatch for Java interface defaults
        // instead of generating Kotlin synthetic forwarder methods. Keeps the
        // bytecode clean and stops the JetBrains Plugin Verifier from reporting
        // our classes as "overriding" inherited defaults they don't actually touch.
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}
