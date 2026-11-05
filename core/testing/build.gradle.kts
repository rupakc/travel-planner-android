plugins {
    alias(libs.plugins.travelplanner.android.library)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.testing"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(projects.core.data)
    api(projects.core.network)
    api(projects.core.datastore)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
}
