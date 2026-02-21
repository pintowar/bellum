plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

fun convertToPluginDependency(plugin: Provider<PluginDependency>): String {
    val pluginId = plugin.get().pluginId
    val pluginVersion = plugin.get().version
    return "$pluginId:$pluginId.gradle.plugin:$pluginVersion"
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(convertToPluginDependency(libs.plugins.kotlin.jvm))
    implementation(convertToPluginDependency(libs.plugins.kotlin.serialization))
    implementation(convertToPluginDependency(libs.plugins.ktlint))
}