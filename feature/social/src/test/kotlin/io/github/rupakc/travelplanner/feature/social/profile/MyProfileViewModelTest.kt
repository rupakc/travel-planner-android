package io.github.rupakc.travelplanner.feature.social.profile

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.social.HandleCheck
import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileVisibility
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSocialRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MyProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val account = FakeAccountRepository()
    private val telemetry = FakeTelemetry()

    private fun vm() = MyProfileViewModel(social, account, telemetry)

    private fun client(code: String, retryAfter: String? = null, status: Int = 422) =
        AppError.Client(status, "server said so", code, extras = retryAfter?.let { mapOf("retry_after" to it) } ?: emptyMap())

    @Test
    fun `no profile yet shows an empty private form`() {
        val s = vm().state.value
        assertEquals(ProfilePhase.Content, s.phase)
        assertTrue(s.isNew)
        assertEquals(ProfileVisibility.PRIVATE, s.form.visibility)
        assertFalse(s.canSave)
    }

    @Test
    fun `a failure to load shows the error and retry reloads`() {
        social.ownProfileResult = AppResult.Failure(AppError.Network(null))
        val v = vm()
        assertEquals(ProfilePhase.Error, v.state.value.phase)
        social.ownProfileResult = AppResult.Success(OwnProfile(handle = "ann"))
        v.onEvent(MyProfileEvent.Retry)
        assertEquals("ann", v.state.value.form.handle)
    }

    @Test
    fun `hide_game hides the level and badge switches`() {
        account.settingsResult = AppResult.Success(AccountSettings(hideGame = true))
        assertTrue(vm().state.value.hideGame)
    }

    @Test
    fun `a bad handle is rejected locally without a request`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("a b"))
            advanceTimeBy(1_000)
            assertEquals(HandleStatus.BadSyntax, v.state.value.handleStatus)
            assertTrue(social.calls.none { it.startsWith("checkHandle") })
            assertFalse(v.state.value.canSave)
        }

    @Test
    fun `the handle check is debounced and only the last value is asked`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("an"))
            v.onEvent(MyProfileEvent.HandleChanged("ann"))
            v.onEvent(MyProfileEvent.HandleChanged("anna"))
            assertEquals(HandleStatus.Checking, v.state.value.handleStatus)
            assertFalse(v.state.value.canSave)
            advanceTimeBy(HANDLE_CHECK_DEBOUNCE_MS + 50)
            assertEquals(listOf("checkHandle(anna)"), social.calls.filter { it.startsWith("checkHandle") })
            assertEquals(HandleStatus.Available, v.state.value.handleStatus)
            assertTrue(v.state.value.canSave)
        }

    @Test
    fun `a taken handle blocks saving`() =
        runTest(timeout = 30.seconds) {
            social.checkHandleResult = AppResult.Success(HandleCheck(available = false, reason = "taken"))
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("anna"))
            advanceTimeBy(HANDLE_CHECK_DEBOUNCE_MS + 50)
            assertTrue(v.state.value.handleStatus is HandleStatus.Unavailable)
            assertFalse(v.state.value.canSave)
        }

    @Test
    fun `the saved handle is not re-checked`() =
        runTest(timeout = 30.seconds) {
            social.ownProfileResult = AppResult.Success(OwnProfile(handle = "ann"))
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("ANN"))
            advanceTimeBy(1_000)
            assertEquals(HandleStatus.Idle, v.state.value.handleStatus)
            assertTrue(social.calls.none { it.startsWith("checkHandle") })
        }

    @Test
    fun `creating a profile sends the handle and reports the visibility`() =
        runTest(timeout = 30.seconds) {
            social.saveProfileResult = AppResult.Success(OwnProfile(handle = "anna", visibility = "public"))
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("anna"))
            advanceTimeBy(HANDLE_CHECK_DEBOUNCE_MS + 50)
            v.onEvent(MyProfileEvent.VisibilityChanged(ProfileVisibility.PUBLIC))
            v.onEvent(MyProfileEvent.Save)
            val sent = social.savedProfiles.single()
            assertEquals("anna", sent.handle)
            assertEquals("public", sent.visibility)
            assertEquals(1, v.state.value.savedTick)
            assertFalse(v.state.value.isNew)
            assertEquals(listOf("social_profile_save" to mapOf<String, Any?>("visibility" to "public")), telemetry.events)
        }

    @Test
    fun `an edit sends only what changed`() {
        social.ownProfileResult = AppResult.Success(OwnProfile(handle = "ann", bio = "hi"))
        social.saveProfileResult = AppResult.Success(OwnProfile(handle = "ann", bio = "hello", showLevel = true))
        val v = vm()
        v.onEvent(MyProfileEvent.BioChanged("hello"))
        v.onEvent(MyProfileEvent.Toggled(ProfileToggle.Level, true))
        v.onEvent(MyProfileEvent.Save)
        val sent = social.savedProfiles.single()
        assertNull(sent.handle)
        assertEquals("hello", sent.bio)
        assertEquals(true, sent.showLevel)
    }

    @Test
    fun `the bio is capped at 160`() {
        val v = vm()
        v.onEvent(MyProfileEvent.BioChanged("x".repeat(200)))
        assertEquals(BIO_MAX, v.state.value.form.bio.length)
    }

    @Test
    fun `an incomplete country blocks saving`() {
        social.ownProfileResult = AppResult.Success(OwnProfile(handle = "ann"))
        val v = vm()
        v.onEvent(MyProfileEvent.CountryChanged("p"))
        assertFalse(v.state.value.canSave)
        v.onEvent(MyProfileEvent.CountryChanged("pt"))
        assertTrue(v.state.value.canSave)
    }

    @Test
    fun `a rate limited handle change shows days left and no generic error`() =
        runTest(timeout = 30.seconds) {
            social.ownProfileResult = AppResult.Success(OwnProfile(handle = "ann"))
            social.saveProfileResult = AppResult.Failure(client("handle_rate_limited", retryAfter = "172800"))
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("anna"))
            advanceTimeBy(HANDLE_CHECK_DEBOUNCE_MS + 50)
            v.onEvent(MyProfileEvent.Save)
            assertEquals(FieldProblem.HandleRateLimited, v.state.value.problem)
            assertEquals(2, v.state.value.retryDays)
            assertNull(v.state.value.saveError)
            assertTrue(telemetry.events.isEmpty())
        }

    @Test
    fun `a taken or rejected value maps to its field`() =
        runTest(timeout = 30.seconds) {
            social.saveProfileResult = AppResult.Failure(client("handle_taken", status = 409))
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("anna"))
            advanceTimeBy(HANDLE_CHECK_DEBOUNCE_MS + 50)
            v.onEvent(MyProfileEvent.Save)
            assertEquals(FieldProblem.HandleTaken, v.state.value.problem)
            assertTrue(v.state.value.handleStatus is HandleStatus.Unavailable)
            social.saveProfileResult = AppResult.Failure(client("content_rejected"))
            v.onEvent(MyProfileEvent.BioChanged("bad"))
            v.onEvent(MyProfileEvent.HandleChanged("annb"))
            advanceTimeBy(HANDLE_CHECK_DEBOUNCE_MS + 50)
            v.onEvent(MyProfileEvent.Save)
            assertEquals(FieldProblem.BioRejected, v.state.value.problem)
        }

    @Test
    fun `an unrelated failure keeps the backend text`() =
        runTest(timeout = 30.seconds) {
            social.saveProfileResult = AppResult.Failure(AppError.RateLimited("Slow down", null, null))
            val v = vm()
            v.onEvent(MyProfileEvent.HandleChanged("anna"))
            advanceTimeBy(HANDLE_CHECK_DEBOUNCE_MS + 50)
            v.onEvent(MyProfileEvent.Save)
            assertNull(v.state.value.problem)
            assertEquals("Slow down", v.state.value.saveError?.message)
        }
}
