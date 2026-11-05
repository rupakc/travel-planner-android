plugins {
    alias(libs.plugins.travelplanner.android.feature)
}

android {
    namespace = "io.github.rupakc.travelplanner.feature.results"
}

dependencies {
    // Walking-tour images (Team E); sized and crash-safe, http(s) only.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
