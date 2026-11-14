package io.github.rupakc.travelplanner

import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.config.FeaturesDto
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.network.api.ClientConfigApi
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeOfflinePackRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.FakeTripsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

/**
 * The journey flag must come from a fresh `GET /api/config`: a failed launch read is retried, and a process that
 * survives (relaunch from recents) re-reads on `refreshFlags()` instead of keeping stale or "off" flags forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelConfigRefreshTest {
    @get:Rule val main = MainDispatcherRule()

    private val on = Response.success(ClientConfigDto(features = FeaturesDto(accounts = true, trips = true, journey = true)))
    private val off = Response.success(ClientConfigDto(features = FeaturesDto(accounts = true, trips = true, journey = false)))

    private fun vm(api: ClientConfigApi, cached: JourneyFlagsCache = cache()): MainViewModel =
        MainViewModel(
            FakeAuthRepository(Session("t", User("alice"), userId = "u1")),
            FakeProfileRepository(TravellerProfile(onboardingCompleted = true)),
            FakeSettingsRepository(),
            FakeConsentRepository(),
            FakeTelemetry(),
            api,
            FakeTripsRepository(),
            cached,
            mockk(relaxed = true),
            FakeOfflinePackRepository(),
            FakeGameEvents(),
        )

    private fun cache() =
        mockk<JourneyFlagsCache>(relaxed = true).also { every { it.read() } returns null }

    @Test fun `a failed launch read is retried and turns the journey on`() =
        runTest(timeout = 30.seconds) {
            val api = mockk<ClientConfigApi>()
            var calls = 0
            coEvery { api.config() } answers { if (calls++ == 0) throw IOException("backend not up yet") else on }
            val vm = vm(api)
            assertFalse(vm.state.value.flags.journey)
            advanceTimeBy(10_000)
            assertTrue(vm.state.value.flags.journey)
        }

    @Test fun `refreshFlags replaces stale cached or earlier flags with the server's`() =
        runTest(timeout = 30.seconds) {
            val api = mockk<ClientConfigApi>()
            var answer = off
            coEvery { api.config() } answers { answer }
            val vm = vm(api)
            assertFalse(vm.state.value.flags.journey)
            answer = on
            vm.refreshFlags()
            assertTrue(vm.state.value.flags.journey)
        }

    @Test fun `a failing refresh keeps the last good flags`() =
        runTest(timeout = 30.seconds) {
            val api = mockk<ClientConfigApi>()
            var fail = false
            coEvery { api.config() } answers { if (fail) throw IOException("offline") else on }
            val vm = vm(api)
            assertTrue(vm.state.value.flags.journey)
            fail = true
            vm.refreshFlags()
            assertTrue(vm.state.value.flags.journey)
        }
}
