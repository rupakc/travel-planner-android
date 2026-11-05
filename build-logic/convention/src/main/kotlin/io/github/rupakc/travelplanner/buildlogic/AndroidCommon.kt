package io.github.rupakc.travelplanner.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Lint issues that fail the build (accessibility + i18n), on top of Lint's own
 * fatal/error defaults. Everything else stays a warning.
 */
internal val LINT_ERRORS =
    setOf(
        // accessibility
        "ContentDescription",
        "ClickableViewAccessibility",
        "KeyboardInaccessibleWidget",
        "LabelFor",
        // i18n / l10n
        "HardcodedText",
        "SetTextI18n",
        "MissingTranslation",
        "ExtraTranslation",
        "MissingQuantity",
        "UnusedQuantity",
        "ImpliedQuantity",
        "StringFormatInvalid",
        "StringFormatCount",
        "StringFormatMatches",
        "PluralsCandidate",
        "ByteOrderMark",
        "EnforceUTF8",
        "RtlHardcoded",
        "RtlCompat",
        "RtlSymmetry",
        "RtlEnabled",
        "Typos",
        "LocaleFolder",
        "InvalidResourceFolder",
        "WrongRegion",
    )

/** Checks that only report "a newer version exists" and would make builds time-dependent. */
internal val LINT_DISABLED =
    setOf(
        "GradleDependency",
        "NewerVersionAvailable",
        "AndroidGradlePluginVersion",
        "OldTargetApi",
        "ObsoleteLintCustomCheck",
    )

internal fun Project.configureAndroidCommon(ext: CommonExtension) {
    ext.compileSdk = Sdk.COMPILE
    ext.defaultConfig.minSdk = Sdk.MIN
    ext.compileOptions.sourceCompatibility = JavaVersion.VERSION_21
    ext.compileOptions.targetCompatibility = JavaVersion.VERSION_21

    ext.flavorDimensions += Flavors.DIMENSION
    ext.productFlavors.create(Flavors.LOCAL).dimension = Flavors.DIMENSION
    ext.productFlavors.create(Flavors.PROD).dimension = Flavors.DIMENSION

    ext.lint.apply {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        checkDependencies = false
        error += LINT_ERRORS
        disable += LINT_DISABLED
        lintConfig = rootProject.file("config/lint/lint.xml")
        htmlReport = true
        xmlReport = true
        targetSdk = Sdk.TARGET
    }
    // Shared robolectric.properties (sdk=35) for every module's unit tests.
    ext.sourceSets.getByName("test").resources.srcDir(rootProject.file("config/robolectric"))
    ext.testOptions.unitTests.isIncludeAndroidResources = true
    ext.testOptions.unitTests.isReturnDefaultValues = true
    ext.packaging.resources.excludes +=
        setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    configureKotlinCompile()
}

internal fun Project.configureKotlinCompile() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        val opts = compilerOptions
        if (opts is KotlinJvmCompilerOptions) opts.jvmTarget.set(JvmTarget.JVM_21)
        opts.freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}
