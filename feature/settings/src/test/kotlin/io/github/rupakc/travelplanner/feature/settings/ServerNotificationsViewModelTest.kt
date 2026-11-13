package io.github.rupakc.travelplanner.feature.settings

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationsFlags
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.notifications.CategorySetting
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettingsUpdate
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeJourneyFlagsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.feature.settings.notifications.ListedCategories
import io.github.rupakc.travelplanner.feature.settings.notifications.QuietWindow
import io.github.rupakc.travelplanner.feature.settings.notifications.ServerNotificationsEvent
import io.github.rupakc.travelplanner.feature.settings.notifications.ServerNotificationsViewModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerNotificationsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo =
        FakeNotificationSettingsRepository(
            NotificationSettings(
                quietHours = QuietHours(true, "22:00", "07:00"),
                dailyCap = 3,
                timezone = "Asia/Kolkata",
                devices = 2,
            ),
        )
    private val flags = FakeJourneyFlagsRepository(JourneyFlags(notifications = NotificationsFlags(notificationsApi = 1)))
    private val local = FakeSettingsRepository(AppSettings(nearbyNotifications = true))
    private val telemetry = FakeTelemetry()
    private val account = io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository()

    private fun vm() = ServerNotificationsViewModel(repo, flags, local, telemetry, account)

    @Test
    fun `social and quiz are listed only with their features and a visible game, and the brief follows the trip switch`() =
        runTest(timeout = 30.seconds) {
            val api = NotificationsFlags(notificationsApi = 1)
            assertEquals(ListedCategories, vm().state.value.categories)
            flags.flags = JourneyFlags(notifications = api, social = true)
            assertEquals(ListedCategories + NotificationCategory.SOCIAL, vm().state.value.categories)
            flags.flags = JourneyFlags(notifications = api, social = true, play = true, game = true)
            assertEquals(ListedCategories + NotificationCategory.SOCIAL + NotificationCategory.QUIZ, vm().state.value.categories)
            account.settingsResult = AppResult.Success(AccountSettings(hideGame = true))
            assertEquals(ListedCategories + NotificationCategory.SOCIAL, vm().state.value.categories)
            local.state.value = AppSettings(tripNotifications = false, nearbyNotifications = true)
            val v = vm()
            assertFalse(v.state.value.isOn(NotificationCategory.MORNING_BRIEF))
            assertFalse(v.state.value.isOn(NotificationCategory.TRIP_REMINDERS))
        }

    @Test
    fun `turning the morning brief on also turns the local trip switch on`() =
        runTest(timeout = 30.seconds) {
            local.state.value = AppSettings(tripNotifications = false, nearbyNotifications = true)
            val vm = vm()
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.MORNING_BRIEF, true))
            assertTrue(local.state.value.tripNotifications)
            assertTrue(vm.state.value.isOn(NotificationCategory.MORNING_BRIEF))
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.MORNING_BRIEF, false))
            assertTrue(local.state.value.tripNotifications)
        }

    @Test
    fun `without the notifications_api flag nothing is called`() =
        runTest(timeout = 30.seconds) {
            flags.flags = JourneyFlags()
            val vm = vm()
            assertFalse(vm.state.value.available)
            assertFalse(vm.state.value.loading)
            assertEquals(0, repo.settingsCalls)
            assertTrue(telemetry.events.isEmpty())
        }

    @Test
    fun `loads settings once and emits one view event`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            val s = vm.state.value
            assertTrue(s.available && !s.loading)
            assertEquals(3, s.dailyCap)
            assertEquals("22:00", s.quietHours.start)
            vm.onEvent(ServerNotificationsEvent.Refresh)
            assertEquals(2, repo.settingsCalls)
            assertEquals(listOf("notif_settings_view"), telemetry.events.map { it.first })
        }

    @Test
    fun `a failed load shows the backend message and retry reloads`() =
        runTest(timeout = 30.seconds) {
            repo.settingsResult = AppResult.Failure(AppError.Server(503, "down"))
            val vm = vm()
            assertEquals("down", vm.state.value.loadError?.message)
            assertNull(vm.state.value.settings)
            repo.settingsResult = null
            vm.onEvent(ServerNotificationsEvent.Retry)
            assertNull(vm.state.value.loadError)
            assertNotNull(vm.state.value.settings)
        }

    @Test
    fun `a silent refresh that fails keeps the screen`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.settingsResult = AppResult.Failure(AppError.Network())
            vm.onEvent(ServerNotificationsEvent.Refresh)
            assertNull(vm.state.value.loadError)
            assertNotNull(vm.state.value.settings)
        }

    @Test
    fun `nearby category drives the local switch and shows on only when both are on`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            assertTrue(vm.state.value.isOn(NotificationCategory.NEARBY))
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.NEARBY, false))
            assertEquals(NotificationSettingsUpdate.category(NotificationCategory.NEARBY, false), repo.updates.single())
            assertFalse(local.state.value.nearbyNotifications)
            assertFalse(vm.state.value.isOn(NotificationCategory.NEARBY))
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.NEARBY, true))
            assertTrue(local.state.value.nearbyNotifications)
            assertTrue(vm.state.value.isOn(NotificationCategory.NEARBY))
            assertEquals(
                listOf("notif_category_toggle", "notif_category_toggle"),
                telemetry.events.map { it.first }.filter { it != "notif_settings_view" },
            )
            assertEquals(mapOf("category" to "nearby", "on" to false), telemetry.events[1].second)
        }

    @Test
    fun `a local switch that is off keeps the category off even when the server is on`() =
        runTest(timeout = 30.seconds) {
            local.state.value = AppSettings(nearbyNotifications = false)
            val vm = vm()
            assertFalse(vm.state.value.isOn(NotificationCategory.NEARBY))
            assertTrue(vm.state.value.isOn(NotificationCategory.PRICE_DROPS))
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.NEARBY, true))
            assertTrue(local.state.value.nearbyNotifications)
        }

    @Test
    fun `trip and price categories drive their local switches and weather stays server only`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.TRIP_REMINDERS, false))
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.PRICE_DROPS, false))
            assertFalse(local.state.value.tripNotifications)
            assertFalse(local.state.value.priceAlertNotifications)
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.WEATHER, false))
            assertFalse(vm.state.value.isOn(NotificationCategory.WEATHER))
            assertTrue(local.state.value.nearbyNotifications)
        }

    @Test
    fun `a refused save keeps the screen and the local switch and shows the backend detail`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.updateResult = AppResult.Failure(AppError.Client(422, "Unknown category", code = "notification_settings_invalid"))
            vm.onEvent(ServerNotificationsEvent.SetCategory(NotificationCategory.NEARBY, false))
            assertEquals("Unknown category", vm.state.value.saveError?.message)
            assertTrue(vm.state.value.isOn(NotificationCategory.NEARBY))
            assertTrue(local.state.value.nearbyNotifications)
            assertFalse(vm.state.value.saving)
            assertEquals(listOf("notif_settings_view"), telemetry.events.map { it.first })
            repo.updateResult = null
            vm.onEvent(ServerNotificationsEvent.SetCap(5))
            assertNull(vm.state.value.saveError)
        }

    @Test
    fun `quiet hours switch and times send the whole window`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.onEvent(ServerNotificationsEvent.SetQuietEnabled(false))
            assertEquals(QuietHours(false, "22:00", "07:00"), repo.updates.last().quietHours)
            vm.onEvent(ServerNotificationsEvent.SetQuietEnabled(true))
            vm.onEvent(ServerNotificationsEvent.SetQuietTimes("21:30", "06:00"))
            assertEquals(QuietHours(true, "21:30", "06:00"), repo.updates.last().quietHours)
            val before = repo.updates.size
            vm.onEvent(ServerNotificationsEvent.SetQuietTimes("21:30", "06:00"))
            assertEquals(before, repo.updates.size)
            assertEquals(
                listOf(false, true, true),
                telemetry.events.filter { it.first == "notif_quiet_hours_change" }.map { it.second["enabled"] },
            )
        }

    @Test
    fun `quiet hours default to a switched off window when the server has none`() =
        runTest(timeout = 30.seconds) {
            repo.current = NotificationSettings(categories = mapOf("weather" to CategorySetting(true)))
            val vm = vm()
            assertEquals(QuietWindow(false, "22:00", "07:00"), vm.state.value.quietHours)
            assertEquals(3, vm.state.value.dailyCap)
            vm.onEvent(ServerNotificationsEvent.SetQuietEnabled(true))
            assertEquals(QuietHours(true, "22:00", "07:00"), repo.updates.single().quietHours)
        }

    @Test
    fun `daily cap is clamped to 1 to 10 and a no-op sends nothing`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.onEvent(ServerNotificationsEvent.SetCap(3))
            assertTrue(repo.updates.isEmpty())
            vm.onEvent(ServerNotificationsEvent.SetCap(99))
            assertEquals(10, repo.updates.single().dailyCap)
            assertEquals(10, vm.state.value.dailyCap)
            vm.onEvent(ServerNotificationsEvent.SetCap(0))
            assertEquals(1, repo.updates.last().dailyCap)
            assertEquals(mapOf("cap" to 1), telemetry.events.last().second)
        }
}
