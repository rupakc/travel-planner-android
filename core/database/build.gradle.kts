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

android {
    // Exported schemas feed MigrationTestHelper.
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}
