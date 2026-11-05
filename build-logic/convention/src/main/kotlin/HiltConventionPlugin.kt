import io.github.rupakc.travelplanner.buildlogic.lib
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/** Hilt via KSP for an Android module (apply after the android plugin). */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("ksp"))
            apply(plugin = libs.pluginId("hilt"))
            dependencies {
                add("implementation", libs.lib("hilt-android"))
                add("ksp", libs.lib("hilt-compiler"))
                add("testImplementation", libs.lib("hilt-android-testing"))
                add("kspTest", libs.lib("hilt-compiler"))
            }
        }
    }
}
