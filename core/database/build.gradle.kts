plugins {
    alias(libs.plugins.travelplanner.android.library)
    alias(libs.plugins.travelplanner.android.room)
    alias(libs.plugins.travelplanner.hilt)
}

android {
    namespace = "io.github.rupakc.travelplanner.core.database"
}

dependencies {
    api(projects.core.model)
}
