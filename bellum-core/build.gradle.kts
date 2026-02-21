plugins {
    id("bellum.base")
    `java-library`
    `java-test-fixtures`
}

dependencies {
    implementation(libs.konform.jvm)
    implementation(libs.uuid.generator)
    implementation(libs.jgrapht.core)
    implementation(libs.commons.math3)
}
