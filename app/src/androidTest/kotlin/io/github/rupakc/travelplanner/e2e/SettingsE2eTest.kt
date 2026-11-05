package io.github.rupakc.travelplanner.e2e

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.home.R as HomeR
import io.github.rupakc.travelplanner.feature.onboarding.R as OnboardingR
import io.github.rupakc.travelplanner.feature.settings.R as SettingsR
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flow 2: Settings -> retake quiz (edit mode), toggle a feature -> Home changes; dark theme; Español. */
@E2eTest
@RunWith(AndroidJUnit4::class)
class SettingsE2eTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val api = BackendApi()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        api.resetPreferences(api.adminToken())
        robot = AppRobot(rule)
    }

    @After fun resetLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test fun retakeQuiz_toggleFeature_theme_language() {
        robot.signInToHome()

        // Retake the quiz from Settings: edit mode, ends back in Settings.
        robot.openSettings()
        robot.tap(SettingsR.string.settings_retake_quiz)
        robot.waitForText(robot.str(OnboardingR.string.onboarding_retake_title))
        robot.completeOnboarding(
            listOf(
                listOf(OnboardingR.string.onboarding_opt_companions_family_kids),
                listOf(OnboardingR.string.onboarding_opt_budget_mid_range),
                emptyList(),
                listOf(OnboardingR.string.onboarding_opt_pace_relaxed),
                emptyList(),
                emptyList(),
            ),
        )
        robot.tap(OnboardingR.string.onboarding_continue)
        robot.tap(OnboardingR.string.onboarding_save_changes)
        val family = robot.str(UiR.string.traveller_type_family)
        robot.waitForText(family, substring = true)
        val prefs = api.preferences(api.adminToken())
        assertEquals(1, prefs.getValue("children").jsonPrimitive.int)

        // Home shows the new type on its chip.
        robot.back()
        robot.waitFor(robot.hasDescription(robot.str(HomeR.string.home_type_a11y, family), substring = true))
        val compare = hasText(robot.str(UiR.string.feature_compare_title))
        val before = robot.scrollToFind(compare)

        // Toggle one feature in Settings -> Features; Home's tiles follow.
        robot.openSettings()
        robot.tap(SettingsR.string.settings_features)
        robot.waitForText(robot.str(SettingsR.string.settings_features_intro))
        robot.scrollToFind(compare)
        robot.tap(robot.str(UiR.string.feature_compare_title))
        robot.back()
        robot.back()
        robot.waitForText(robot.str(HomeR.string.home_tools_heading))
        assertNotEquals("Compare tile should flip", before, robot.scrollToFind(compare))
        Screenshots.capture("home-light")

        // Dark theme.
        robot.openSettings()
        robot.tap(SettingsR.string.settings_appearance)
        robot.tap(SettingsR.string.settings_theme_dark)
        robot.back()
        robot.waitForText(robot.str(SettingsR.string.settings_title))
        Screenshots.capture("settings-dark")

        // Language: Español -> the UI switches (activity is recreated with the per-app locale).
        robot.tap(SettingsR.string.settings_language)
        robot.tap(SettingsR.string.settings_language_es)
        robot.waitForText("Ajustes", timeoutMs = AppRobot.LONG_TIMEOUT)
        robot.waitForText("Apariencia")
    }
}
