package io.github.rupakc.travelplanner.feature.social.feed

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.social.FeedScope
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.IdeaAuthor
import io.github.rupakc.travelplanner.core.model.social.IdeaPage
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.testing.fake.FakeJourneyFlagsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSocialRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val telemetry = FakeTelemetry()
    private val changes = IdeaChanges()

    private fun idea(id: Long, author: String = "ann", liked: Boolean = false) =
        Idea(
            id = id,
            title = "T$id",
            author = IdeaAuthor(author),
            liked = liked,
            likes = 1,
        )

    private val flags = FakeJourneyFlagsRepository(JourneyFlags(social = true))

    private fun vm() = FeedViewModel(social, telemetry, changes, flags)

    private fun events(name: String) = telemetry.events.filter { it.first == name }.map { it.second }

    @Test
    fun `with social off nothing is requested or counted`() {
        flags.flags = JourneyFlags(social = false)
        val v = vm()
        v.onEvent(FeedEvent.Refresh)
        v.onEvent(FeedEvent.SelectScope(FeedScope.NEW))
        assertTrue(social.calls.none { it.startsWith("feed") })
        assertTrue(events("social_feed_view").isEmpty())
        assertTrue(v.state.value.current.ideas.isEmpty())
        assertEquals(false, v.state.value.current.loading)
    }

    @Test
    fun `for you loads first and is counted with its scope`() {
        social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1), idea(2)), next = "c1"))
        val v = vm()
        assertEquals(listOf(1L, 2L), v.state.value.current.ideas.map { it.id })
        assertEquals("c1", v.state.value.current.next)
        assertEquals(listOf(mapOf<String, Any?>("scope" to "for_you")), events("social_feed_view"))
    }

    @Test
    fun `scopes load once each and a switch is counted`() {
        val v = vm()
        v.onEvent(FeedEvent.SelectScope(FeedScope.NEW))
        v.onEvent(FeedEvent.SelectScope(FeedScope.FOR_YOU))
        v.onEvent(FeedEvent.SelectScope(FeedScope.NEW))
        assertEquals(listOf("feed(for_you,null)", "feed(new,null)"), social.calls.filter { it.startsWith("feed") })
        assertEquals(listOf("for_you", "new", "for_you", "new"), events("social_feed_view").map { it["scope"] })
    }

    @Test
    fun `an error shows with retry`() {
        social.feedResult = AppResult.Failure(AppError.Network(null))
        val v = vm()
        assertNotNull(v.state.value.current.error)
        social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1))))
        v.onEvent(FeedEvent.Refresh)
        assertNull(v.state.value.current.error)
        assertEquals(1, v.state.value.current.ideas.size)
    }

    @Test
    fun `load more appends with the cursor and skips duplicates`() {
        social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1)), next = "c1"))
        val v = vm()
        social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1), idea(2)), next = null))
        v.onEvent(FeedEvent.LoadMore)
        assertEquals(listOf(1L, 2L), v.state.value.current.ideas.map { it.id })
        assertNull(v.state.value.current.next)
        assertTrue("feed(for_you,c1)" in social.calls)
    }

    @Test
    fun `like is optimistic, counted once and rolled back on failure`() {
        social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1))))
        val v = vm()
        v.onEvent(FeedEvent.ToggleLike(1))
        assertTrue(v.state.value.current.ideas.single().liked)
        assertEquals(1, events("social_idea_like").size)
        social.unitResult = AppResult.Failure(AppError.Network(null))
        v.onEvent(FeedEvent.ToggleLike(1))
        assertTrue(v.state.value.current.ideas.single().liked)
        assertTrue(v.state.value.message is FeedMessage.Failed)
    }

    @Test
    fun `save opens the new plan`() =
        runTest(timeout = 10.seconds) {
            social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1))))
            social.saveIdeaResult = AppResult.Success(42L)
            val v = vm()
            v.effects.test {
                v.onEvent(FeedEvent.ToggleSave(1))
                assertEquals(FeedEffect.OpenPlan(42), awaitItem())
            }
            assertTrue(v.state.value.current.ideas.single().saved)
            assertEquals(1, events("social_idea_save").size)
        }

    @Test
    fun `an idea that is gone leaves the list with a calm message`() {
        social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1), idea(2))))
        val v = vm()
        social.unitResult = AppResult.Failure(AppError.NotFound("x", "idea_unavailable"))
        v.onEvent(FeedEvent.ToggleLike(1))
        assertEquals(listOf(2L), v.state.value.current.ideas.map { it.id })
        assertEquals(FeedMessage.Gone, v.state.value.message)
    }

    @Test
    fun `changes from the idea screen reach the list`() {
        social.feedResult = AppResult.Success(IdeaPage(listOf(idea(1, "ann"), idea(2, "bob"), idea(3, "ann"))))
        val v = vm()
        changes.publish(IdeaChange.Updated(idea(2, "bob", liked = true)))
        assertTrue(v.state.value.current.ideas[1].liked)
        changes.publish(IdeaChange.Deleted(2))
        assertEquals(listOf(1L, 3L), v.state.value.current.ideas.map { it.id })
        changes.publish(IdeaChange.AuthorBlocked("ANN"))
        assertTrue(v.state.value.current.ideas.isEmpty())
    }

    @Test
    fun `opening an idea is counted without ids`() {
        vm().onEvent(FeedEvent.IdeaOpened)
        assertEquals(listOf(emptyMap<String, Any?>()), events("social_idea_open"))
    }
}
