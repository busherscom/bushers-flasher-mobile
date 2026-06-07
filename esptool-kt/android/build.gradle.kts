plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

description = "esptool-kt Android library (.aar): USB-serial transport over usb-serial-for-android"

android {
    namespace = "io.github.ajsb85.esptoolkt.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26 // java.util.Base64 (stub decoding) requires API 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Reuse the platform-agnostic protocol engine unchanged.
    api(project(":core"))
    api(project(":usbserial"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "esptool-kt-android"
            afterEvaluate { from(components["release"]) }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ajsb85/esptool-kt")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}
