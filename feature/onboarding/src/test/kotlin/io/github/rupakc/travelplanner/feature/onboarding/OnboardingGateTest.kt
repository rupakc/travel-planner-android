package io.github.rupakc.travelplanner.feature.onboarding

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import io.github.rupakc.travelplanner.core.model.auth.OnboardingDto
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.util.Optional
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {
    private fun account(done: Boolean) = AppResult.Success(AccountDto(user = TestUser, onboarding = OnboardingDto(done = done)))

    @Test
    fun `decision table`() {
        assertTrue(shouldShowOnboarding(signedIn = true, shownThisProcess = false, account = account(done = false)))
        assertFalse(shouldShowOnboarding(signedIn = true, shownThisProcess = false, account = account(done = true)))
        assertFalse(shouldShowOnboarding(signedIn = false, shownThisProcess = false, account = account(done = false)))
        assertFalse(shouldShowOnboarding(signedIn = true, shownThisProcess = true, account = account(done = false)))
        assertFalse(shouldShowOnboarding(signedIn = true, shownThisProcess = false, account = null))
    }

    @Test
    fun `never fires on 404 or any error`() {
        listOf(AppError.NotFound(), AppError.Network(), AppError.Unauthorized()).forEach {
            assertFalse(shouldShowOnboarding(signedIn = true, shownThisProcess = false, account = AppResult.Failure(it)))
        }
    }

    @Test
    fun `shows once per process`() =
        runTest {
            val repo = FakeAccountRepository().apply { accountResult = account(done = false) }
            val gate = OnboardingGate(Optional.of(repo))
            assertTrue(gate.shouldShow(signedIn = true))
            assertFalse(gate.shouldShow(signedIn = true))
            assertEquals(listOf("account"), repo.calls)
        }

    @Test
    fun `signed out or no repository never asks`() =
        runTest {
            val repo = FakeAccountRepository().apply { accountResult = account(done = false) }
            assertFalse(OnboardingGate(Optional.of(repo)).shouldShow(signedIn = false))
            assertTrue(repo.calls.isEmpty())
            assertFalse(OnboardingGate(Optional.empty()).shouldShow(signedIn = true))
        }

    @Test
    fun `a 404 does not use up the once`() =
        runTest {
            val repo = FakeAccountRepository().apply { accountResult = AppResult.Failure(AppError.NotFound()) }
            val gate = OnboardingGate(Optional.of(repo))
            assertFalse(gate.shouldShow(signedIn = true))
            repo.accountResult = account(done = false)
            assertTrue(gate.shouldShow(signedIn = true))
        }
}
