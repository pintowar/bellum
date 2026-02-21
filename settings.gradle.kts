pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.kotlinx.kover.aggregation") version "0.9.7"
}
rootProject.name = "bellum"

include(":bellum-core")
include(":bellum-solver:choco")
include(":bellum-cli")

kover {
    enableCoverage()
}