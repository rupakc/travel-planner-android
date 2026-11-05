import io.github.rupakc.travelplanner.buildlogic.lib
import io.github.rupakc.travelplanner.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Shared unit-test dependencies and settings (JUnit4, coroutines-test, Turbine, MockK, Robolectric). */
class UnitTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                add("testImplementation", libs.lib("junit"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("testImplementation", libs.lib("turbine"))
                add("testImplementation", libs.lib("mockk"))
            }
            listOf("com.android.application", "com.android.library").forEach { id ->
                pluginManager.withPlugin(id) {
                    dependencies {
                        add("testImplementation", libs.lib("robolectric"))
                        add("testImplementation", libs.lib("androidx-test-core"))
                        add("testImplementation", libs.lib("androidx-test-ext-junit"))
                    }
                }
            }
            tasks.withType<Test>().configureEach {
                maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                systemProperty("robolectric.logging.enabled", "false")
                testLogging { events("failed") }
                // Modules with only KSP-generated test sources (Hilt) and no tests yet must not fail the build.
                failOnNoDiscoveredTests.set(false)
            }
        }
    }
}
