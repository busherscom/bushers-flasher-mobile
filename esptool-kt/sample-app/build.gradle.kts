plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

description = "esptool-kt Android sample: flash an ESP32-S2 over USB-OTG"

android {
    namespace = "io.github.ajsb85.esptoolkt.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.ajsb85.esptoolkt.sample"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":android"))
    implementation(libs.androidx.core.ktx)
}
