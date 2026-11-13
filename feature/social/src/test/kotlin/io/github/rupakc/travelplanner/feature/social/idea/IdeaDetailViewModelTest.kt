package io.github.rupakc.travelplanner.feature.social.idea

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.social.Comment
import io.github.rupakc.travelplanner.core.model.social.CommentPage
import io.github.rupakc.travelplanner.core.model.social.HandleRef
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.IdeaAuthor
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeSocialRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.feature.social.feed.IdeaChange
import io.github.rupakc.travelplanner.feature.social.feed.IdeaChanges
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IdeaDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val telemetry = FakeTelemetry()
    private val changes = IdeaChanges()

    private fun idea(mine: Boolean = false) = Idea(id = 7, title = "Lisbon", author = IdeaAuthor("ann"), mine = mine, likes = 2, comments = 1)

    private fun comment(id: Long, mine: Boolean = false, status: String? = null) =
        Comment(
            id = id,
            author = HandleRef("bob"),
            text = "hi",
            mine = mine,
            status = status,
        )

    private fun vm() = IdeaDetailViewModel(SavedStateHandle(mapOf(IdeaDetailViewModel.ARG_IDEA_ID to 7L)), social, telemetry, changes)

    private fun events(name: String) = telemetry.events.filter { it.first == name }.map { it.second }

    @Test
    fun `the idea and its comments load and the open is counted`() {
        social.ideaResult = AppResult.Success(idea())
        social.commentsResult = AppResult.Success(CommentPage(listOf(comment(1)), next = "n"))
        val s = vm().state.value
        assertEquals(IdeaPhase.Content, s.phase)
        assertEquals(listOf(1L), s.comments.map { it.id })
        assertEquals("n", s.commentsNext)
        assertEquals(1, events("social_idea_open").size)
    }

    @Test
    fun `a hidden or blocked idea is one calm unavailable state`() {
        social.ideaResult = AppResult.Failure(AppError.NotFound("x", "idea_unavailable"))
        assertEquals(IdeaPhase.Gone, vm().state.value.phase)
        social.ideaResult = AppResult.Failure(AppError.Network(null))
        assertEquals(IdeaPhase.Error, vm().state.value.phase)
    }

    @Test
    fun `liking updates the counts and is counted once`() {
        social.ideaResult = AppResult.Success(idea())
        val v = vm()
        v.onEvent(IdeaDetailEvent.ToggleLike)
        assertEquals(3, v.state.value.idea?.likes)
        assertTrue(v.state.value.idea?.liked == true)
        assertEquals(1, events("social_idea_like").size)
    }

    @Test
    fun `a change reaches the feed bus`() =
        runTest(timeout = 10.seconds) {
            social.ideaResult = AppResult.Success(idea())
            val v = vm()
            changes.changes.test {
                v.onEvent(IdeaDetailEvent.ToggleLike)
                assertTrue((awaitItem() as IdeaChange.Updated).idea.liked)
            }
        }

    @Test
    fun `a failed like rolls back`() {
        social.ideaResult = AppResult.Success(idea())
        val v = vm()
        social.unitResult = AppResult.Failure(AppError.Network(null))
        v.onEvent(IdeaDetailEvent.ToggleLike)
        assertFalse(v.state.value.idea?.liked == true)
        assertEquals(2, v.state.value.idea?.likes)
        assertTrue(v.state.value.message is IdeaMessage.Failed)
    }

    @Test
    fun `save as remix opens the new plan`() =
        runTest(timeout = 10.seconds) {
            social.ideaResult = AppResult.Success(idea())
            social.saveIdeaResult = AppResult.Success(99L)
            val v = vm()
            v.effects.test {
                v.onEvent(IdeaDetailEvent.ToggleSave)
                assertEquals(IdeaDetailEffect.OpenPlan(99), awaitItem())
            }
            assertTrue(v.state.value.idea?.saved == true)
            assertEquals(1, events("social_idea_save").size)
        }

    @Test
    fun `a comment is posted, counted and added`() {
        social.ideaResult = AppResult.Success(idea())
        social.commentResult = AppResult.Success(comment(5, mine = true))
        val v = vm()
        v.onEvent(IdeaDetailEvent.DraftChanged("  nice  "))
        v.onEvent(IdeaDetailEvent.PostComment)
        assertEquals(listOf(5L), v.state.value.comments.map { it.id })
        assertEquals("", v.state.value.draft)
        assertEquals(2, v.state.value.idea?.comments)
        assertEquals(1, events("social_comment").size)
    }

    @Test
    fun `a held comment shows the notice and does not raise the count`() {
        social.ideaResult = AppResult.Success(idea())
        social.commentResult = AppResult.Success(comment(5, mine = true, status = "held"))
        val v = vm()
        v.onEvent(IdeaDetailEvent.DraftChanged("hello"))
        v.onEvent(IdeaDetailEvent.PostComment)
        assertEquals(IdeaMessage.CommentHeld, v.state.value.message)
        assertEquals(1, v.state.value.idea?.comments)
    }

    @Test
    fun `a blank draft cannot be posted and the draft is capped`() {
        social.ideaResult = AppResult.Success(idea())
        val v = vm()
        v.onEvent(IdeaDetailEvent.DraftChanged("   "))
        assertFalse(v.state.value.canPost)
        v.onEvent(IdeaDetailEvent.DraftChanged("x".repeat(400)))
        assertEquals(300, v.state.value.draft.length)
    }

    @Test
    fun `the author marks a comment helpful and deletes comments`() {
        social.ideaResult = AppResult.Success(idea(mine = true))
        social.commentsResult = AppResult.Success(CommentPage(listOf(comment(1), comment(2))))
        val v = vm()
        v.onEvent(IdeaDetailEvent.ToggleHelpful(1))
        assertTrue(v.state.value.comments.first { it.id == 1L }.helpful)
        assertTrue("setHelpful(1,true)" in social.calls)
        v.onEvent(IdeaDetailEvent.DeleteComment(2))
        assertEquals(listOf(1L), v.state.value.comments.map { it.id })
        assertEquals(0, v.state.value.idea?.comments)
    }

    @Test
    fun `only the idea author can mark helpful`() {
        social.ideaResult = AppResult.Success(idea(mine = false))
        social.commentsResult = AppResult.Success(CommentPage(listOf(comment(1))))
        val v = vm()
        v.onEvent(IdeaDetailEvent.ToggleHelpful(1))
        assertTrue(social.calls.none { it.startsWith("setHelpful") })
    }

    @Test
    fun `deleting the idea leaves and tells the feed`() =
        runTest(timeout = 10.seconds) {
            social.ideaResult = AppResult.Success(idea(mine = true))
            val v = vm()
            v.effects.test {
                v.onEvent(IdeaDetailEvent.AskDelete)
                assertTrue(v.state.value.confirmDelete)
                v.onEvent(IdeaDetailEvent.ConfirmDelete)
                assertEquals(IdeaDetailEffect.Leave, awaitItem())
            }
            assertTrue("deleteIdea(7)" in social.calls)
        }

    @Test
    fun `blocking the author is confirmed, counted and leaves`() =
        runTest(timeout = 10.seconds) {
            social.ideaResult = AppResult.Success(idea())
            val v = vm()
            v.effects.test {
                v.onEvent(IdeaDetailEvent.AskBlock)
                v.onEvent(IdeaDetailEvent.ConfirmBlock)
                assertEquals(IdeaDetailEffect.Leave, awaitItem())
            }
            assertTrue("block(ann)" in social.calls)
            assertEquals(1, events("social_block").size)
        }

    @Test
    fun `reporting an idea and a comment sends the right target without ids in telemetry`() {
        social.ideaResult = AppResult.Success(idea())
        social.commentsResult = AppResult.Success(CommentPage(listOf(comment(3))))
        val v = vm()
        v.onEvent(IdeaDetailEvent.AskReport(ReportSubject.OfIdea))
        v.onEvent(IdeaDetailEvent.SendReport(ReportReason.SPAM))
        v.onEvent(IdeaDetailEvent.AskReport(ReportSubject.OfComment(3)))
        v.onEvent(IdeaDetailEvent.SendReport(ReportReason.ABUSE))
        assertEquals(listOf("report(idea,7,spam)", "report(comment,3,abuse)"), social.calls.filter { it.startsWith("report") })
        assertEquals(
            listOf(mapOf<String, Any?>("target" to "idea", "reason" to "spam"), mapOf("target" to "comment", "reason" to "abuse")),
            events("social_report"),
        )
        assertEquals(IdeaMessage.Reported, v.state.value.message)
    }

    @Test
    fun `a failed report keeps the dialog for a retry`() {
        social.ideaResult = AppResult.Success(idea())
        val v = vm()
        v.onEvent(IdeaDetailEvent.AskReport(ReportSubject.OfIdea))
        social.unitResult = AppResult.Failure(AppError.Network(null))
        v.onEvent(IdeaDetailEvent.SendReport(ReportReason.OTHER))
        assertNotNull(v.state.value.reporting)
        assertNotNull(v.state.value.reportError)
        assertTrue(events("social_report").isEmpty())
    }
}
