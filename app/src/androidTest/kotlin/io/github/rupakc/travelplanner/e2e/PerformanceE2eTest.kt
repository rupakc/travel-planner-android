package io.github.rupakc.travelplanner.e2e

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame stats while flinging through a streamed Results list (`dumpsys gfxinfo`), saved as `perf/results-gfxinfo.txt`
 * in the test outputs. Debug build with animations off, so treat the numbers as relative, not absolute.
 */
@E2eTest
@RunWith(AndroidJUnit4::class)
class PerformanceE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    @Test fun resultsScroll_frameStats() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        val robot = AppRobot(rule)
        robot.signInToHome()
        robot.searchTrip(AppRobot.Trip())
        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = PlanFlows.STREAM_TIMEOUT)
        PlanFlows(robot).awaitTerminal(UiR.string.section_activities)

        val pkg = rule.activity.packageName
        shell("dumpsys gfxinfo $pkg reset")
        val list = rule.onNode(hasTestTag(PlanFlows.RESULTS_LIST))
        repeat(FLINGS) { list.performTouchInput { swipeUp(durationMillis = SWIPE_MS) } }
        repeat(FLINGS) { list.performTouchInput { swipeDown(durationMillis = SWIPE_MS) } }
        rule.waitForIdle()
        val stats = shell("dumpsys gfxinfo $pkg")
        TestStorage().openOutputFile("perf/results-gfxinfo.txt").use { it.write(stats.toByteArray()) }
    }

    private companion object {
        const val FLINGS = 8
        const val SWIPE_MS = 250L
    }
}
