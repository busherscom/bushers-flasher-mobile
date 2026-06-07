plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    // Format/lint every module — JVM (core/jvm-serial/cli) and Android (android/sample-app) alike.
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint("1.5.0").editorConfigOverride(
                mapOf(
                    "max_line_length" to "off",
                    "ktlint_standard_function-naming" to "disabled",
                ),
            )
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.5.0")
        }
    }

    // Android modules: treat warnings as errors too.
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            compilerOptions { allWarningsAsErrors.set(true) }
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
            compilerOptions {
                allWarningsAsErrors.set(true)
            }
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
        // Measure coverage and generate API docs for the JVM-side modules.
        apply(plugin = "org.jetbrains.kotlinx.kover")
        apply(plugin = "org.jetbrains.dokka")

        extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
            moduleName.set(project.name)
            dokkaSourceSets.configureEach {
                val moduleDoc = project.layout.projectDirectory.file("Module.md").asFile
                if (moduleDoc.exists()) includes.from(moduleDoc)
                sourceLink {
                    localDirectory.set(project.projectDir.resolve("src"))
                    remoteUrl.set(
                        uri("https://github.com/ajsb85/esptool-kt/tree/main/${project.name}/src"),
                    )
                    remoteLineSuffix.set("#L")
                }
            }
        }
    }
}

// Aggregate coverage across the testable modules.
dependencies {
    kover(project(":core"))
    kover(project(":jvm-serial"))
    kover(project(":cli"))
    add("dokka", project(":core"))
    add("dokka", project(":jvm-serial"))
    add("dokka", project(":cli"))
}

kover {
    reports {
        filters {
            excludes {
                // Hardware/CLI I/O wrappers are validated against real silicon, not unit tests.
                classes(
                    "io.github.ajsb85.esptoolkt.jvm.*",
                    "io.github.ajsb85.esptoolkt.cli.*",
                )
            }
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
