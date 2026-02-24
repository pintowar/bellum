plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenLocal()
    mavenCentral()
}

val javaLangVersion: JavaLanguageVersion = JavaLanguageVersion.of(25)
val javaVendor: JvmVendorSpec = JvmVendorSpec.GRAAL_VM

java {
    toolchain {
        languageVersion.set(javaLangVersion)
        vendor.set(javaVendor)
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(javaLangVersion)
        vendor.set(javaVendor)
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.nop)

    testImplementation(libs.mockk.jvm)
    testImplementation(libs.bundles.kotest)
}

tasks {
    test {
        useJUnitPlatform()
    }
}