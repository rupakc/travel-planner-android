plugins {
    alias(libs.plugins.travelplanner.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
}
