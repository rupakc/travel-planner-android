import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import io.github.rupakc.travelplanner.buildlogic.Sdk
import io.github.rupakc.travelplanner.buildlogic.VerifyHiltViewModelsTask
import io.github.rupakc.travelplanner.buildlogic.configureAndroidCommon
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

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
            registerHiltViewModelGuard()
        }
    }

    /**
     * `verifyHiltViewModels<Variant>` for every release variant; `assemble`/`bundle`/`package<Variant>` depend on it,
     * so an APK/AAB whose Hilt graph lost a ViewModel (stale incremental KSP output) can never be produced.
     */
    private fun Project.registerHiltViewModelGuard() {
        val sourceTree =
            rootProject.fileTree(rootProject.layout.projectDirectory) {
                include("**/src/main/**/*.kt", "**/src/main/**/*.java")
                exclude("**/build/**", "build-logic/**", ".gradle/**")
            }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().withBuildType("release")) { variant ->
                val cap = variant.name.replaceFirstChar(Char::uppercase)
                val guard =
                    tasks.register<VerifyHiltViewModelsTask>("verifyHiltViewModels$cap") {
                        group = "verification"
                        description = "Checks that every @HiltViewModel is bound in the ${variant.name} Hilt component and R8 dex."
                        variantName.set(variant.name)
                        sources.from(sourceTree)
                        componentSources.from(layout.buildDirectory.dir("generated/hilt/component_sources/${variant.name}"))
                        dependsOn("hiltJavaCompile$cap")
                        mappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
                        dexFiles.from(files(tasks.named("minify${cap}WithR8")).asFileTree.matching { include("**/*.dex") })
                        report.set(layout.buildDirectory.file("reports/hilt-viewmodels/${variant.name}.txt"))
                    }
                // Packaging too, so a broken APK/AAB is never even written to build/outputs.
                val gated = setOf("assemble$cap", "bundle$cap", "package$cap", "package${cap}Bundle")
                tasks.matching { it.name in gated }.configureEach { dependsOn(guard) }
            }
        }
    }
}
