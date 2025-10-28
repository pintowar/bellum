plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("idea")
}

group = "io.github.pintowar"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.konform:konform-jvm:0.11.0")
    implementation("org.choco-solver:choco-solver:4.10.18")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.1")
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation("org.apache.commons:commons-math3:3.6.1")

    implementation("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:4.11.2")

    testImplementation("io.mockk:mockk-jvm:1.14.6")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.4")
    testImplementation("io.kotest:kotest-assertions-core:6.0.4")
    testImplementation("io.kotest:kotest-assertions-konform-jvm:6.0.4")
}

tasks.test {
    useJUnitPlatform()
}
