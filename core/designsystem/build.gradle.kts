plugins {
    alias(libs.plugins.travelplanner.android.library)
    alias(libs.plugins.travelplanner.android.compose)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.designsystem"
    buildFeatures.androidResources = true
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.core.ktx)
}
