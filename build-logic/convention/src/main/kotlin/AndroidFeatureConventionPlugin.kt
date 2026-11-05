import io.github.rupakc.travelplanner.buildlogic.lib
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module: Android library + Compose + Hilt + type-safe navigation.
 * Enforces the dependency rule: features see core:{ui,designsystem,data,domain,model,common} only.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "travelplanner.android.library")
            apply(plugin = "travelplanner.android.compose")
            apply(plugin = "travelplanner.hilt")
            apply(plugin = libs.pluginId("kotlin-serialization"))
            extensions.getByType(com.android.build.api.dsl.LibraryExtension::class.java)
                .buildFeatures.androidResources = true
            dependencies {
                listOf("ui", "designsystem", "data", "domain", "model", "common").forEach {
                    add("implementation", project(":core:$it"))
                }
                add("testImplementation", project(":core:testing"))
                add("implementation", libs.lib("androidx-navigation-compose"))
                add("implementation", libs.lib("androidx-hilt-navigation-compose"))
                add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("kotlinx-serialization-json"))
                add("testImplementation", libs.lib("androidx-navigation-testing"))
            }
        }
    }
}
