import net.researchgate.release.ReleaseExtension
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.release)
    id("idea")
    application
}

group = "io.github.pintowar"

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
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.nop)

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
    toolchainDetection.set(true)
    binaries {
        named("main") {
            buildArgs.add("-H:IncludeResources=application\\.properties")
            buildArgs.add("--enable-url-protocols=https")
            buildArgs.add("--rerun-class-initialization-at-runtime=kotlin.DeprecationLevel")
        }
        named("test") {
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

    named("sonar") {
        dependsOn(koverXmlReport)
    }

    register<JavaExec>("runWithNativeImageAgent") {
        group = "native"
        description = "Run the app on the JVM with native-image agent to generate config for native-image"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(application.mainClass)

        // the agent writes configs into the path you choose. This matches the files referenced above.
        jvmArgs = listOf("-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image")
        standardInput = System.`in`

        // Add these lines to process command-line arguments
        if (project.hasProperty("args")) {
            // Splits the single string of arguments into a list
            args((project.property("args") as String).split(" "))
        }
    }
}

configure<ReleaseExtension> {
    tagTemplate.set("v\$version")
    with(git) {
        requireBranch.set("master")
    }
}

jreleaser {
    project {
        authors.set(listOf("Thiago Oliveira Pinheiro"))
        license.set("Apache-2.0")
        copyright.set("Copyright (C) 2025 Thiago Oliveira Pinheiro")
        description.set("Kotlin based scheduling system that optimizes task assignment to employees")
        links {
            homepage.set("https://github.com/pintowar/bellum")
        }
    }
    release {
        github {
            changelog {
                enabled.set(false)
            }
            branch.set("master")
            releaseName.set("v$version")
        }
    }
    distributions {
        create("bellum") {
            distributionType.set(org.jreleaser.model.Distribution.DistributionType.BINARY)
            artifact {
                path.set(file("$rootDir/build/native/nativeCompile/bellum-linux-x86_64"))
                platform.set("linux-x86_64")
                extraProperties.put("graalVMNativeImage", true)
            }
            artifact {
                path.set(file("$rootDir/build/native/nativeCompile/bellum-osx-aarch64"))
                platform.set("osx-aarch64")
                extraProperties.put("graalVMNativeImage", true)
            }
            artifact {
                path.set(file("$rootDir/build/native/nativeCompile/bellum-windows-x86_64.exe"))
                platform.set("windows-x86_64")
                extraProperties.put("graalVMNativeImage", true)
            }
        }
    }
}

sonarqube {
    properties {
        val sonarToken = project.findProperty("sonar.token")?.toString() ?: System.getenv("SONAR_TOKEN")
        val koverPath =
            project.layout.buildDirectory
                .dir("reports/kover")
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
