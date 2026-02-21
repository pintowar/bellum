plugins {
    id("bellum.base")
}

dependencies {
    implementation(libs.konform.jvm)
    implementation(libs.choco.solver)
    implementation(libs.uuid.generator)
    implementation(libs.jgrapht.core)
    implementation(libs.commons.math3)
}
