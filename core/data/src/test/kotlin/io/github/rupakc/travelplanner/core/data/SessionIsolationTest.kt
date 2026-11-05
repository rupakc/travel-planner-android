package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.data.impl.AuthRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.NearbyRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.SessionCleanerImpl
import io.github.rupakc.travelplanner.core.data.impl.SettingsRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.database.PlanDao
import io.github.rupakc.travelplanner.core.database.RecentSearchDao
import io.github.rupakc.travelplanner.core.database.SeenNotificationDao
import io.github.rupakc.travelplanner.core.model.auth.LoginResponse
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.network.AuthEvent
import io.github.rupakc.travelplanner.core.network.AuthEvents
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.network.api.DevicesApi
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review I6: one user's local data never leaks to the next. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionIsolationTest {
    private class CountingCleaner : SessionCleaner {
        var calls = 0

        override suspend fun clearUserData() {
            calls++
        }
    }

    private val buildInfo = AppBuildInfo("http://10.0.2.2:8001", "local", "t", false, true)

    private fun TestScope.auth(
        store: FakeSessionStore,
        cleaner: SessionCleaner,
        authApi: AuthApi = mockk(),
        devicesApi: DevicesApi = mockk(relaxed = true),
        push: PushTokenSource? = null,
    ) = AuthRepositoryImpl(
        authApi,
        devicesApi,
        store,
        Optional.ofNullable(push),
        BaseUrlProvider(FakeSettingsStore(), buildInfo),
        cleaner,
    )

    @Test
    fun `cleaner wipes draft, search, caches and user tables`() =
        runTest {
            val plan = FakeCurrentPlanStore()
            plan.toggle(PlanItem.HotelItem(Hotel(name = "A's hotel")))
            val search = FakeSearchRepository()
            search.start(TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", nationality = "American"))
            val planDao = mockk<PlanDao>(relaxed = true)
            val recent = mockk<RecentSearchDao>(relaxed = true)
            val seen = mockk<SeenNotificationDao>(relaxed = true)
            val cleaner = SessionCleanerImpl(plan, search, NearbyRepositoryImpl(mockk(), SystemAppClock()), planDao, recent, seen, FakeProfileRepository())
            cleaner.clearUserData()
            assertNull(plan.draft.value.selections.hotel)
            assertEquals(StreamPhase.IDLE, search.current.value.phase)
            coVerify { planDao.clear() }
            coVerify { recent.clear() }
            coVerify { seen.clear() }
        }

    /**
     * Regression (E2E flow 10): after admin signed out, a second account on the same phone skipped onboarding and
     * got admin's traveller type, quiz answers (incl. accessibility/dietary needs) and feature toggles.
     */
    @Test
    fun `cleaner resets the traveller profile so the next account onboards`() =
        runTest {
            val profile =
                FakeProfileRepository(
                    TravellerProfile(onboardingCompleted = true, answers = mapOf("needs" to listOf("dietary"))),
                )
            profile.setFeature("compare", false)
            SessionCleanerImpl(
                FakeCurrentPlanStore(),
                FakeSearchRepository(),
                NearbyRepositoryImpl(mockk(), SystemAppClock()),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                profile,
            ).clearUserData()
            assertEquals(TravellerProfile(), profile.profileState.value)
            assertEquals(FeatureOverrides(), profile.overridesState.value)
        }

    @Test
    fun `cleaner keeps going when one table fails`() =
        runTest {
            val plan = FakeCurrentPlanStore()
            plan.toggle(PlanItem.HotelItem(Hotel(name = "A")))
            val planDao = mockk<PlanDao>()
            coEvery { planDao.clear() } throws IllegalStateException("db closed")
            val seen = mockk<SeenNotificationDao>(relaxed = true)
            SessionCleanerImpl(
                plan,
                FakeSearchRepository(),
                NearbyRepositoryImpl(mockk(), SystemAppClock()),
                planDao,
                mockk(relaxed = true),
                seen,
                FakeProfileRepository(),
            )
                .clearUserData()
            coVerify { seen.clear() }
            assertNull(plan.draft.value.selections.hotel)
        }

    @Test
    fun `logout clears data and is bounded when unregister hangs`() =
        runTest {
            val store = FakeSessionStore(Session("t", User("alice")))
            val cleaner = CountingCleaner()
            val devices = mockk<DevicesApi>()
            coEvery { devices.unregister(any()) } coAnswers { awaitCancellation() }
            val repo = auth(store, cleaner, devicesApi = devices, push = PushTokenSource { "fcm-token" })
            repo.logout()
            assertTrue("took $currentTime ms", currentTime <= AuthRepositoryImpl.UNREGISTER_TIMEOUT_MS)
            assertNull(store.current())
            assertEquals(1, cleaner.calls)
        }

    @Test
    fun `a 401 signs out but keeps the user's data`() =
        runTest {
            val events = AuthEvents()
            val cleaner = CountingCleaner()
            val store = FakeSessionStore(Session("t", User("alice")))
            auth(store, cleaner)
            store.state.value = null // what AuthInterceptor does on a 401
            events.emit(AuthEvent.SessionExpired)
            assertEquals(0, cleaner.calls)
        }

    private fun cleanerFor(plan: FakeCurrentPlanStore) =
        SessionCleanerImpl(
            plan,
            FakeSearchRepository(),
            NearbyRepositoryImpl(mockk(), SystemAppClock()),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            FakeProfileRepository(),
        )

    private fun loginApi(): AuthApi =
        mockk<AuthApi>().also { api ->
            coEvery { api.login(any()) } answers {
                LoginResponse("tok", user = User(firstArg<io.github.rupakc.travelplanner.core.model.auth.LoginRequest>().username))
            }
        }

    @Test
    fun `token expiry then login as the same user keeps the draft`() =
        runTest {
            val plan = FakeCurrentPlanStore().apply { toggle(PlanItem.HotelItem(Hotel(name = "Alice's hotel"))) }
            val store = FakeSessionStore(Session("expired", User("alice")))
            val repo = auth(store, cleanerFor(plan), authApi = loginApi())
            store.state.value = null // the JWT expired locally / a 401 cleared the session
            repo.login("alice", "pw")
            assertEquals("Alice's hotel", plan.draft.value.selections.hotel?.name)
        }

    @Test
    fun `token expiry then login as a different user wipes the draft`() =
        runTest {
            val plan = FakeCurrentPlanStore().apply { toggle(PlanItem.HotelItem(Hotel(name = "Alice's hotel"))) }
            val store = FakeSessionStore(Session("expired", User("alice")))
            val repo = auth(store, cleanerFor(plan), authApi = loginApi())
            store.state.value = null
            repo.login("bob", "pw")
            assertNull(plan.draft.value.selections.hotel)
        }

    @Test
    fun `signing in as a different user clears the previous user's data, the same user does not`() =
        runTest {
            val api = mockk<AuthApi>()
            coEvery { api.login(any()) } answers
                { LoginResponse("tok", user = User(firstArg<io.github.rupakc.travelplanner.core.model.auth.LoginRequest>().username)) }
            val store = FakeSessionStore(Session("old", User("alice")))
            val cleaner = CountingCleaner()
            val repo = auth(store, cleaner, authApi = api)
            assertTrue(repo.login("alice", "pw") is AppResult.Success)
            assertEquals(0, cleaner.calls)
            repo.login("bob", "pw")
            assertEquals(1, cleaner.calls)
            assertEquals("http://10.0.2.2:8001", store.current()?.baseUrl)
        }

    @Test
    fun `changing server signs out and clears data`() =
        runTest {
            val session = FakeSessionStore(Session("t", User("alice")))
            val cleaner = CountingCleaner()
            val settings = FakeSettingsStore(AppSettings())
            val repo = SettingsRepositoryImpl(settings, BaseUrlProvider(settings, buildInfo), session, cleaner)
            repo.setBaseUrl("http://192.168.1.10:8001")
            assertNull(session.current())
            assertEquals(1, cleaner.calls)
        }

    @Test
    fun `expire, switch server, login as the same user wipes the draft`() =
        runTest {
            val plan = FakeCurrentPlanStore().apply { toggle(PlanItem.HotelItem(Hotel(name = "From server A"))) }
            val settings = FakeSettingsStore(AppSettings())
            val provider = BaseUrlProvider(settings, buildInfo)
            val store = FakeSessionStore(Session("expired", User("alice"), baseUrl = "http://10.0.2.2:8001"))
            val cleaner = cleanerFor(plan)
            val repo = AuthRepositoryImpl(loginApi(), mockk(relaxed = true), store, Optional.empty(), provider, cleaner)
            store.state.value = null // token expired: signed out, data kept
            SettingsRepositoryImpl(settings, provider, store, cleaner).setBaseUrl("http://192.168.1.10:8001")
            repo.login("alice", "pw")
            assertNull(plan.draft.value.selections.hotel)
            assertEquals("http://192.168.1.10:8001", store.current()?.baseUrl)
        }

    @Test
    fun `login on a different server than the stored data's wipes it even without a settings change`() =
        runTest {
            val plan = FakeCurrentPlanStore().apply { toggle(PlanItem.HotelItem(Hotel(name = "Old server"))) }
            val store = FakeSessionStore(Session("expired", User("alice"), baseUrl = "https://old.example.com"))
            store.state.value = null
            auth(store, cleanerFor(plan), authApi = loginApi()).login("alice", "pw")
            assertNull(plan.draft.value.selections.hotel)
        }

    @Test
    fun `a storage failure during login is a Failure, not a crash`() =
        runTest {
            val store =
                object : io.github.rupakc.travelplanner.core.datastore.SessionStore by FakeSessionStore() {
                    override suspend fun save(session: Session) = throw java.io.IOException("disk")
                }
            val repo =
                AuthRepositoryImpl(
                    loginApi(),
                    mockk(relaxed = true),
                    store,
                    Optional.empty(),
                    BaseUrlProvider(FakeSettingsStore(), buildInfo),
                    CountingCleaner(),
                )
            assertTrue(repo.login("alice", "pw") is AppResult.Failure)
        }
}
