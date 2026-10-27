import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    jacoco
}

group = "no.dervis.webbrowser"
version = "0.7.0"

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

        // Embedded browser (JCEF) support. As of 2026.2 JCEF lives in its own
        // bundled plugin (com.intellij.modules.jcef) rather than the core
        // platform, so its classes (com.intellij.ui.jcef.*, org.cef.*) must be
        // pulled in explicitly to be on the compile/test classpath. The matching
        // runtime dependency is declared in META-INF/plugin.xml.
        bundledPlugin("com.intellij.modules.jcef")

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
    implementation("io.arrow-kt:arrow-core:2.3.0") {
        exclude(group = "org.jetbrains.kotlin")
    }

    // Unit-test stack: kotlin.test wired to JUnit 5 for the pure domain tests.
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.4")

    // JUnit 4 (which also exposes junit.framework.TestCase — the JUnit 3 superclass
    // BasePlatformTestCase extends) plus the vintage engine so those JUnit 3/4-style
    // platform tests run under the same JUnit Platform invocation as the JUnit 5 tests.
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.14.4")
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
    // 0.8.13+ adds experimental support for recent Java releases — required
    // because the Gradle daemon and test JVM run on the installed JDK (25+, e.g.
    // JDK 27); older JaCoCo silently no-ops on newer JVMs and the report comes
    // back at 0% coverage.
    toolVersion = "0.8.16"
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
            // 262 == 2026.2. Raised from 261 because 2026.2 extracted JCEF into a
            // separate bundled plugin (com.intellij.modules.jcef); the plugin.xml
            // dependency that fixes the 2026.2 NoClassDefFoundError only resolves
            // on 262+. 2026.1 users continue to receive the previous release.
            // No untilBuild — the plugin stays loadable on all newer IDE versions;
            // the JetBrains Plugin Verifier (below) guards against cross-version breakage.
            sinceBuild = "262"
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

// IntelliJ 2026.2 bundles JBR 25 and ships some platform classes (e.g. the JCEF
// module's JBCefApp) as Java 25 bytecode, so the IntelliJ Platform Gradle Plugin
// needs a toolchain that can read them — JDK 25 or newer. Rather than pin an
// exact version (which breaks every time the installed JDK is bumped, e.g. 25 →
// 26 → 27) or provision a separate JDK, reuse the JDK that is already running
// Gradle (JAVA_HOME). It must be 25+. Output bytecode is still Java 21 (see the
// jvmTarget / release settings below), so the plugin runs on the IDE's JBR 25.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(Runtime.version().feature())
    }
}

kotlin {
    compilerOptions {
        // Target Java 21 bytecode: runs on the IDE's JBR 25 and stays broadly
        // compatible. NOTE: the 25+ compile toolchain overrides this extension-level
        // value, so it is re-asserted per-task in the configureEach block below.
        jvmTarget = JvmTarget.JVM_21
        // Use the JVM's native default-method dispatch for Java interface defaults
        // instead of generating Kotlin synthetic forwarder methods. Keeps the
        // bytecode clean and stops the JetBrains Plugin Verifier from reporting
        // our classes as "overriding" inherited defaults they don't actually touch.
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

// The 25+ compile toolchain makes KGP set -jvm-target to the toolchain version,
// which overrides the extension-level jvmTarget above and would emit bytecode the
// IDE's JBR 25 cannot load (e.g. Java 27). Pin -jvm-target back to 21 on every
// Kotlin compile task; configureEach runs at task realization (after the toolchain
// wiring), so this value wins. Emitted bytecode is then Java 21, runnable on JBR 25+.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

// Match the Java compile tasks to the same target (21). With the 25+ toolchain
// javac would default to the toolchain release, which both emits unloadable
// bytecode and trips KGP's Kotlin/Java target-consistency check against Kotlin 21.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
