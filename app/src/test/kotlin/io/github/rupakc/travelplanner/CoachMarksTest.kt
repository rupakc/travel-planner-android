package io.github.rupakc.travelplanner

import io.github.rupakc.travelplanner.core.datastore.CoachMarkStore
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingGate
import io.github.rupakc.travelplanner.navigation.CoachMarks
import io.github.rupakc.travelplanner.navigation.CoachTip
import io.github.rupakc.travelplanner.navigation.ShellServicesViewModel
import io.github.rupakc.travelplanner.navigation.TopLevelDestination
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class MemoryCoachStore : CoachMarkStore {
    val state = MutableStateFlow(emptyMap<String, Set<String>>())

    override fun seen(userKey: String): Flow<Set<String>> = state.map { it[userKey].orEmpty() }

    override suspend fun markSeen(userKey: String, ids: Set<String>) {
        state.value = state.value + (userKey to (state.value[userKey].orEmpty() + ids))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoachMarksTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `one tip per tab, only with the journey tabs and a loaded state`() {
        assertEquals(CoachTip.TRIPS, CoachMarks.tipFor(TopLevelDestination.TRIPS, emptySet(), journey = true))
        assertEquals(CoachTip.EXPLORE, CoachMarks.tipFor(TopLevelDestination.EXPLORE, emptySet(), journey = true))
        assertEquals(CoachTip.PLAY, CoachMarks.tipFor(TopLevelDestination.PLAY, emptySet(), journey = true))
        assertNull(CoachMarks.tipFor(TopLevelDestination.ALERTS, emptySet(), journey = true))
        assertNull(CoachMarks.tipFor(TopLevelDestination.YOU, emptySet(), journey = true))
        assertNull(CoachMarks.tipFor(TopLevelDestination.TRIPS, emptySet(), journey = false))
        assertNull(CoachMarks.tipFor(TopLevelDestination.TRIPS, null, journey = true))
        assertNull(CoachMarks.tipFor(null, emptySet(), journey = true))
        assertEquals(setOf("trips", "explore", "play"), CoachMarks.ALL_IDS)
    }

    @Test(timeout = 30_000)
    fun `a tip is shown once per user and skip marks all three`() =
        runTest {
            val auth = FakeAuthRepository(Session("t", User(username = "alice", id = "u1")))
            val store = MemoryCoachStore()
            val vm = ShellServicesViewModel(auth, store, OnboardingGate(Optional.empty()), FakeGameEvents())

            assertEquals(CoachTip.TRIPS, CoachMarks.tipFor(TopLevelDestination.TRIPS, vm.coachSeen.value, true))
            vm.markCoachSeen(setOf("trips"))
            assertNull(CoachMarks.tipFor(TopLevelDestination.TRIPS, vm.coachSeen.value, true))
            assertEquals(CoachTip.EXPLORE, CoachMarks.tipFor(TopLevelDestination.EXPLORE, vm.coachSeen.value, true))

            vm.markCoachSeen(CoachMarks.ALL_IDS)
            assertNull(CoachMarks.tipFor(TopLevelDestination.PLAY, vm.coachSeen.value, true))

            // Another user on the same device still gets the tips; the first user does not get them back.
            auth.sessionFlow.value = Session("t2", User(username = "bob", id = "u2"))
            assertEquals(CoachTip.TRIPS, CoachMarks.tipFor(TopLevelDestination.TRIPS, vm.coachSeen.value, true))
            auth.sessionFlow.value = Session("t", User(username = "alice", id = "u1"))
            assertNull(CoachMarks.tipFor(TopLevelDestination.TRIPS, vm.coachSeen.value, true))
            assertEquals(setOf("trips", "explore", "play"), store.seen("u1").first())
        }

    @Test(timeout = 30_000)
    fun `signed out has no coach state and no onboarding check`() =
        runTest {
            val vm = ShellServicesViewModel(FakeAuthRepository(null), MemoryCoachStore(), OnboardingGate(Optional.empty()), FakeGameEvents())
            assertNull(vm.coachSeen.value)
            assertEquals(false, vm.shouldShowOnboarding())
        }
}
