package io.github.rupakc.travelplanner.feature.social.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.social.FollowResult
import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileRelation
import io.github.rupakc.travelplanner.core.model.social.PublicProfile
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSocialRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PublicProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val account = FakeAccountRepository()
    private val telemetry = FakeTelemetry()

    private fun vm() =
        PublicProfileViewModel(SavedStateHandle(mapOf(PublicProfileViewModel.ARG_HANDLE to "ann")), social, account, telemetry)

    private fun events(name: String) = telemetry.events.filter { it.first == name }.map { it.second }

    @Test
    fun `a profile loads and is counted as a view without any handle`() {
        social.profileResult = AppResult.Success(PublicProfile(handle = "ann", bio = "hi", followersCount = 3))
        val s = vm().state.value
        assertEquals(ViewPhase.Content, s.phase)
        assertEquals("hi", s.profile?.bio)
        assertEquals(listOf(emptyMap<String, Any?>()), events("social_profile_view"))
    }

    @Test
    fun `a missing profile is the calm unavailable state`() {
        social.profileResult = AppResult.Failure(AppError.NotFound("nope", "profile_not_found"))
        assertEquals(ViewPhase.Unavailable, vm().state.value.phase)
        assertTrue(events("social_profile_view").isEmpty())
    }

    @Test
    fun `another failure is an error with retry`() {
        social.profileResult = AppResult.Failure(AppError.Network(null))
        val v = vm()
        assertEquals(ViewPhase.Error, v.state.value.phase)
        social.profileResult = AppResult.Success(PublicProfile(handle = "ann"))
        v.onEvent(PublicProfileEvent.Retry)
        assertEquals(ViewPhase.Content, v.state.value.phase)
    }

    @Test
    fun `the relation decides the button`() {
        social.profileResult = AppResult.Success(PublicProfile(handle = "ann", you = ProfileRelation(pending = true)))
        assertEquals(FollowUi.Pending, vm().state.value.follow)
        social.profileResult = AppResult.Success(PublicProfile(handle = "ann", you = ProfileRelation(following = true)))
        assertEquals(FollowUi.Following, vm().state.value.follow)
    }

    @Test
    fun `follow, then a pending answer, then cancel`() {
        social.followResult = AppResult.Success(FollowResult("pending"))
        val v = vm()
        v.onEvent(PublicProfileEvent.ToggleFollow)
        assertEquals(FollowUi.Pending, v.state.value.follow)
        v.onEvent(PublicProfileEvent.ToggleFollow)
        assertEquals(FollowUi.None, v.state.value.follow)
        assertEquals(listOf("pending", "none"), events("social_follow").map { it["state"] })
        assertTrue(social.calls.containsAll(listOf("follow(ann)", "unfollow(ann)")))
    }

    @Test
    fun `an immediate follow is following`() {
        val v = vm()
        v.onEvent(PublicProfileEvent.ToggleFollow)
        assertEquals(FollowUi.Following, v.state.value.follow)
        assertEquals("following", events("social_follow").single()["state"])
    }

    @Test
    fun `a failed follow keeps the state and shows the backend text`() {
        social.followResult = AppResult.Failure(AppError.Client(422, "Daily follow limit reached", "limit_reached"))
        val v = vm()
        v.onEvent(PublicProfileEvent.ToggleFollow)
        assertEquals(FollowUi.None, v.state.value.follow)
        assertEquals("Daily follow limit reached", (v.state.value.message as ProfileMessage.Failed).error.message)
        assertTrue(events("social_follow").isEmpty())
    }

    @Test
    fun `your own profile has no follow, block or report and does not follow`() {
        social.profileResult = AppResult.Success(PublicProfile(handle = "ann"))
        social.ownProfileResult = AppResult.Success(OwnProfile(handle = "Ann"))
        val v = vm()
        assertTrue(v.state.value.isMe)
        v.onEvent(PublicProfileEvent.ToggleFollow)
        assertTrue(social.calls.none { it.startsWith("follow(") })
    }

    @Test
    fun `hide_game on the viewer hides level and badges`() {
        account.settingsResult = AppResult.Success(AccountSettings(hideGame = true))
        assertTrue(vm().state.value.hideGame)
    }

    @Test
    fun `blocking asks first, blocks, reports telemetry and leaves`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(PublicProfileEvent.AskBlock)
                assertTrue(v.state.value.confirmBlock)
                assertTrue(social.calls.none { it.startsWith("block") })
                v.onEvent(PublicProfileEvent.ConfirmBlock)
                assertEquals(PublicProfileEffect.Leave, awaitItem())
            }
            assertTrue(social.calls.contains("block(ann)"))
            assertEquals(1, events("social_block").size)
        }

    @Test
    fun `a failed block stays on the profile`() {
        social.unitResult = AppResult.Failure(AppError.Network(null))
        val v = vm()
        v.onEvent(PublicProfileEvent.AskBlock)
        v.onEvent(PublicProfileEvent.ConfirmBlock)
        assertTrue(v.state.value.message is ProfileMessage.Failed)
        assertTrue(events("social_block").isEmpty())
    }

    @Test
    fun `a report sends the reason and the enum telemetry`() {
        val v = vm()
        v.onEvent(PublicProfileEvent.AskReport)
        assertTrue(v.state.value.reporting)
        v.onEvent(PublicProfileEvent.SendReport(ReportReason.SPAM))
        assertTrue(social.calls.contains("report(profile,ann,spam)"))
        assertEquals(ProfileMessage.Reported, v.state.value.message)
        assertEquals(false, v.state.value.reporting)
        assertEquals(mapOf<String, Any?>("target" to "profile", "reason" to "spam"), events("social_report").single())
    }

    @Test
    fun `a failed report keeps the dialog open with the error`() {
        social.unitResult = AppResult.Failure(AppError.RateLimited("Too many reports", null, null))
        val v = vm()
        v.onEvent(PublicProfileEvent.AskReport)
        v.onEvent(PublicProfileEvent.SendReport(ReportReason.OTHER))
        assertTrue(v.state.value.reporting)
        assertEquals("Too many reports", v.state.value.reportError?.message)
        assertNull(v.state.value.message)
        assertTrue(events("social_report").isEmpty())
    }
}
