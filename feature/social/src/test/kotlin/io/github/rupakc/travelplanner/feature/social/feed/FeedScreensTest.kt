package io.github.rupakc.travelplanner.feature.social.feed

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.social.Comment
import io.github.rupakc.travelplanner.core.model.social.FeedScope
import io.github.rupakc.travelplanner.core.model.social.HandleRef
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.IdeaAuthor
import io.github.rupakc.travelplanner.core.model.social.IdeaSnapshot
import io.github.rupakc.travelplanner.core.model.social.SnapshotItem
import io.github.rupakc.travelplanner.feature.social.idea.IdeaDetailEvent
import io.github.rupakc.travelplanner.feature.social.idea.IdeaDetailScreen
import io.github.rupakc.travelplanner.feature.social.idea.IdeaDetailUiState
import io.github.rupakc.travelplanner.feature.social.idea.IdeaPhase
import io.github.rupakc.travelplanner.feature.social.publish.PublishIdeaEvent
import io.github.rupakc.travelplanner.feature.social.publish.PublishIdeaScreen
import io.github.rupakc.travelplanner.feature.social.publish.PublishIdeaUiState
import io.github.rupakc.travelplanner.feature.social.publish.Published
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FeedScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun themed(dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }

    private val idea =
        Idea(
            id = 7,
            title = "Lisbon <b>slow</b>",
            caption = "See https://example.com now",
            author = IdeaAuthor("ann"),
            snapshot = IdeaSnapshot(destinations = listOf("Lisbon"), days = 4, items = mapOf("hotels" to listOf(SnapshotItem("Casa Azul")))),
            likes = 2,
            saves = 1,
            comments = 3,
        )

    @Test
    fun `feed shows the card as plain text with its counts`() {
        val state = FeedUiState(scopes = mapOf(FeedScope.FOR_YOU to ScopeState(loaded = true, ideas = listOf(idea))))
        themed { FeedScreen(state, {}, {}, {}) }
        compose.onNodeWithText("Lisbon <b>slow</b>").assertIsDisplayed()
        compose.onNodeWithText("@ann").assertIsDisplayed()
        compose.onNodeWithText("Lisbon · 4 days").assertIsDisplayed()
        compose.onNodeWithText("See https://example.com now").assertIsDisplayed()
        compose.onNodeWithText("2 likes").assertIsDisplayed()
    }

    @Test
    fun `an empty following scope explains itself and an error offers retry`() {
        val events = mutableListOf<FeedEvent>()
        themed {
            FeedScreen(FeedUiState(scope = FeedScope.FOLLOWING, scopes = mapOf(FeedScope.FOLLOWING to ScopeState(loaded = true))), { events += it }, {}, {})
        }
        compose.onNodeWithText("Ideas from people you follow will show up here.").assertIsDisplayed()
    }

    @Test
    fun `tapping a card opens the idea and counts the open`() {
        val events = mutableListOf<FeedEvent>()
        val opened = mutableListOf<Long>()
        val state = FeedUiState(scopes = mapOf(FeedScope.FOR_YOU to ScopeState(loaded = true, ideas = listOf(idea))))
        themed(dark = true) { FeedScreen(state, { events += it }, {}, { opened += it }) }
        compose.onNodeWithText("Lisbon <b>slow</b>").performClick()
        assertEquals(listOf(7L), opened)
        assertEquals(FeedEvent.IdeaOpened, events.first())
    }

    @Test
    fun `an own idea has no save button`() {
        val state = FeedUiState(scopes = mapOf(FeedScope.FOR_YOU to ScopeState(loaded = true, ideas = listOf(idea.copy(mine = true)))))
        themed { FeedScreen(state, {}, {}, {}) }
        compose.onNodeWithText("Save as my plan").assertDoesNotExist()
    }

    @Test
    fun `detail shows the held notice, the snapshot items and comments, and posts`() {
        val events = mutableListOf<IdeaDetailEvent>()
        val state =
            IdeaDetailUiState(
                ideaId = 7,
                phase = IdeaPhase.Content,
                idea = idea.copy(mine = true, status = "held"),
                commentsLoaded = true,
                comments = listOf(Comment(id = 1, author = HandleRef("bob"), text = "Great *plan*")),
                draft = "thanks",
            )
        themed { IdeaDetailScreen(state, { events += it }, {}, {}) }
        compose.onNodeWithText("Waiting for review. Only you can see this idea until it is approved.").assertIsDisplayed()
        compose.onNodeWithText("Casa Azul").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Great *plan*"))
        compose.onNodeWithText("Great *plan*").assertIsDisplayed()
    }

    @Test
    fun `a gone idea is the calm unavailable state`() {
        themed { IdeaDetailScreen(IdeaDetailUiState(phase = IdeaPhase.Gone, error = AppError.NotFound("x", "idea_unavailable")), {}, {}, {}) }
        compose.onNodeWithText("This idea is not available").assertIsDisplayed()
    }

    @Test
    fun `publish guides to the profile when it is missing`() {
        var profile = 0
        themed {
            PublishIdeaScreen(
                PublishIdeaUiState(title = "T", problem = io.github.rupakc.travelplanner.feature.social.feed.PublishProblem.ProfileRequired),
                { _: PublishIdeaEvent -> },
                {},
                { profile++ },
                {},
            )
        }
        compose.onNodeWithText("Set up profile").performScrollTo().performClick()
        assertEquals(1, profile)
    }

    @Test
    fun `publish shows the held notice and opens the idea`() {
        val opened = mutableListOf<Long>()
        themed { PublishIdeaScreen(PublishIdeaUiState(title = "T", published = Published(9, held = true)), {}, {}, {}, { opened += it }) }
        compose.onNodeWithText("Your idea is waiting for review. Only you can see it until it is approved.").assertIsDisplayed()
        compose.onNodeWithText("View idea").performClick()
        assertEquals(listOf(9L), opened)
    }
}
