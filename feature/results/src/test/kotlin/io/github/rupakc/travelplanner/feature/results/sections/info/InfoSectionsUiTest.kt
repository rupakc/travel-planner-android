package io.github.rupakc.travelplanner.feature.results.sections.info

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.EmergencyCardData
import io.github.rupakc.travelplanner.core.model.section.EventsData
import io.github.rupakc.travelplanner.core.model.section.ForexData
import io.github.rupakc.travelplanner.core.model.section.GettingAroundData
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.core.model.section.WeatherDay
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.sections.EmergencyCardSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.EventsSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.ForexSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.GettingAroundSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.PlacesToSeeSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.SimSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.TipsSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.VisaSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.WalkingToursSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.WeatherSectionContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Read-only and add-to-plan info sections rendered from the recorded contract fixture. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class InfoSectionsUiTest {
    @get:Rule val compose = createComposeRule()

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var darkTheme by mutableStateOf(false)

    /** Sets (or swaps) the content under test inside a scrollable, themed frame; setContent runs once per test. */
    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        val first = screen == null
        darkTheme = dark
        screen = content
        if (first) {
            compose.setContent {
                TravelTheme(themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { screen?.invoke() }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `weather renders the fixture day, the estimate note and the poor-weather alert`() {
        show { WeatherSectionContent(fixture(SectionId.WEATHER), FakeSectionActions()) }
        compose.onNodeWithContentDescription("Sunny", substring = true).assertExists()
        compose.onNodeWithText("Historical climate estimate", substring = true).assertDoesNotExist()

        val estimate =
            WeatherData(
                days = listOf(WeatherDay("2027-05-02", null, "Showers", 80, 19.0, 13.0, 6.2, isPoor = true)),
                poorWeatherDayCount = 1,
                source = "llm-estimate",
            )
        show(dark = true) { WeatherSectionContent(estimate, FakeSectionActions()) }
        compose.onNodeWithText("Historical climate estimate — actual conditions may vary").assertExists()
        compose.onNodeWithText("1 day with poor weather — outdoor activities may be affected").assertExists()
        compose.onNodeWithContentDescription("Poor weather", substring = true).assertExists()
    }

    @Test
    fun `places shows free entry and toggles add to plan`() {
        val actions = FakeSectionActions()
        show { PlacesToSeeSectionContent(fixture(SectionId.PLACES_TO_SEE), actions) }
        compose.onNodeWithText("Senso-ji").assertExists()
        compose.onNodeWithText("Free entry").assertExists()
        compose.onNodeWithContentDescription("Add Senso-ji to My Plan").performScrollTo().performClick()
        assertEquals(listOf("Senso-ji"), actions.value.placesToSee.map { it.name })
        compose.onNodeWithContentDescription("Remove Senso-ji from My Plan").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `places empty copy`() {
        show { PlacesToSeeSectionContent(PlacesData(), FakeSectionActions()) }
        compose.onNodeWithText("No places found").assertExists()
    }

    @Test
    fun `walking tours show top pick, open the provider page and add by url`() {
        val actions = FakeSectionActions()
        show { WalkingToursSectionContent(fixture(SectionId.WALKING_TOURS), actions) }
        compose.onNodeWithText("Asakusa free tour").assertExists()
        compose.onNodeWithText("Top pick").assertExists()
        compose.onNodeWithText("View on GuruWalk ↗").performScrollTo().performClick()
        assertEquals(listOf("https://www.guruwalk.com/tokyo/1"), actions.opened)
        compose.onNodeWithContentDescription("Add Asakusa free tour to My Plan").performScrollTo().performClick()
        assertEquals(listOf("https://www.guruwalk.com/tokyo/1"), actions.value.walkingTours.map { it.url })
    }

    @Test
    fun `walking tours empty and error copy`() {
        show { WalkingToursSectionContent(WalkingToursData(), FakeSectionActions()) }
        compose.onNodeWithText("No free walking tours", substring = true).assertExists()
        show { WalkingToursSectionContent(WalkingToursData(error = "timed out"), FakeSectionActions()) }
        compose.onNodeWithText("Couldn't reach the walking-tour sites this time — the rest of your plan is unaffected.").assertExists()
    }

    @Test
    fun `events explain the typical-season marker to TalkBack`() {
        show(dark = true) { EventsSectionContent(fixture(SectionId.EVENTS), FakeSectionActions()) }
        compose.onNodeWithText("Cherry blossom season").assertExists()
        compose.onNodeWithContentDescription("typical season — exact dates may vary", substring = true).assertExists()
        compose.onNodeWithText("🌿 Nature").assertExists()
    }

    @Test
    fun `events empty and error copy`() {
        show { EventsSectionContent(EventsData(), FakeSectionActions()) }
        compose.onNodeWithText("No notable events found during your dates.").assertExists()
        show { EventsSectionContent(EventsData(error = "x"), FakeSectionActions()) }
        compose.onNodeWithText("Couldn't check local events this time — the rest of your plan is unaffected.").assertExists()
    }

    @Test
    fun `visa shows type, tiles, health, customs and the fallback`() {
        val actions = FakeSectionActions()
        show { VisaSectionContent(fixture(SectionId.VISA), actions) }
        compose.onNodeWithText("VISA FREE").assertExists()
        compose.onNodeWithText("90 days").assertExists()
        compose.onNodeWithText("Free").assertExists()
        compose.onNodeWithText("Hepatitis A").assertExists()
        compose.onNodeWithText("Narcotics").assertExists()
        compose.onNodeWithText("Official Source").performScrollTo().performClick()
        assertEquals(listOf("https://www.mofa.go.jp"), actions.opened)
        show { VisaSectionContent(VisaData(), FakeSectionActions()) }
        compose.onNodeWithText("Visa info unavailable — check your government's official travel portal").assertExists()
    }

    @Test
    fun `sim is a single selection`() {
        val actions = FakeSectionActions()
        show { SimSectionContent(fixture(SectionId.SIM), actions) }
        compose.onNodeWithText("10GB data").assertExists()
        compose.onNodeWithText("30 days validity").assertExists()
        compose.onNodeWithContentDescription("Add Airalo Moshi Moshi to My Plan").performScrollTo().performClick()
        assertEquals("Airalo", actions.value.sim?.provider)
        show { SimSectionContent(SimData(), FakeSectionActions()) }
        compose.onNodeWithText("No SIM plans found — check local carriers on arrival").assertExists()
    }

    @Test
    fun `tips carry a visible severity label`() {
        show { TipsSectionContent(fixture(SectionId.TIPS), FakeSectionActions()) }
        compose.onNodeWithText("Carry cash").assertExists()
        compose.onNodeWithText("Info").assertExists()
        show { TipsSectionContent(TipsData(), FakeSectionActions()) }
        compose.onNodeWithText("No travel tips available yet").assertExists()
    }

    @Test
    fun `emergency number tiles open the dialer`() {
        show { EmergencyCardSectionContent(fixture(SectionId.EMERGENCY_CARD), FakeSectionActions()) }
        compose.onNodeWithContentDescription("Call Police, 110").performClick()
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:110", intent.data.toString())
        compose.onNodeWithContentDescription("Tourist Police, no number listed").assertExists()
    }

    @Test
    fun `emergency card fallback`() {
        show { EmergencyCardSectionContent(EmergencyCardData(), FakeSectionActions()) }
        compose.onNodeWithText("Emergency information unavailable").assertExists()
    }

    @Test
    fun `getting around groups and tags`() {
        val actions = FakeSectionActions()
        show { GettingAroundSectionContent(fixture(SectionId.GETTING_AROUND), actions) }
        compose.onNodeWithText("In the City").assertExists()
        compose.onNodeWithText("MRT").assertExists()
        compose.onNodeWithContentDescription("Add Tokyo Metro to My Plan").performScrollTo().performClick()
        assertEquals(listOf("Tokyo Metro"), actions.value.gettingAround.map { it.name })
        show { GettingAroundSectionContent(GettingAroundData(), FakeSectionActions()) }
        compose.onNodeWithText("No transport info available — check local transit apps on arrival").assertExists()
    }

    @Test
    fun `forex shows currency and rates, else the fallback`() {
        show(dark = true) { ForexSectionContent(fixture(SectionId.FOREX), FakeSectionActions()) }
        compose.onNodeWithText("Japanese yen (JPY) ¥").assertExists()
        compose.onNodeWithText("151.2").assertExists()
        show { ForexSectionContent(ForexData(), FakeSectionActions()) }
        compose.onNodeWithText("Currency info unavailable — check xe.com for live rates").assertExists()
    }

    @Test
    fun `no external link is opened for non-http urls`() {
        assertNull(safeHttpUrl("intent://evil"))
        assertTrue(FakeSectionActions().opened.isEmpty())
    }
}
