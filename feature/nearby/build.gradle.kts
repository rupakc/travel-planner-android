plugins {
    alias(libs.plugins.travelplanner.android.feature)
}

android {
    namespace = "io.github.rupakc.travelplanner.feature.nearby"
}

dependencies {
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
}
