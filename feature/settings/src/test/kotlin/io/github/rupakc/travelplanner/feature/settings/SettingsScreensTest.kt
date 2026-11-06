package io.github.rupakc.travelplanner.feature.settings

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.navigation.FeaturesRoute
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.features.FeatureGroup
import io.github.rupakc.travelplanner.feature.settings.features.FeatureRow
import io.github.rupakc.travelplanner.feature.settings.features.FeaturesEvent
import io.github.rupakc.travelplanner.feature.settings.features.FeaturesScreen
import io.github.rupakc.travelplanner.feature.settings.features.FeaturesUiState
import io.github.rupakc.travelplanner.feature.settings.preferences.PreferencesEvent
import io.github.rupakc.travelplanner.feature.settings.preferences.PreferencesScreen
import io.github.rupakc.travelplanner.feature.settings.preferences.PreferencesUiState
import io.github.rupakc.travelplanner.feature.settings.preferences.SaveStatus
import io.github.rupakc.travelplanner.feature.settings.preferences.TravellerField
import io.github.rupakc.travelplanner.feature.settings.root.SettingsEvent
import io.github.rupakc.travelplanner.feature.settings.root.SettingsNav
import io.github.rupakc.travelplanner.feature.settings.root.SettingsScreen
import io.github.rupakc.travelplanner.feature.settings.root.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2400dp")
class SettingsScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `root shows account, type and routes rows, sign out asks for confirmation`() {
        val events = mutableListOf<SettingsEvent>()
        val navs = mutableListOf<SettingsNav>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    SettingsUiState(
                        loading = false,
                        user = User("alice", email = "alice@example.com"),
                        travellerTypes = listOf(TravellerType.FAMILY),
                        featuresOn = 5,
                        versionName = "1.2.3",
                    ),
                    onEvent = { events += it },
                    onNav = { navs += it },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("alice").assertExists()
        compose.onNodeWithText("Family Traveller").assertExists()
        compose.onNodeWithText("Retake quiz").performClick()
        compose.onNodeWithText("Change password").performClick()
        compose.onNodeWithText("Features").performClick()
        compose.onNodeWithText("Features on: 5 of 18").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sign out"))
        compose.onNodeWithText("Sign out").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Contains data from GeoNames (geonames.org), CC BY 4.0"))
        assertEquals(listOf(SettingsNav.RetakeQuiz, SettingsNav.ChangePassword, SettingsNav.Open(FeaturesRoute)), navs)
        assertEquals(listOf<SettingsEvent>(SettingsEvent.AskSignOut), events)
    }

    @Test
    fun `sign out dialog confirms`() {
        val events = mutableListOf<SettingsEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.DARK) {
                SettingsScreen(SettingsUiState(loading = false, confirmingSignOut = true), onEvent = { events += it }, onNav = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Sign out?").assertExists()
        compose.onAllNodes(hasText("Sign out")).let { it[it.fetchSemanticsNodes().size - 1].performClick() }
        assertEquals(listOf<SettingsEvent>(SettingsEvent.ConfirmSignOut), events)
    }

    @Test
    fun `preferences save error path shows the backend detail`() {
        val events = mutableListOf<PreferencesEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                PreferencesScreen(
                    PreferencesUiState(
                        loading = false,
                        prefs = Preferences(adults = 1),
                        saveStatus = SaveStatus.Failed(AppError.Client(422, "budget_category is invalid")),
                    ),
                    onEvent = { events += it },
                    onBack = {},
                    onOpenTaste = {},
                )
            }
        }
        compose.onNodeWithText("Mid-range").performClick()
        compose.onNodeWithContentDescription("Add one: Children").performClick()
        compose.onNodeWithContentDescription("Remove one: Adults").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Save Preferences"))
        compose.onNodeWithText("Error: budget_category is invalid").assertExists()
        compose.onNodeWithText("Save Preferences").performClick()
        assertEquals(
            listOf(PreferencesEvent.SetBudget("medium"), PreferencesEvent.ChangeCount(TravellerField.CHILDREN, 1), PreferencesEvent.Save),
            events,
        )
    }

    @Test
    fun `preferences load error offers retry`() {
        val events = mutableListOf<PreferencesEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                PreferencesScreen(PreferencesUiState(loading = false, loadError = AppError.Network()), onEvent = {
                    events += it
                }, onBack = {}, onOpenTaste = {})
            }
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Retry), events)
    }

    @Test
    fun `feature switches expose state and toggle`() {
        val events = mutableListOf<FeaturesEvent>()
        val rows = FeatureGroup.entries.flatMap { it.members }.map { FeatureRow(it, on = it == FeatureId.CHAT, defaultOn = it == FeatureId.CHAT) }
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                FeaturesScreen(FeaturesUiState(loading = false, rows = rows, hasOverrides = true), onEvent = { events += it }, onBack = {})
            }
        }
        compose.onNode(hasText("Chat") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState), useUnmergedTree = false).assertIsOn()
        compose.onNode(hasText("Compare") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertIsOff().performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Reset to my type's defaults"))
        compose.onNodeWithText("Reset to my type's defaults").performClick()
        assertEquals(listOf(FeaturesEvent.Toggle(FeatureId.COMPARE, true), FeaturesEvent.ResetToDefaults), events)
        assertTrue(compose.onAllNodes(hasText("On for your type", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `nearby always switch is disabled while nearby suggestions are off`() {
        val toggles = mutableListOf<io.github.rupakc.travelplanner.feature.settings.notifications.SetToggle>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                io.github.rupakc.travelplanner.feature.settings.notifications.NotificationSettingsScreen(
                    settings = io.github.rupakc.travelplanner.core.model.settings.AppSettings(nearbyNotifications = false),
                    systemEnabled = true,
                    onToggle = { toggles += it },
                    onOpenSystem = {},
                    onBack = {},
                )
            }
        }
        val always = hasText("Suggest places even when I'm not on a trip") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
        compose.onNode(always).assertIsNotEnabled()
        compose.onNodeWithText("Only if you opened Near me in the last 24 hours. Nothing is looked up until you open it.").assertExists()
        compose.onNode(hasText("Nearby suggestions") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performClick()
        assertEquals(
            listOf(
                io.github.rupakc.travelplanner.feature.settings.notifications.SetToggle(
                    io.github.rupakc.travelplanner.feature.settings.notifications.NotificationToggle.NEARBY,
                    true,
                ),
            ),
            toggles,
        )
    }

    @Test
    fun `nearby always toggles when nearby suggestions are on`() {
        val toggles = mutableListOf<io.github.rupakc.travelplanner.feature.settings.notifications.SetToggle>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.DARK) {
                io.github.rupakc.travelplanner.feature.settings.notifications.NotificationSettingsScreen(
                    settings = io.github.rupakc.travelplanner.core.model.settings.AppSettings(nearbyNotifications = true),
                    systemEnabled = true,
                    onToggle = { toggles += it },
                    onOpenSystem = {},
                    onBack = {},
                )
            }
        }
        compose.onNode(hasText("Suggest places even when I'm not on a trip") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .assertIsEnabled()
            .assertIsOff()
            .performClick()
        assertEquals(io.github.rupakc.travelplanner.feature.settings.notifications.NotificationToggle.NEARBY_ALWAYS, toggles.single().toggle)
        assertTrue(toggles.single().enabled)
    }

    @Test
    fun `server switch asks for confirmation before signing out`() {
        val events = mutableListOf<io.github.rupakc.travelplanner.feature.settings.server.ServerEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                io.github.rupakc.travelplanner.feature.settings.server.ServerScreen(
                    io.github.rupakc.travelplanner.feature.settings.server.ServerUiState(confirmingSwitch = true, pendingUrl = "https://new.example"),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Switch servers?").assertExists()
        compose.onNodeWithText("Switching to https://new.example signs you out", substring = true).assertExists()
        compose.onNodeWithText("Switch and sign out").performClick()
        assertEquals(listOf<Any>(io.github.rupakc.travelplanner.feature.settings.server.ServerEvent.ConfirmSwitch), events)
    }
}
