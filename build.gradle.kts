plugins {
    kotlin("jvm") version "2.2.20"
    id("idea")
}

group = "io.github.pintowar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.konform:konform-jvm:0.11.0")
    implementation("org.threeten:threeten-extra:1.8.0")
    implementation("org.choco-solver:choco-solver:4.10.18")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.1")
    implementation("org.jgrapht:jgrapht-core:1.5.2")

    testImplementation("io.mockk:mockk-jvm:1.14.6")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.4")
    testImplementation("io.kotest:kotest-assertions-core:6.0.4")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
