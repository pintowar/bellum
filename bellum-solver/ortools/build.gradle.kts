plugins {
    id("bellum.base")
    `java-library`
}

dependencies {
    implementation(project(":bellum-core"))
    implementation(libs.or.tools)

    testImplementation(testFixtures(project(":bellum-core")))
}
