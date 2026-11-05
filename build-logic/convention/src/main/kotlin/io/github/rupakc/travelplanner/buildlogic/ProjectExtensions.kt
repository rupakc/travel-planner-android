package io.github.rupakc.travelplanner.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

/** Shared SDK levels (spec §4). */
object Sdk {
    /** 37: the pinned AndroidX/OkHttp releases require compileSdk >= 37 (AAR metadata). targetSdk stays 36. */
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
    const val JAVA = 21
}

/** Product flavour dimension present in every Android module so root task names line up. */
object Flavors {
    const val DIMENSION = "env"
    const val LOCAL = "local"
    const val PROD = "prod"
}
