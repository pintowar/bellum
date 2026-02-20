import net.researchgate.release.ReleaseExtension

plugins {
    base
    id("idea")
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.release)
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

allprojects {
    group = "io.github.pintowar"
    description = "Kotlin based scheduling system that optimizes task assignment to employees"
}

tasks {
    named("sonar") {
        dependsOn(koverXmlReport)
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
                path.set(file("$rootDir/build/native/nativeCompile/bellum-osx-aarch_64"))
                platform.set("osx-aarch_64")
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
