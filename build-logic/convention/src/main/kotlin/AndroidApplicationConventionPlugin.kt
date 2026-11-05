import com.android.build.api.dsl.ApplicationExtension
import io.github.rupakc.travelplanner.buildlogic.Sdk
import io.github.rupakc.travelplanner.buildlogic.configureAndroidCommon
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("android-application"))
            apply(plugin = "travelplanner.unit.test")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = Sdk.TARGET
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                defaultConfig.vectorDrawables.useSupportLibrary = true
                buildFeatures.buildConfig = true
                buildTypes.getByName("release").apply {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    // No release keystore exists yet: CI's assembleProdRelease is debug-signed
                    // (see docs/Release.md). Never ship this build to a store.
                    signingConfig = signingConfigs.getByName("debug")
                }
                buildTypes.getByName("debug").apply {
                    applicationIdSuffix = ".debug"
                    isPseudoLocalesEnabled = true
                }
            }
        }
    }
}
