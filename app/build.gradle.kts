import java.util.Properties

plugins {
    alias(libs.plugins.travelplanner.android.application)
    alias(libs.plugins.travelplanner.android.compose)
    alias(libs.plugins.travelplanner.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.travelplanner.android.e2e)
}

// Firebase is optional (spec §7): FCM is wired but inert until app/google-services.json is supplied.
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) apply(plugin = libs.plugins.google.services.get().pluginId)

val prodBaseUrl: String = providers.gradleProperty("PROD_BASE_URL").orElse("").get()

// Release signing (docs/Release.md): environment variables or a git-ignored keystore.properties (local). Without either,
// release stays signed with the debug key so local and PR builds keep working; -PVOYAGER_REQUIRE_RELEASE_SIGNING=true
// turns that fallback into a build failure. -PVOYAGER_UNSIGNED_RELEASE=true builds release unsigned instead: CI does
// that and signs with apksigner in a separate step, so the key password never reaches Gradle.
val keystoreProps =
    Properties().apply {
        providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull?.let { load(it.reader()) }
    }

fun signingValue(env: String, prop: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("VOYAGER_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("VOYAGER_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("VOYAGER_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("VOYAGER_KEY_PASSWORD", "keyPassword") ?: releaseStorePassword
val hasReleaseKey = releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null
val requireReleaseKey = providers.gradleProperty("VOYAGER_REQUIRE_RELEASE_SIGNING").orNull == "true"
val unsignedRelease = providers.gradleProperty("VOYAGER_UNSIGNED_RELEASE").orNull == "true"
check(!(unsignedRelease && requireReleaseKey)) { "VOYAGER_UNSIGNED_RELEASE and VOYAGER_REQUIRE_RELEASE_SIGNING are mutually exclusive" }
check(unsignedRelease || hasReleaseKey || !requireReleaseKey) {
    "VOYAGER_REQUIRE_RELEASE_SIGNING=true but no release key: set VOYAGER_KEYSTORE_FILE, VOYAGER_KEYSTORE_PASSWORD and VOYAGER_KEY_ALIAS"
}

// Published builds pass a strictly increasing versionCode and a matching versionName; local builds keep the defaults.
val appVersionCode: Int = providers.gradleProperty("VOYAGER_VERSION_CODE").orNull?.toInt() ?: 1
val appVersionName: String = providers.gradleProperty("VOYAGER_VERSION_NAME").orNull ?: "0.1.0"
val webBaseUrl: String = providers.gradleProperty("WEB_BASE_URL").orElse("").get()

android {
    namespace = "io.github.rupakc.travelplanner"

    defaultConfig {
        applicationId = "io.github.rupakc.travelplanner"
        versionCode = appVersionCode
        versionName = appVersionName
        resValue("bool", "fcm_enabled", hasGoogleServices.toString())
        buildConfigField("String", "WEB_BASE_URL", "\"$webBaseUrl\"")
        // Resource configs shipped (spec §5: en default + es).
        androidResources.localeFilters += listOf("en", "es", "de", "fr", "hi", "zh", "bn")
    }

    buildFeatures {
        resValues = true
    }

    if (unsignedRelease) {
        buildTypes.getByName("release").signingConfig = null
    } else if (hasReleaseKey) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = if (releaseStoreFile.endsWith(".jks")) "JKS" else "PKCS12"
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    productFlavors {
        getByName("local") {
            // Today's local-only backend: emulator host loopback; cleartext allowed (src/local network_security_config).
            buildConfigField("String", "DEFAULT_BASE_URL", "\"http://10.0.2.2:8001\"")
            buildConfigField("boolean", "CLEARTEXT_ALLOWED", "true")
            applicationIdSuffix = ".local"
        }
        getByName("prod") {
            // HTTPS only; no host baked in beyond the PROD_BASE_URL build property.
            buildConfigField("String", "DEFAULT_BASE_URL", "\"$prodBaseUrl\"")
            buildConfigField("boolean", "CLEARTEXT_ALLOWED", "false")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.notifications)

    implementation(projects.feature.auth)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.home)
    implementation(projects.feature.search)
    implementation(projects.feature.results)
    implementation(projects.feature.plan)
    implementation(projects.feature.chat)
    implementation(projects.feature.alerts)
    implementation(projects.feature.settings)
    implementation(projects.feature.nearby)
    implementation(projects.feature.play)
    implementation(projects.feature.share)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.navigation.testing)
}

// Review M8 / R2-M3: fail if R8 removed anything kotlinx.serialization needs from core:model in the prod release:
// a generated `$$serializer`, a custom `*Serializer` object, a `Companion` field or a `serializer()` member.
val verifyReleaseKeepRules by tasks.registering {
    group = "verification"
    description = "Checks the prodRelease R8 usage report: core:model serializers kept, AdminApi removed."
    dependsOn("minifyProdReleaseWithR8")
    val usage = layout.buildDirectory.file("outputs/mapping/prodRelease/usage.txt")
    inputs.file(usage)
    doLast {
        val modelPrefix = "io.github.rupakc.travelplanner.core.model."
        val serializerMember = Regex("""(\sCompanion;?$)|(KSerializer serializer\()""")
        var owner: String? = null
        val problems = mutableListOf<String>()
        usage.get().asFile.forEachLine { line ->
            if (!line.startsWith(" ")) {
                val name = line.removeSuffix(":")
                owner = if (line.endsWith(":")) name else null
                if (!line.endsWith(":") && name.startsWith(modelPrefix) && (name.endsWith("\$\$serializer") || name.endsWith("Serializer"))) {
                    problems += "removed class $name"
                }
            } else {
                val cls = owner
                if (cls != null && cls.startsWith(modelPrefix) && serializerMember.containsMatchIn(line.trim())) problems += "$cls: ${line.trim()}"
            }
        }
        check(problems.isEmpty()) { "R8 removed serialization members:\n" + problems.joinToString("\n") }
        // AdminApi has no UI (admin is web-only): it must not ship in release.
        val adminApi = "io.github.rupakc.travelplanner.core.network.api.AdminApi"
        check(usage.get().asFile.useLines { lines -> lines.any { it == adminApi || it == "$adminApi:" } }) {
            "AdminApi was kept in the release build; it should be removed by R8"
        }
    }
}
