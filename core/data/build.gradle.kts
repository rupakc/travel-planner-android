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
    api(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.work.testing)
}
