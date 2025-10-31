import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.processResources
import org.gradle.kotlin.dsl.test

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("kapt") version "2.2.21"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("org.graalvm.buildtools.native") version "0.11.2"
    id("idea")
    application
}

group = "io.github.pintowar"

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Picocli for command-line parsing
    implementation("info.picocli:picocli:4.7.5")
    kapt("info.picocli:picocli-codegen:4.7.5") // Annotation processor for GraalVM

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

application {
    mainClass.set("io.github.pintowar.bellum.cli.MainKt")
}

kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
}

graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-H:IncludeResources=application\\.properties")
            buildArgs.add("--enable-url-protocols=https")
        }
    }
}

tasks {
    processResources {
        filesMatching("**/application.properties") {
            filter(ReplaceTokens::class, "tokens" to mapOf("version" to project.version))
        }
    }

    test {
        useJUnitPlatform()
    }
}
