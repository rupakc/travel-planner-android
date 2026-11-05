plugins {
    alias(libs.plugins.travelplanner.android.library)
    alias(libs.plugins.travelplanner.hilt)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.notifications"
    buildFeatures.androidResources = true
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
}
