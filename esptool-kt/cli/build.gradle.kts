plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

description = "esptool-kt command-line interface"

dependencies {
    implementation(project(":core"))
    implementation(project(":jvm-serial"))
    implementation(libs.clikt)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    applicationName = "esptool-kt"
    mainClass.set("io.github.ajsb85.esptoolkt.cli.MainKt")
}
