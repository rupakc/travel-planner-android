import com.android.build.api.dsl.LibraryExtension
import io.github.rupakc.travelplanner.buildlogic.configureAndroidCommon
import io.github.rupakc.travelplanner.buildlogic.lib
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("android-library"))
            apply(plugin = "travelplanner.unit.test")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
                testOptions.targetSdk = io.github.rupakc.travelplanner.buildlogic.Sdk.TARGET
                // Resources are opt-in per module (designsystem, ui, features, notifications).
                buildFeatures.androidResources = false
            }
            dependencies {
                add("implementation", libs.lib("kotlinx-coroutines-android"))
            }
        }
    }
}
