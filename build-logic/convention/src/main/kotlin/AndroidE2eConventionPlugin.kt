import com.android.build.api.dsl.ApplicationExtension
import io.github.rupakc.travelplanner.buildlogic.lib
import io.github.rupakc.travelplanner.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Instrumented end-to-end tests (`app/src/androidTest`) that drive the real app against a real backend.
 *
 * They are opt-in: CI only runs unit tests, and a plain `connectedLocalDebugAndroidTest` skips every test annotated
 * `@E2eTest`. Run them with `-Pe2e=true` (see docs/Testing.md). Optional properties, passed to the tests as
 * instrumentation arguments: `e2eBaseUrl` (default `http://10.0.2.2:8031`, the emulator's view of the host),
 * `e2eAdminPassword` (the backend's `ADMIN_PASSWORD`) , `e2eFakeSerpUrl` (default
 * `http://10.0.2.2:9100`) and `e2eScreenshots` (`true` saves README screenshots).
 *
 * Each test runs in its own process with the app's data cleared (Android Test Orchestrator, `clearPackageData`),
 * so tests are independent and share state only through the backend.
 */
class AndroidE2eConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val e2e = providers.gradleProperty("e2e").map(String::toBoolean).orElse(false).get()
            val baseUrl = providers.gradleProperty("e2eBaseUrl").orElse("http://10.0.2.2:8031").get()
            val adminPassword = providers.gradleProperty("e2eAdminPassword").orElse("e2e-Test-Pass1").get()
            val fakeSerp = providers.gradleProperty("e2eFakeSerpUrl").orElse("http://10.0.2.2:9100").get()
            val screenshots = providers.gradleProperty("e2eScreenshots").orElse("false").get()
            extensions.configure<ApplicationExtension> {
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                val args = defaultConfig.testInstrumentationRunnerArguments
                args["clearPackageData"] = "true"
                // Screenshots go through the test storage service; AGP copies them to
                // build/outputs/connected_android_test_additional_output.
                args["useTestStorageService"] = "true"
                val marker = "io.github.rupakc.travelplanner.e2e.E2eTest"
                if (e2e) {
                    args["annotation"] = marker
                    args["e2eBaseUrl"] = baseUrl
                    args["e2eAdminPassword"] = adminPassword
                    args["e2eScreenshots"] = screenshots
                    args["e2eFakeSerpUrl"] = fakeSerp
                } else {
                    args["notAnnotation"] = marker
                }
                testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"
                testOptions.animationsDisabled = true
            }
            // The tested APK already ships androidx.concurrent + listenablefuture (older, pinned by AGP's consistent
            // resolution), and Compose's ATF wrapper drags in Material Components (View widgets the suite never uses).
            // Leaving them out of the test APK avoids version conflicts; at run time the app's copies are used.
            configurations.matching { it.name.endsWith("AndroidTestRuntimeClasspath") }.configureEach {
                exclude(mapOf("group" to "androidx.concurrent"))
                exclude(mapOf("group" to "com.google.guava", "module" to "listenablefuture"))
                exclude(mapOf("group" to "com.google.android.material"))
            }
            dependencies {
                add("androidTestImplementation", libs.lib("androidx-test-runner"))
                add("androidTestImplementation", libs.lib("androidx-test-rules"))
                add("androidTestImplementation", libs.lib("androidx-test-core"))
                add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.lib("androidx-test-espresso-core"))
                add("androidTestImplementation", libs.lib("androidx-test-espresso-intents"))
                add("androidTestImplementation", libs.lib("androidx-test-uiautomator"))
                add("androidTestImplementation", libs.lib("androidx-compose-ui-test-junit4"))
                add("androidTestImplementation", libs.lib("androidx-compose-ui-test-junit4-accessibility"))
                add("androidTestImplementation", libs.lib("androidx-test-services-storage"))
                add("androidTestImplementation", libs.lib("okhttp"))
                add("androidTestImplementation", libs.lib("kotlinx-serialization-json"))
                add("androidTestImplementation", libs.lib("junit"))
                add("androidTestUtil", libs.lib("androidx-test-orchestrator"))
                add("androidTestUtil", libs.lib("androidx-test-services"))
            }
        }
    }
}
