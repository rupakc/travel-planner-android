plugins {
    alias(libs.plugins.travelplanner.android.library)
    alias(libs.plugins.travelplanner.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.network"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(projects.core.datastore)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(projects.core.testing)
}
