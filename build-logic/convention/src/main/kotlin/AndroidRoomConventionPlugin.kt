import androidx.room.gradle.RoomExtension
import io.github.rupakc.travelplanner.buildlogic.lib
import io.github.rupakc.travelplanner.buildlogic.libs
import io.github.rupakc.travelplanner.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room with KSP and an exported, committed schema directory. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.pluginId("ksp"))
            apply(plugin = libs.pluginId("room"))
            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            dependencies {
                add("implementation", libs.lib("androidx-room-runtime"))
                add("implementation", libs.lib("androidx-room-ktx"))
                add("ksp", libs.lib("androidx-room-compiler"))
                add("testImplementation", libs.lib("androidx-room-testing"))
            }
        }
    }
}
