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
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.9"
}
rootProject.name = "bellum"

include(":bellum-core")
include(":bellum-solver:choco", ":bellum-solver:jenetics", ":bellum-solver:timefold")
include(":bellum-cli")

kover {
    enableCoverage()
}

gitHooks {
    commitMsg { conventionalCommits() } // Applies the default conventional commits configuration
    createHooks()
}