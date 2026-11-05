plugins {
    alias(libs.plugins.travelplanner.android.library)
    alias(libs.plugins.travelplanner.android.compose)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.ui"
    buildFeatures.androidResources = true
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(projects.core.domain)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
}
