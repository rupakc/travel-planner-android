plugins {
    alias(libs.plugins.travelplanner.android.library)
    alias(libs.plugins.travelplanner.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
}
