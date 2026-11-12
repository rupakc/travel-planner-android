plugins {
    alias(libs.plugins.travelplanner.android.feature)
}

android {
    namespace = "io.github.rupakc.travelplanner.feature.social"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
