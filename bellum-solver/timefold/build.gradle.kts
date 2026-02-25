plugins {
    id("bellum.base")
    `java-library`
}

dependencies {
    implementation(project(":bellum-core"))
    implementation(libs.timefold.core)

    testImplementation(testFixtures(project(":bellum-core")))
}
