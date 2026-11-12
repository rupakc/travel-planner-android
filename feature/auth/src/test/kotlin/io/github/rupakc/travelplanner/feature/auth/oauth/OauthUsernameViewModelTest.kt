package io.github.rupakc.travelplanner.feature.auth.oauth

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.OauthInbox
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import io.github.rupakc.travelplanner.core.testing.fake.FakeOauthRepository
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OauthUsernameViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val oauth = FakeOauthRepository()
    private val inbox = OauthInbox()

    private fun vm(draft: OauthResult? = OauthResult(status = "needs_username", signupToken = "tok", suggestedUsername = "jane")): OauthUsernameViewModel {
        inbox.setDraft(draft)
        return OauthUsernameViewModel(oauth, inbox, FakeAppLanguage())
    }

    @Test
    fun `suggested username prefills and submit completes with the token`() =
        runTest(timeout = 30.seconds) {
            oauth.completeResult = AppResult.Success(OauthResult(status = "signed_in"))
            val vm = vm()
            assertEquals("jane", vm.state.value.username)
            vm.onEvent(OauthUsernameEvent.UsernameChanged("Jane_9 "))
            vm.onEvent(OauthUsernameEvent.Submit)
            assertEquals(listOf("complete:jane_9"), oauth.calls)
            assertNull(inbox.draft.value)
            assertNull(vm.state.value.error)
        }

    @Test
    fun `username_taken shows its copy and keeps the draft`() =
        runTest(timeout = 30.seconds) {
            oauth.completeResult = AppResult.Failure(AppError.Client(status = 409, message = "taken", code = "username_taken"))
            val vm = vm()
            vm.onEvent(OauthUsernameEvent.Submit)
            assertEquals(AuthMessage.Local(R.string.auth_err_username_taken), vm.state.value.error)
            assertTrue(inbox.draft.value != null)
        }

    @Test
    fun `invite is required only when the server says so`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(OauthResult(status = "needs_username", signupToken = "t", suggestedUsername = "jane", inviteRequired = true))
            assertFalse(vm.state.value.canSubmit)
            vm.onEvent(OauthUsernameEvent.InviteChanged("INV-1"))
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `no draft cannot submit`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(null)
            assertFalse(vm.state.value.hasDraft)
            vm.onEvent(OauthUsernameEvent.Submit)
            assertTrue(oauth.calls.isEmpty())
        }

    @Test
    fun `cancel drops the draft`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.onEvent(OauthUsernameEvent.Cancel)
            assertNull(inbox.draft.value)
        }
}
