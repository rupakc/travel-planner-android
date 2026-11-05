package io.github.rupakc.travelplanner.e2e

/**
 * Marks an instrumented test that needs the real backend (see docs/Testing.md, "Instrumented end to end").
 * Only `-Pe2e=true` runs these; otherwise the runner filters them out.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class E2eTest
