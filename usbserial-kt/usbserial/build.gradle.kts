plugins {
    alias(libs.plugins.android.library)
}

description = "usb-serial-for-android-kt: a pure-Kotlin USB serial driver library for Android"

android {
    namespace = "io.github.ajsb85.usbserial"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
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
        release { isMinifyEnabled = false }
    }
}
