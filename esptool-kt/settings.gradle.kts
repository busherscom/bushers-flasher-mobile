rootProject.name = "esptool-kt"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") } // usb-serial-for-android
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

include(":core")
include(":jvm-serial")
include(":cli")
// Phase 2: Android library (.aar) + sample app
include(":android")
include(":sample-app")
