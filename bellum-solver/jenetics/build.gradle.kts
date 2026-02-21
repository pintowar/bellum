plugins {
    id("bellum.base")
    `java-library`
}

dependencies {
    implementation(project(":bellum-core"))
    implementation(libs.jenetics)

    testImplementation(testFixtures(project(":bellum-core")))
}
