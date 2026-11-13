package io.github.rupakc.travelplanner

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

/** Review findings 2, 3 and 7: `hide_game` gates celebrations, the offline purge follows the server flag, per-user work re-runs. */
class MainViewModelSessionTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository(Session("t", User("alice"), userId = "u1"))
    private val game = FakeGameEvents().also { it.setVisible(false) }
    private val trips = FakeTripsRepository()
    private val packs = FakeOfflinePackRepository()
    private var hideGame = true
    private var hideCommunity = false
    private var settingsCalls = 0

    private val account =
        mockk<AccountRepository> {
            coEvery { settings() } coAnswers {
                settingsCalls++
                game.setVisible(!hideGame)
                game.setCommunityHidden(hideCommunity)
                AppResult.Success(AccountSettings(hideGame = hideGame, hideCommunity = hideCommunity))
            }
        }

    private fun vm(features: FeaturesDto): MainViewModel {
        val api = mockk<ClientConfigApi>()
        coEvery { api.config() } returns Response.success(ClientConfigDto(features = features))
        val cache = mockk<JourneyFlagsCache>(relaxed = true)
        every { cache.read() } returns null
        return MainViewModel(
            auth,
            FakeProfileRepository(TravellerProfile(onboardingCompleted = true)),
            FakeSettingsRepository(),
            FakeConsentRepository(),
            FakeTelemetry(),
            api,
            trips,
            cache,
            account,
            packs,
            game,
        )
    }

    @Test fun `celebrations stay hidden while hide_game is on and show once it is off`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(FeaturesDto(accounts = true, game = true))
            assertEquals(1, settingsCalls)
            assertFalse(vm.state.value.shellMode().gameEnabled)
            hideGame = false
            // Saving the setting in Account pushes the new value (AccountRepositoryImpl.patchSettings).
            game.setVisible(true)
            assertTrue(vm.state.value.shellMode().gameEnabled)
        }

    @Test fun `a different user starts hidden and reads their own hide_game`() =
        runTest(timeout = 30.seconds) {
            hideGame = false
            val vm = vm(FeaturesDto(accounts = true, game = true))
            assertTrue(vm.state.value.shellMode().gameEnabled)
            hideGame = true
            auth.sessionFlow.value = Session("t2", User("bob"), userId = "u2")
            assertEquals(2, settingsCalls)
            assertFalse(vm.state.value.shellMode().gameEnabled)
        }

    @Test fun `hide_community drops the social entries and missing means shown`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(FeaturesDto(accounts = true, game = true, social = 1))
            assertTrue(vm.state.value.shellMode().social)
            game.setCommunityHidden(true)
            assertFalse(vm.state.value.shellMode().social)
            game.setCommunityHidden(false)
            assertTrue(vm.state.value.shellMode().social)
        }

    @Test fun `hide_community is read and applied when the game is off and social is on`() =
        runTest(timeout = 30.seconds) {
            hideCommunity = true
            val vm = vm(FeaturesDto(accounts = true, game = false, social = 1))
            assertEquals(1, settingsCalls)
            assertFalse(vm.state.value.shellMode().social)
            assertFalse(vm.state.value.shellMode().gameEnabled)
        }

    @Test fun `the game flag off keeps celebrations off`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(FeaturesDto(accounts = true, game = false))
            assertEquals(0, settingsCalls)
            assertFalse(vm.state.value.shellMode().gameEnabled)
        }

    @Test fun `the trip count is fetched again for the next signed-in user`() =
        runTest(timeout = 30.seconds) {
            vm(FeaturesDto(accounts = true, trips = true, journey = true))
            assertEquals(1, trips.calls.count { it.startsWith("list:") })
            auth.sessionFlow.value = Session("t2", User("bob"), userId = "u2")
            assertEquals(2, trips.calls.count { it.startsWith("list:") })
        }

    @Test fun `offline data is purged when the server reports offline_pack off, not when on`() =
        runTest(timeout = 30.seconds) {
            vm(FeaturesDto(accounts = true, trips = true, offlinePack = true))
            assertFalse(packs.calls.contains("purgeAll"))
            vm(FeaturesDto(accounts = true, trips = true, offlinePack = false))
            assertTrue(packs.calls.contains("purgeAll"))
        }
}
