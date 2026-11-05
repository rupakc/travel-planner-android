import io.github.rupakc.travelplanner.buildlogic.Sdk
import io.github.rupakc.travelplanner.buildlogic.configureKotlinCompile
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Pure Kotlin module (no Android). Android Lint runs on it too, and the root
 * task names used by CI (`testLocalDebugUnitTest`, `lintLocalDebug`) are
 * aliased to `test` / `lint` so one root command covers every module.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("kotlin-jvm"))
            apply(plugin = libs.pluginId("android-lint"))
            apply(plugin = "travelplanner.unit.test")
            extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(Sdk.JAVA) }
            configureKotlinCompile()
            tasks.register("testLocalDebugUnitTest") {
                group = "verification"
                description = "Alias of :test for a pure Kotlin module."
                dependsOn("test")
            }
            tasks.register("lintLocalDebug") {
                group = "verification"
                description = "Alias of :lint for a pure Kotlin module."
                dependsOn("lint")
            }
        }
    }
}
