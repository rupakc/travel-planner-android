package io.github.rupakc.travelplanner.feature.auth.login

import android.app.Application
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.component.SocialProvider
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LoginSocialTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: LoginUiState = LoginUiState(), onEvent: (LoginEvent) -> Unit = {}) =
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { LoginScreen(state, onEvent) } }

    @Test
    fun `four provider buttons in order Apple, Google, Facebook, GitHub`() {
        show()
        val tops =
            listOf("Apple", "Google", "Facebook", "GitHub").map {
                compose.onNodeWithText("Continue with $it").assertExists().getUnclippedBoundsInRoot().top
            }
        assertEquals(tops.sorted(), tops)
        assertEquals(4, tops.distinct().size)
        // Then the "or" divider, then the form.
        val divider = compose.onNodeWithText("or").getUnclippedBoundsInRoot().top
        val username = compose.onNodeWithText("Username").getUnclippedBoundsInRoot().top
        assertTrue(tops.last() < divider && divider < username)
    }

    @Test
    fun `tapping a provider reports it`() {
        val events = mutableListOf<LoginEvent>()
        show(onEvent = { events += it })
        compose.onNodeWithText("Continue with Google").performClick()
        assertEquals(listOf<LoginEvent>(LoginEvent.SocialProviderTapped(SocialProvider.GOOGLE)), events)
    }

    @Test
    fun `coming soon note is a polite live region`() {
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        show()
        // Always composed, so TalkBack announces the note when it appears.
        compose.onNodeWithTag(COMING_SOON_REGION_TAG).assertExists().assert(polite)
    }

    @Test
    fun `coming soon note names the provider`() {
        show(LoginUiState(comingSoonProvider = "Google"))
        compose.onNodeWithTag(COMING_SOON_REGION_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNode(hasText("Google sign-in is coming soon — use your username and password for now.")).assertExists()
    }

    @Test
    fun `privacy choices footer opens the choices`() {
        val events = mutableListOf<LoginEvent>()
        show(onEvent = { events += it })
        compose.onNodeWithText("Privacy choices").performScrollTo().performClick()
        assertEquals(listOf<LoginEvent>(LoginEvent.OpenPrivacyChoices), events)
    }
}
