package io.github.rupakc.travelplanner.e2e

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage

/**
 * Saves a full-screen PNG (status bar included) through the test storage service when the run passes
 * `-Pe2eScreenshots=true`; AGP copies the files to `app/build/outputs/connected_android_test_additional_output/`.
 */
object Screenshots {
    private val enabled get() = InstrumentationRegistry.getArguments().getString("e2eScreenshots") == "true"

    fun capture(name: String) {
        if (!enabled) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }
        bitmap.recycle()
    }

    /** A rule that saves `fail-<test>.png` when a test fails (screenshots enabled only). */
    fun onFailure(): org.junit.rules.TestWatcher =
        object : org.junit.rules.TestWatcher() {
            override fun failed(e: Throwable?, description: org.junit.runner.Description) {
                runCatching { capture("fail-" + description.methodName) }
            }
        }

    private const val QUALITY = 100
}
