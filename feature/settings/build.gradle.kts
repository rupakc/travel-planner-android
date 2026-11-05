plugins {
    alias(libs.plugins.travelplanner.android.feature)
}

android {
    namespace = "io.github.rupakc.travelplanner.feature.settings"
}

dependencies {
    implementation(libs.androidx.appcompat)
}
