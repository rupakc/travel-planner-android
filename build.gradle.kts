plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "build-logic/**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**", ".idea/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(files("config/detekt/detekt.yml"))
    // No baseline file: every finding must be fixed or explicitly suppressed in code.
    source.setFrom(
        fileTree(rootDir) {
            include("app/src/**/*.kt", "core/**/src/**/*.kt", "feature/**/src/**/*.kt", "build-logic/convention/src/**/*.kt")
            exclude("**/build/**")
        },
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

// detekt 1.23.x embeds the Kotlin 2.0 compiler; keep its own classpath on that version
// even though the build uses Kotlin 2.4 (see MEMORY.md).
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}
