package io.github.rupakc.travelplanner.feature.alerts.savings

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.alerts.AlertsFlags
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAlertsV2Repository
import io.github.rupakc.travelplanner.core.testing.fake.FakeJourneyFlagsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeAlertsV2Repository()
    private val flags = FakeJourneyFlagsRepository(JourneyFlags(alerts = AlertsFlags(alertsApi = 2, priceAlertsV2 = true, livePrices = true)))

    @Test
    fun `loads the tally when Alerts 2 is on`() =
        runTest(timeout = TIMEOUT) {
            repo.summaryResult = AppResult.Success(Savings(totalUsd = 80.0, dropsCaught = 2))
            val s = SavingsViewModel(repo, flags).state.value as SavingsUiState.Loaded
            assertEquals(80.0, s.view.totalUsd, 0.0)
            assertEquals(false, s.livePricesPaused)
        }

    @Test
    fun `without live prices the tally still loads and says checks are paused`() =
        runTest(timeout = TIMEOUT) {
            flags.flags = JourneyFlags(alerts = AlertsFlags(priceAlertsV2 = true, livePrices = false))
            assertTrue((SavingsViewModel(repo, flags).state.value as SavingsUiState.Loaded).livePricesPaused)
        }

    @Test
    fun `an older backend is never asked`() =
        runTest(timeout = TIMEOUT) {
            flags.flags = JourneyFlags()
            repo.summaryResult = AppResult.Failure(AppError.Unknown())
            assertEquals(SavingsUiState.Hidden, SavingsViewModel(repo, flags).state.value)
        }

    @Test
    fun `a failure keeps the backend text and retry recovers`() =
        runTest(timeout = TIMEOUT) {
            repo.summaryResult = AppResult.Failure(AppError.RateLimited("Too many requests", 30))
            val vm = SavingsViewModel(repo, flags)
            assertEquals(SavingsUiState.Failed("Too many requests"), vm.state.value)
            repo.summaryResult = AppResult.Success(Savings(dropsCaught = 1, totalUsd = 5.0))
            vm.onEvent(SavingsEvent.Retry)
            assertTrue(vm.state.value is SavingsUiState.Loaded)
        }

    private companion object {
        val TIMEOUT = kotlin.time.Duration.parse("10s")
    }
}
