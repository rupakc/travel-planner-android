package io.github.rupakc.travelplanner.e2e

import android.app.NotificationManager
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.R as AppR
import io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollWorker
import io.github.rupakc.travelplanner.feature.alerts.R as AlertsR
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flow 7: a watched Lisbon hotel (the fake SerpAPI replays Lisbon hotel fixtures) -> Check now ->
 * the fake's prices drop 20 % -> Check now -> a drop alert in the in-app inbox and as a system notification
 * (POST_NOTIFICATIONS granted through the onboarding rationale + system dialog).
 */
@E2eTest
@RunWith(AndroidJUnit4::class)
class AlertsE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()
    private val api = BackendApi()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        val token = api.adminToken()
        api.resetPreferences(token)
        api.priceWatches(token).forEach { api.deletePriceWatch(token, it.jsonObject.getValue("id").jsonPrimitive.content) }
        api.setFakePriceScale(1.0)
        robot = AppRobot(rule)
    }

    @After fun tearDown() = api.setFakePriceScale(1.0)

    @Test fun hotelWatch_checkNow_dropAlert() {
        robot.signIn()
        robot.completeOnboarding()
        robot.finishOnboarding(allowNotifications = true)

        // Watch a hotel the fake SerpAPI knows. (Watching from a Results card reaches the sheet, but the AI hotels
        // are not in the fixtures and the no-live-price suggestions don't render; see the E2E report.)
        val created = api.createFixtureHotelWatch(api.adminToken())
        check(created.code == 200) { "create watch -> ${created.code} ${created.body}" }

        // Alerts: first check at today's price, then a 20 % drop.
        robot.openTab(AppR.string.nav_alerts)
        // The poller's first run for a user only records a baseline (old alerts are never posted): take it now.
        pollAlerts()
        robot.tap(AlertsR.string.alerts_check_now)
        robot.waitForText(robot.str(AlertsR.string.alerts_check_now), timeoutMs = AppRobot.LONG_TIMEOUT)
        api.setFakePriceScale(DROP)
        // The backend caches SerpAPI answers (CACHE_TTL_SECONDS, 5 s for E2E; 30 min by default).
        Thread.sleep(CACHE_WAIT_MS)
        // The drop is found by a server-side check (what the backend scheduler does twice a day). A drop found by the
        // in-app "Check now" is shown on the card and deliberately never posted again as a system notification
        // (AlertsViewModel.check marks it seen), so the server check is what exercises the poller.
        val watchId = api.priceWatches(api.adminToken()).first().jsonObject.getValue("id").jsonPrimitive.content
        check(api.call("POST", "/api/price-watches/$watchId/check", api.adminToken()).code == 200)

        // System notification: posted by the app (NotificationManager), and visible in the shade (UiAutomator).
        // Without FCM the alert poller (WorkManager) posts it.
        val manager = rule.activity.getSystemService(NotificationManager::class.java)
        pollAlerts()
        rule.waitUntil(NOTIFICATION_TIMEOUT) { manager.activeNotifications.isNotEmpty() }
        robot.device.openNotification()
        val shown = robot.device.wait(Until.findObject(By.pkg("com.android.systemui").textContains("Lumen")), NOTIFICATION_TIMEOUT)
        Screenshots.capture("notification-shade")
        robot.device.pressBack()
        assertNotNull("system notification in the shade", shown)

        // The Alerts card shows the drop after a refresh (Check now reads the server state).
        robot.tap(AlertsR.string.alerts_check_now)
        robot.waitForText(robot.str(AlertsR.string.alerts_status_dropped), timeoutMs = AppRobot.LONG_TIMEOUT)
        Screenshots.capture("alerts")

        // In-app inbox (bell) lists the alert.
        robot.tapDescription(robot.str(AlertsR.string.alerts_bell), substring = true)
        robot.waitForText(robot.str(AlertsR.string.alerts_notifications_title))
        rule.waitUntil(AppRobot.LONG_TIMEOUT) { !robot.exists(hasText(robot.str(AlertsR.string.alerts_notifications_empty))) }
        Screenshots.capture("alerts-inbox")
    }

    /**
     * Runs the alert poller now (one-off work through the app's own WorkManager) instead of waiting for its periodic
     * run. Forcing the app's scheduled jobs from the shell would also fire every pending trip reminder early.
     */
    private fun pollAlerts() {
        val request = OneTimeWorkRequestBuilder<AlertPollWorker>().build()
        val work = WorkManager.getInstance(rule.activity)
        work.enqueue(request).result.get()
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { work.getWorkInfoById(request.id).get()?.state?.isFinished == true }
    }

    private companion object {
        const val DROP = 0.8
        const val CACHE_WAIT_MS = 7_000L
        const val NOTIFICATION_TIMEOUT = 60_000L
    }
}
