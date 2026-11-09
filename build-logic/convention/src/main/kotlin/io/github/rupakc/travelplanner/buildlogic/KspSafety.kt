package io.github.rupakc.travelplanner.buildlogic

import com.google.devtools.ksp.gradle.KspAATask
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withType

/** Shared lock: at most one KSP task runs at a time in a build. */
abstract class KspSerialService : BuildService<BuildServiceParameters.None>

/**
 * KSP 2.3.13 (KSP2, in-process Analysis API) silently writes empty or partial output when several KSP tasks run at
 * once in the same daemon: a module's `ksp<Variant>Kotlin` succeeds with no generated files (or Hilt reports
 * "could not be resolved" for classpath types). Reproduced locally and in CI; with `--max-workers=1` every output is
 * complete. Such a run produced the release APK that crashed after sign-in.
 *
 * So every KSP task (a) shares [KspSerialService] (`maxParallelUsages = 1`), (b) is never stored in or loaded from
 * the build cache, so an incomplete output can never spread to other builds or CI, and (c) is never up to date with an
 * empty output. (c) covers only the empty case: a partial but non-empty output from an earlier run still looks up to date
 * until the inputs change or `clean` runs; `verifyHiltViewModels<Variant>` catches it (missing ViewModels fail the
 * build) and `scripts/build-release-apk.sh` always cleans and reruns. The empty check looks at the task's declared
 * outputs, which are only the generated kotlin/java/class/resource dirs: the KSP caches dir is `@LocalState`, not an
 * output, so it cannot make an empty run look non-empty. Remove all of this once KSP fixes concurrent execution (check
 * with `verifyHiltViewModels<Variant>` on a parallel build with KSP tasks executing).
 */
internal fun Project.configureKspSafety() {
    pluginManager.withPlugin("com.google.devtools.ksp") {
        val serial =
            gradle.sharedServices.registerIfAbsent("kspSerial", KspSerialService::class) {
                maxParallelUsages.set(1)
            }
        tasks.withType<KspAATask>().configureEach {
            usesService(serial)
            outputs.cacheIf("KSP2 can write incomplete output under concurrency; never share it through the build cache") { false }
            // An empty output is never trusted: Gradle otherwise treats an earlier empty run as up to date even after
            // `clean` (empty before, missing now), which keeps a broken build broken until the inputs change.
            outputs.upToDateWhen { task -> !task.outputs.files.asFileTree.isEmpty }
        }
    }
}
