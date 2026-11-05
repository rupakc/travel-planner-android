import com.android.build.api.dsl.CommonExtension
import io.github.rupakc.travelplanner.buildlogic.lib
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/** Compose for an Android application or library module (apply after the android plugin). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("compose-compiler"))
            val android = extensions.getByType(CommonExtension::class.java)
            android.buildFeatures.compose = true
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("config/compose/stability.conf"),
                )
            }
            dependencies {
                val bom = libs.lib("androidx-compose-bom")
                add("implementation", platform(bom))
                add("testImplementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-foundation"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
                add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
                add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            }
        }
    }
}
