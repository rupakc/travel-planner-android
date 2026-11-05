plugins {
    alias(libs.plugins.travelplanner.android.feature)
}

android {
    namespace = "io.github.rupakc.travelplanner.feature.search"
}

dependencies {
    // Window size class (compact cards vs expanded compare table) and the Photo Picker launcher.
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.activity.compose)
}
