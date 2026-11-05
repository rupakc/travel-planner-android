package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.nearby.R as NearbyR
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flow 8: Near me asks for location (granted through the system dialog), then renders /api/nearby results or an
 * honest no_place / failure state. The test puts the phone in Lisbon with a shell test provider for `gps` (the
 * emulator console's `geo fix` did not reach the fused provider on the API 36 image) and removes it afterwards.
 */
@E2eTest
@RunWith(AndroidJUnit4::class)
class NearMeE2eTest {
    @get:Rule(order = 0) val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1) val failureShot = Screenshots.onFailure()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
        shell("appops set com.android.shell android:mock_location allow")
        shell("cmd location providers add-test-provider gps")
        shell("cmd location providers set-test-provider-enabled gps true")
        shell("cmd location providers set-test-provider-location gps --location $LISBON")
    }

    @After fun tearDown() {
        shell("cmd location providers remove-test-provider gps")
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    @Test fun nearMe_grantsLocation_rendersSuggestionsOrHonestState() {
        robot.signInToHome()
        robot.tap(robot.str(UiR.string.feature_near_me_title))
        robot.tap(NearbyR.string.nearby_allow)
        robot.allowSystemPermission()

        val results = hasText(robot.str(NearbyR.string.nearby_distance, "").trim().substringAfter(" ").ifBlank { "km from you" }, substring = true)
        val honest =
            listOf(NearbyR.string.nearby_no_place_title, NearbyR.string.nearby_failed, NearbyR.string.nearby_empty_title)
                .map { hasText(robot.str(it)) }
        val outcome = robot.waitForAny(results, *honest.toTypedArray(), timeoutMs = NEARBY_TIMEOUT)
        Screenshots.capture("near-me")
        assertTrue("outcome $outcome", outcome >= 0)
        if (outcome == 0) robot.waitForText("Lisbon", substring = true)
    }

    private companion object {
        const val NEARBY_TIMEOUT = 150_000L
        const val LISBON = "38.7223,-9.1393"
    }
}
