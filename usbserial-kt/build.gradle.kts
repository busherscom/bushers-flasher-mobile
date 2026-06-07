plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    // Format/lint every module (library + sample).
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
}

// Coverage + API docs for the library module only.
dependencies {
    kover(project(":usbserial"))
    add("dokka", project(":usbserial"))
}

kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
