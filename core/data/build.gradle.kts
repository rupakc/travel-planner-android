plugins {
    alias(libs.plugins.travelplanner.android.library)
    alias(libs.plugins.travelplanner.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(projects.core.testing)
}
