import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.sonarqube)
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
    implementation(libs.picocli)
    kapt(libs.picocli.codegen)

    implementation(libs.konform.jvm)
    implementation(libs.choco.solver)
    implementation(libs.uuid.generator)
    implementation(libs.jgrapht.core)
    implementation(libs.commons.math3)

    implementation(libs.lets.plot.kotlin.jvm)
    implementation(libs.lets.plot.image.export)

    testImplementation(libs.mockk.jvm)
    testImplementation(libs.bundles.kotest)
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
