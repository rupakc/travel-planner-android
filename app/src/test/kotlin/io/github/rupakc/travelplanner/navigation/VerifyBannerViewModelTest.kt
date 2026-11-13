package io.github.rupakc.travelplanner.navigation

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.EmailVerificationGate
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VerifyBannerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository(Session("t", User("alice", accountVerified = false)))
    private val account = FakeAccountRepository()
    private val gate = EmailVerificationGate()
    private val telemetry = FakeTelemetry()

    private fun TestScope.vm(): VerifyBannerViewModel {
        val vm = VerifyBannerViewModel(auth, account, gate, telemetry, FakeAppLanguage("de"))
        vm.state.onEach { }.launchIn(backgroundScope)
        return vm
    }

    @Test
    fun `an unverified account shows the banner once and reports it`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            assertTrue(vm.state.value.visible)
            assertEquals(1, telemetry.events.count { it.first == "quota_block" })
            assertTrue(telemetry.events.contains("quota_block" to mapOf("bucket" to "search", "reason" to "unverified")))
        }

    @Test
    fun `a verified account shows nothing until an AI route answers email_unverified`() =
        runTest(timeout = 30.seconds) {
            auth.sessionFlow.value = Session("t", User("alice"))
            auth.refreshResult = AppResult.Success(User("alice", accountVerified = false))
            val vm = vm()
            assertFalse(vm.state.value.visible)
            gate.markBlocked()
            assertTrue(vm.state.value.visible)
        }

    @Test
    fun `the gate clears when the refreshed user turns out confirmed`() =
        runTest(timeout = 30.seconds) {
            auth.sessionFlow.value = Session("t", User("alice"))
            auth.refreshResult = AppResult.Success(User("alice", accountVerified = true))
            val vm = vm()
            gate.markBlocked()
            assertFalse(gate.blocked.value)
            assertFalse(vm.state.value.visible)
        }

    @Test
    fun `dismiss hides it for that user and a different user sees it again`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.dismiss()
            assertFalse(vm.state.value.visible)
            auth.sessionFlow.value = Session("t2", User("bob", accountVerified = false))
            assertTrue(vm.state.value.visible)
        }

    @Test
    fun `send reaches sent and reports the request`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.send()
            assertEquals(VerifySend.Sent, vm.state.value.send)
            assertTrue(account.calls.contains("requestEmailVerification"))
            assertTrue(telemetry.events.contains("account_action" to mapOf("action" to "email_verify_request")))
        }

    @Test
    fun `a failed send shows the backend text and no_email hides the action`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            account.requestEmailVerificationResult = AppResult.Failure(AppError.Client(422, "No email on file", "no_email"))
            vm.send()
            assertEquals(VerifySend.Failed("No email on file", true), vm.state.value.send)
            account.requestEmailVerificationResult = AppResult.Failure(AppError.Network(null))
            vm.send()
            assertEquals(VerifySend.Failed(null, false), vm.state.value.send)
        }
}
