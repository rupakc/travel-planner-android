package io.github.rupakc.travelplanner.feature.onboarding

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OnboardingScreenTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val profile = FakeProfileRepository()
    private val preferences = ScriptedPreferencesRepository()
    private var finished = 0

    private fun launch(dark: Boolean = false): OnboardingViewModel {
        val vm = onboardingViewModel(profile, preferences)
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { OnboardingRoute(onFinished = { finished++ }, viewModel = vm) }
        }
        return vm
    }

    /** Brings [text] into view (lazy lists and scrolling columns), then taps it. */
    private fun click(text: String) {
        reveal(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    private fun reveal(matcher: SemanticsMatcher) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        repeat(lists.fetchSemanticsNodes().size) { i -> runCatching { lists[i].performScrollToNode(matcher) } }
    }

    @Test
    fun `next, back and skip move through the questions with progress`() {
        launch()
        compose.onNodeWithText("Welcome to Voyager").assertExists()
        click("Get started")
        compose.onNodeWithText("Who do you usually travel with?").assertExists()
        compose.onNode(hasContentDescription("Step 1 of 6")).assertExists()
        compose.onNodeWithText("Next").assertIsNotEnabled()

        click("For work")
        click("Next")
        compose.onNodeWithText("What's your typical budget?").assertExists()
        compose.onNode(hasContentDescription("Step 2 of 6")).assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Who do you usually travel with?").assertExists()
        reveal(hasText("For work"))
        compose.onNodeWithText("For work").assertIsSelected()

        click("Next")
        click("Skip")
        compose.onNodeWithText("What do you love doing?").assertExists()
        compose.onNodeWithText("Choose all that apply").assertExists()
    }

    @Test
    fun `multi select toggles each option and describes its state`() {
        val vm = launch(dark = true)
        vm.onEvent(OnboardingEvent.Start)
        vm.onEvent(OnboardingEvent.Next)
        vm.onEvent(OnboardingEvent.Next)
        compose.waitForIdle()
        click("Food")
        click("Nightlife")
        reveal(hasText("Food"))
        compose.onNode(hasText("Food").and(isToggleable())).assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"))
        click("Food")
        compose.onNode(hasText("Food").and(isToggleable())).assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not selected"))
        reveal(hasText("Nightlife"))
        compose.onNode(hasText("Nightlife").and(isToggleable())).assertIsOn()
        assertEquals(listOf("nightlife"), vm.state.value.currentAnswer)
    }

    @Test
    fun `result shows the type, and change answers returns to question one`() {
        val vm = launch()
        vm.onEvent(OnboardingEvent.Start)
        vm.onEvent(OnboardingEvent.Select("family_kids"))
        repeat(6) { vm.onEvent(OnboardingEvent.Next) }
        compose.onNode(hasContentDescription("You're a Family Traveller")).assertExists()
        click("Not you? Change answers")
        compose.onNodeWithText("Who do you usually travel with?").assertExists()
    }

    @Test
    fun `feature switches toggle and finishing goes through the notification rationale`() {
        val vm = launch()
        vm.onEvent(OnboardingEvent.Start)
        vm.onEvent(OnboardingEvent.Select("work"))
        repeat(6) { vm.onEvent(OnboardingEvent.Next) }
        click("Continue")
        compose.onNodeWithText("Pick your features").assertExists()
        compose.onNodeWithText("You can change these any time in Settings.").assertExists()

        val sim = hasText("SIM cards").and(isToggleable())
        reveal(sim)
        compose.onNode(sim).assertIsOn().performClick()
        compose.onNode(sim).assertIsOff()

        compose.onNodeWithText("Finish").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Stay on top of your trips").assertExists()
        click("Not now")
        compose.waitForIdle()
        assertEquals(1, finished)
        assertTrue(profile.profileState.value.onboardingCompleted)
        assertTrue("sim" in profile.overridesState.value.disabled)
    }

    private fun isToggleable() = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
}
