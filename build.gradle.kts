import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("kapt") version "2.2.21"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("org.graalvm.buildtools.native") version "0.11.2"
    id("org.jetbrains.kotlinx.kover") version "0.9.3"
    id("org.sonarqube") version "7.0.1.6134"
    id("idea")
    application
}

group = "io.github.pintowar"

val javaLangVersion = JavaLanguageVersion.of(21)
val javaVendor = JvmVendorSpec.matching("GraalVM Community")

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
            filter(ReplaceTokens::class, "tokens" to mapOf("version" to version))
        }
    }

    test {
        useJUnitPlatform()
    }
}

sonarqube {
    properties {
        val sonarToken = project.findProperty("sonar.token")?.toString() ?: System.getenv("SONAR_TOKEN")
        val koverPath =
            project.layout.buildDirectory
                .dir("reports/kover/xml")
                .get()
                .asFile.absolutePath

        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.organization", "pintowar")
        property("sonar.projectName", "bellum")
        property("sonar.projectKey", "pintowar_bellum")
        property("sonar.projectVersion", project.version.toString())
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.token", sonarToken)
        property("sonar.verbose", true)
        property("sonar.github.repository", "pintowar/bellum")
        property("sonar.coverage.jacoco.xmlReportPaths", "$koverPath/report.xml")
    }
}

tasks.sonarqube {
    dependsOn(tasks.koverXmlReport)
}
