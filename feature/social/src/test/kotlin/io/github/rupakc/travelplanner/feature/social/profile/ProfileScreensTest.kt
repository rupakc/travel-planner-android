package io.github.rupakc.travelplanner.feature.social.profile

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileBadge
import io.github.rupakc.travelplanner.core.model.social.ProfileRelation
import io.github.rupakc.travelplanner.core.model.social.PublicProfile
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.feature.social.report.ReportDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ProfileScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun themed(dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }

    private fun view(state: PublicProfileUiState, events: MutableList<PublicProfileEvent> = mutableListOf(), dark: Boolean = false) =
        themed(dark) { PublicProfileScreen(state, { events += it }, {}) }

    @Test
    fun `optional fields show only when present`() {
        view(
            PublicProfileUiState(
                handle = "ann",
                phase = ViewPhase.Content,
                profile = PublicProfile(handle = "ann", bio = "Slow travel fan", ideasCount = 2, followersCount = 5, followingCount = 1),
            ),
        )
        compose.onNodeWithText("Slow travel fan").assertIsDisplayed()
        compose.onNodeWithText("Follow").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Level"))
        assertEquals(0, compose.onAllNodesWithTextCount("Badges"))
        assertEquals(0, compose.onAllNodesWithTextCount("Home country"))
    }

    @Test
    fun `level and badges show when sent and not hidden`() {
        val profile = PublicProfile(handle = "ann", level = 4, badges = listOf(ProfileBadge("early_bird", "gold")), homeCountry = "PT")
        view(PublicProfileUiState(handle = "ann", phase = ViewPhase.Content, profile = profile), dark = true)
        compose.onNodeWithText("Level 4").assertIsDisplayed()
        compose.onNodeWithText("Early bird (gold)").assertIsDisplayed()
        compose.onNodeWithText("Home country").assertIsDisplayed()
    }

    @Test
    fun `hide_game omits level and badges even when sent`() {
        val profile = PublicProfile(handle = "ann", level = 4, badges = listOf(ProfileBadge("early_bird")))
        view(PublicProfileUiState(handle = "ann", phase = ViewPhase.Content, profile = profile, hideGame = true))
        assertEquals(0, compose.onAllNodesWithTextCount("Level 4"))
        assertEquals(0, compose.onAllNodesWithTextCount("Badges"))
    }

    @Test
    fun `bio is plain text, markup and links are not interpreted`() {
        val bio = "**bold** <b>x</b> https://evil.test"
        view(PublicProfileUiState(handle = "ann", phase = ViewPhase.Content, profile = PublicProfile(handle = "ann", bio = bio)))
        compose.onNodeWithText(bio).assertIsDisplayed()
    }

    @Test
    fun `follow states label the button and click toggles`() {
        val events = mutableListOf<PublicProfileEvent>()
        view(PublicProfileUiState(handle = "ann", phase = ViewPhase.Content, profile = PublicProfile(handle = "ann"), follow = FollowUi.Pending), events)
        compose.onNodeWithText("Requested").performClick()
        assertEquals(PublicProfileEvent.ToggleFollow, events.single())
    }

    @Test
    fun `follows you and your own profile`() {
        view(PublicProfileUiState(handle = "ann", phase = ViewPhase.Content, profile = PublicProfile(handle = "ann", you = ProfileRelation(followsYou = true))))
        compose.onNodeWithText("Follows you").assertIsDisplayed()
    }

    @Test
    fun `unavailable shows the calm message`() {
        view(PublicProfileUiState(handle = "ann", phase = ViewPhase.Unavailable))
        compose.onNodeWithText("This profile is not available").assertIsDisplayed()
    }

    @Test
    fun `the report dialog needs a reason and sends it`() {
        val sent = mutableListOf<ReportReason>()
        themed { ReportDialog(onDismiss = {}, onSubmit = { sent += it }) }
        compose.onNodeWithText("Send report").performClick()
        assertEquals(0, sent.size)
        compose.onNodeWithText("Spam").performClick()
        compose.onNodeWithText("Send report").performClick()
        assertEquals(listOf(ReportReason.SPAM), sent)
    }

    @Test
    fun `the new profile form asks for a handle and starts private`() {
        themed {
            MyProfileScreen(
                MyProfileUiState(phase = ProfilePhase.Content, original = OwnProfile(), form = ProfileForm()),
                {},
                {},
                {},
                {},
            )
        }
        compose.onNodeWithText("Create profile").assertExists()
        compose.onNodeWithText("Only me").assertExists()
    }

    @Test
    fun `hide_game removes the level and badge switches`() {
        themed {
            MyProfileScreen(
                MyProfileUiState(phase = ProfilePhase.Content, original = OwnProfile(handle = "ann"), form = ProfileForm(handle = "ann"), hideGame = true),
                {},
                {},
                {},
                {},
            )
        }
        assertEquals(0, compose.onAllNodesWithTextCount("Show my level"))
        compose.onNodeWithText("Show my photo").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
