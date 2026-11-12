package io.github.rupakc.travelplanner.feature.account

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.auth.SessionInfo
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeAccountRepository()
    private val telemetry = FakeTelemetry()
    private val here = SessionInfo(id = "a", deviceName = "Pixel", current = true)
    private val other = SessionInfo(id = "b", deviceName = "iPad")

    @Test fun `lists the sessions with the current one marked`() =
        runTest(timeout = 30.seconds) {
            repo.sessionsResult = AppResult.Success(listOf(here, other))
            val vm = SessionsViewModel(repo, telemetry)
            assertEquals(SessionsPhase.List, vm.state.value.phase)
            assertTrue(vm.state.value.sessions.first { it.id == "a" }.current)
        }

    @Test fun `degrades to sign out everywhere only when the endpoint 404s`() =
        runTest(timeout = 30.seconds) {
            repo.sessionsResult = AppResult.Failure(AppError.NotFound("Not Found"))
            val vm = SessionsViewModel(repo, telemetry)
            assertEquals(SessionsPhase.Unsupported, vm.state.value.phase)
            // Sign out everywhere still works.
            vm.effects.test {
                vm.onEvent(SessionsEvent.AskSignOutAll)
                vm.onEvent(SessionsEvent.ConfirmSignOutAll)
                assertEquals(SessionsEffect.SignedOut, awaitItem())
            }
            assertTrue(repo.calls.contains("logoutAll"))
        }

    @Test fun `other failures show an error with retry`() =
        runTest(timeout = 30.seconds) {
            repo.sessionsResult = AppResult.Failure(AppError.Network())
            val vm = SessionsViewModel(repo, telemetry)
            assertEquals(SessionsPhase.Error, vm.state.value.phase)
            repo.sessionsResult = AppResult.Success(listOf(here))
            vm.onEvent(SessionsEvent.Retry)
            assertEquals(SessionsPhase.List, vm.state.value.phase)
        }

    @Test fun `revoking removes the row and a 404 counts as gone`() =
        runTest(timeout = 30.seconds) {
            repo.sessionsResult = AppResult.Success(listOf(here, other))
            val vm = SessionsViewModel(repo, telemetry)
            vm.onEvent(SessionsEvent.AskRevoke(other))
            repo.revokeSessionResult = AppResult.Failure(AppError.NotFound("session_not_found", "session_not_found"))
            vm.onEvent(SessionsEvent.ConfirmRevoke)
            assertEquals(listOf("a"), vm.state.value.sessions.map { it.id })
            assertTrue(repo.calls.contains("revokeSession:b"))
        }

    @Test fun `a failed sign out everywhere keeps the user signed in`() =
        runTest(timeout = 30.seconds) {
            repo.sessionsResult = AppResult.Success(listOf(here))
            repo.logoutAllResult = AppResult.Failure(AppError.Network())
            val vm = SessionsViewModel(repo, telemetry)
            vm.onEvent(SessionsEvent.AskSignOutAll)
            vm.onEvent(SessionsEvent.ConfirmSignOutAll)
            assertEquals(R.string.account_err_network, vm.state.value.message?.res)
        }
}
