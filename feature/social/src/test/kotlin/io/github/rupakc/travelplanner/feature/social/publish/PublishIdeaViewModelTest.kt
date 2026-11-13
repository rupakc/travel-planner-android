package io.github.rupakc.travelplanner.feature.social.publish

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeSocialRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.feature.social.feed.PublishProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PublishIdeaViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val telemetry = FakeTelemetry()

    private fun vm(kind: String = "plan") =
        PublishIdeaViewModel(SavedStateHandle(mapOf(PublishIdeaViewModel.ARG_PLAN_ID to 12L, PublishIdeaViewModel.ARG_KIND to kind)), social, telemetry)

    private fun filled(v: PublishIdeaViewModel) {
        v.onEvent(PublishIdeaEvent.TitleChanged("Lisbon in 4 days"))
        v.onEvent(PublishIdeaEvent.CaptionChanged("Slow mornings"))
    }

    @Test
    fun `a title is required and the fields are capped`() {
        val v = vm()
        assertFalse(v.state.value.canPublish)
        v.onEvent(PublishIdeaEvent.TitleChanged("x".repeat(120)))
        assertEquals(80, v.state.value.title.length)
        v.onEvent(PublishIdeaEvent.CaptionChanged("y".repeat(900)))
        assertEquals(500, v.state.value.caption.length)
        assertTrue(v.state.value.canPublish)
    }

    @Test
    fun `publishing sends the plan, kind and trimmed text and is counted by kind`() {
        social.publishResult = AppResult.Success(Idea(id = 5, status = "published"))
        val v = vm("recap")
        filled(v)
        v.onEvent(PublishIdeaEvent.Publish)
        assertEquals(listOf("publishIdea(12,recap,Lisbon in 4 days)"), social.calls)
        assertEquals(Published(5, held = false), v.state.value.published)
        assertEquals(listOf(mapOf<String, Any?>("kind" to "recap")), telemetry.events.filter { it.first == "social_idea_publish" }.map { it.second })
    }

    @Test
    fun `an unknown kind publishes as a plan`() {
        assertEquals(IdeaKind.PLAN, vm("weird").state.value.kind)
    }

    @Test
    fun `a held idea is a notice, not an error`() {
        social.publishResult = AppResult.Success(Idea(id = 5, status = "held"))
        val v = vm()
        filled(v)
        v.onEvent(PublishIdeaEvent.Publish)
        assertEquals(Published(5, held = true), v.state.value.published)
        assertNull(v.state.value.error)
    }

    @Test
    fun `refusals become guiding problems and nothing is counted`() {
        val v = vm()
        filled(v)
        listOf(
            AppError.Forbidden("x", "profile_required") to PublishProblem.ProfileRequired,
            AppError.Forbidden("x", "email_unverified") to PublishProblem.EmailUnverified,
            AppError.Client(422, "x", "limit_reached") to PublishProblem.LimitReached,
            AppError.Client(422, "x", "content_rejected") to PublishProblem.ContentRejected,
        ).forEach { (error, problem) ->
            social.publishResult = AppResult.Failure(error)
            v.onEvent(PublishIdeaEvent.Publish)
            assertEquals(problem, v.state.value.problem)
            assertNull(v.state.value.error)
        }
        assertTrue(telemetry.events.none { it.first == "social_idea_publish" })
    }

    @Test
    fun `another failure shows its error and editing clears it`() {
        social.publishResult = AppResult.Failure(AppError.Network(null))
        val v = vm()
        filled(v)
        v.onEvent(PublishIdeaEvent.Publish)
        assertNotNull(v.state.value.error)
        v.onEvent(PublishIdeaEvent.TitleChanged("New"))
        assertNull(v.state.value.error)
        assertNull(v.state.value.published)
    }
}
