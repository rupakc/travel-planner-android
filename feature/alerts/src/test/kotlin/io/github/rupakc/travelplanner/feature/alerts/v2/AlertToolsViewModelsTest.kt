package io.github.rupakc.travelplanner.feature.alerts.v2

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.AlertSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.DailyCap
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.alerts.SuggestionsResponse
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeQuickWatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertToolsViewModelsTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val spec = JsonObject(mapOf("name" to JsonPrimitive("Lumen")))

    private fun suggestion(title: String, already: Boolean = false) =
        AlertSuggestion(kind = "hotel", title = title, subtitle = "Lisbon", spec = spec, reason = "selected_in_plan", alreadyWatching = already)

    private fun suggestionsVm(q: FakeQuickWatch) = AlertSuggestionsViewModel(q.flags, q.alerts, q.watcher, q.telemetry)

    private fun prefsVm(q: FakeQuickWatch) = AlertPrefsViewModel(q.alerts, q.telemetry)

    @Test fun `tools show nothing against an older backend and both with live prices`() =
        runTest(timeout = 30.seconds) {
            val off = suggestionsVm(FakeQuickWatch()).state.value
            assertFalse(off.prefsAvailable)
            assertFalse(off.suggestionsAvailable)
            val on = suggestionsVm(FakeQuickWatch.on()).state.value
            assertTrue(on.prefsAvailable && on.suggestionsAvailable)
        }

    @Test fun `opening loads suggestions, shown is reported once with the count`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(suggestion("A"), suggestion("B", already = true)), livePrices = true))
            val vm = suggestionsVm(q)
            vm.open()
            val ready = vm.state.value.load as SuggestionsLoad.Ready
            assertEquals(listOf("A", "B"), ready.rows.map { it.suggestion.title })
            assertTrue(ready.rows[1].watching)
            assertEquals(listOf("alert_suggest" to mapOf("action" to "shown", "count" to 2)), q.telemetry.events)
        }

    @Test fun `suggestions are hidden when the server says live prices are off`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(suggestion("A")), livePrices = false))
            val vm = suggestionsVm(q)
            vm.open()
            assertTrue((vm.state.value.load as SuggestionsLoad.Ready).rows.isEmpty())
        }

    @Test fun `a failed load keeps the backend text and retry reloads`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.suggestionsResult = AppResult.Failure(AppError.RateLimited("Slow down", 5))
            val vm = suggestionsVm(q)
            vm.open()
            assertEquals(SuggestionsLoad.Failed("Slow down"), vm.state.value.load)
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(emptyList(), livePrices = true))
            vm.load()
            assertTrue((vm.state.value.load as SuggestionsLoad.Ready).rows.isEmpty())
        }

    @Test fun `accepting sends surface suggest, marks it added and ignores a second tap`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(suggestion("A")), livePrices = true))
            q.alerts.quickWatchResult = AppResult.Success(QuickWatchResponse("hotel", JsonObject(mapOf("id" to JsonPrimitive(3))), existing = false))
            val vm = suggestionsVm(q)
            vm.open()
            vm.accept(0)
            vm.accept(0)
            val sent = q.alerts.quickWatchCalls.single()
            assertEquals("suggest", sent.surface)
            assertEquals("A", sent.title)
            assertEquals(spec, sent.spec)
            assertEquals(SuggestionStatus.Added, (vm.state.value.load as SuggestionsLoad.Ready).rows.single().status)
            assertTrue(q.telemetry.events.any { it.second["action"] == "accepted" })
        }

    @Test fun `a limit keeps the row and carries the backend detail, dismiss removes a row`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(suggestion("A"), suggestion("B")), livePrices = true))
            q.alerts.quickWatchResult = AppResult.Failure(AppError.Client(409, "Limit of 20", "watch_limit"))
            val vm = suggestionsVm(q)
            vm.open()
            vm.accept(0)
            val rows = (vm.state.value.load as SuggestionsLoad.Ready).rows
            assertEquals(SuggestionStatus.Blocked(SuggestionBlock.LIMIT, "Limit of 20"), rows[0].status)
            vm.dismiss(1)
            assertEquals(listOf("A"), (vm.state.value.load as SuggestionsLoad.Ready).rows.map { it.suggestion.title })
            assertTrue(q.telemetry.events.any { it.second["action"] == "dismissed" })
        }

    private val prefs =
        AlertPrefs(
            digestWeekly = true,
            digestDay = 1,
            instant = "all",
            quietHours = QuietHours(true, "22:00", "07:00"),
            dailyCap = DailyCap(3, 3, 3, 3),
            groupWindowMin = 15,
            timezone = "Asia/Kolkata",
        )

    @Test fun `prefs load, save a cap as a full daily_cap and report one telemetry field`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.preferencesResult = AppResult.Success(prefs)
            q.alerts.updatePreferencesResult = AppResult.Success(prefs.copy(dailyCap = DailyCap(3, 7, 3, 3)))
            val vm = prefsVm(q)
            assertEquals(prefs, (vm.state.value as AlertPrefsUiState.Content).prefs)
            vm.setCap("hotel", 7)
            assertEquals(DailyCap(3, 7, 3, 3), q.alerts.preferenceUpdates.single().dailyCap)
            assertEquals(7, (vm.state.value as AlertPrefsUiState.Content).prefs.dailyCap?.hotel)
            assertEquals(listOf("alert_prefs_change" to mapOf("field" to "cap")), q.telemetry.events)
        }

    @Test fun `a second change while a save is in flight is ignored`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.preferencesResult = AppResult.Success(prefs)
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            q.alerts.updatePreferencesGate = gate
            val vm = prefsVm(q)
            vm.setCap("hotel", 7)
            assertTrue((vm.state.value as AlertPrefsUiState.Content).saving)
            vm.setCap("hotel", 8)
            vm.setGroupWindow(30)
            assertEquals(1, q.alerts.preferenceUpdates.size)
            gate.complete(Unit)
            assertFalse((vm.state.value as AlertPrefsUiState.Content).saving)
            vm.setGroupWindow(30)
            assertEquals(2, q.alerts.preferenceUpdates.size)
        }

    @Test fun `caps and the group window are clamped before sending`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.preferencesResult = AppResult.Success(prefs)
            val vm = prefsVm(q)
            vm.setCap("flight", 50)
            vm.setGroupWindow(500)
            assertEquals(10, q.alerts.preferenceUpdates[0].dailyCap?.flight)
            assertEquals(120, q.alerts.preferenceUpdates[1].groupWindowMin)
        }

    @Test fun `alert_prefs_invalid keeps the confirmed values and says so`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.preferencesResult = AppResult.Success(prefs)
            q.alerts.updatePreferencesResult = AppResult.Failure(AppError.Client(422, "bad", "alert_prefs_invalid"))
            val vm = prefsVm(q)
            vm.setQuietTimes("21:00", "21:00")
            val content = vm.state.value as AlertPrefsUiState.Content
            assertEquals(PrefsError.Invalid, content.error)
            assertEquals(prefs, content.prefs)
            assertTrue(q.telemetry.events.isEmpty())
        }

    @Test fun `quiet hours toggle keeps the stored times and a load failure can retry`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.preferencesResult = AppResult.Failure(AppError.Network())
            val vm = prefsVm(q)
            assertEquals(AlertPrefsUiState.Failed(null), vm.state.value)
            q.alerts.preferencesResult = AppResult.Success(prefs)
            vm.load()
            vm.setQuietEnabled(false)
            assertEquals(QuietHours(false, "22:00", "07:00"), q.alerts.preferenceUpdates.single().quietHours)
        }
}
