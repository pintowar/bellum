import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("bellum.base")
    alias(libs.plugins.graalvm.native)
    application
}

dependencies {
    implementation(project(":bellum-core"))
    implementation(project(":bellum-solver:choco"))
    implementation(project(":bellum-solver:jenetics"))
    implementation(project(":bellum-solver:ortools"))
    implementation(libs.clikt)
}

application {
    mainClass.set("io.github.pintowar.bellum.cli.MainKt")
    applicationName = "bellum"
}

graalvmNative {
    toolchainDetection.set(true)

    agent {
        defaultMode.set("standard")
    }

    binaries {
        val platformPrefix = "ortools-$osName-$osArch"

        named("main") {
            imageName.set(application.applicationName)
            buildArgs.add("--enable-url-protocols=https")
            buildArgs.add("--rerun-class-initialization-at-runtime=kotlin.DeprecationLevel")
            buildArgs.add("--enable-native-access=ALL-UNNAMED")
            buildArgs.add("-H:+JNI")
            resources {
                includedPatterns.add("application[.]properties")
                includedPatterns.add("$platformPrefix/.*")
            }
        }
        named("test") {
            buildArgs.add("--enable-url-protocols=https")
            buildArgs.add("--rerun-class-initialization-at-runtime=kotlin.DeprecationLevel")
            buildArgs.add("--enable-native-access=ALL-UNNAMED")
            buildArgs.add("-H:+JNI")
            resources {
                includedPatterns.add("application[.]properties")
                includedPatterns.add("$platformPrefix/.*")
            }
        }
    }
}

tasks {
    processResources {
        filesMatching("**/application.properties") {
            filter(ReplaceTokens::class, "tokens" to mapOf("version" to version))
        }
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
