plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

description = "usb-serial-for-android-kt sample: open an ESP bridge and monitor serial"

android {
    namespace = "io.github.ajsb85.usbserial.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ajsb85.usbserial.sample"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation(project(":usbserial"))
    implementation(libs.androidx.core.ktx)
}
