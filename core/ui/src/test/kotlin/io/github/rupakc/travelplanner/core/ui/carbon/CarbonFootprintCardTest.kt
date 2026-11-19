package io.github.rupakc.travelplanner.core.ui.carbon

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonCardState
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import io.github.rupakc.travelplanner.core.model.carbon.FlightEmissions
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CarbonFootprintCardTest {
    @get:Rule val compose = createComposeRule()

    private val footprint =
        TravelJson.decodeFromString(
            CarbonFootprint.serializer(),
            """{"total_kg":245.1,"per_person_kg":122.5,"travellers":2,
            "categories":[{"key":"flights","kg":159.4,"share":0.6504,"included":true},{"key":"stays","kg":0,"share":0,"included":false},
              {"key":"food","kg":85.7,"share":0.3496,"included":true}],
            "lines":[{"category":"flights","kind":"flight_factor","label":"Flight LHR–CDG (348 km)","kg":159.4,"method":"factor",
                "params":{"route":"LHR–CDG","km":348},
                "crawler_source":{"name":"DESNZ 2027 factors","url":"https://www.gov.uk/x","as_of":"2027-06-10","attribution":"OGL v3.0"}},
              {"category":"food","kind":"meals","label":"Meals (mixed): days 4, travellers 2","kg":85.7,"method":"estimate",
                "params":{"diet":"mixed","days":4,"travellers":2}},
              {"category":"activities","kind":"brand_new_kind","label":"Hot-air widget (backend text)","kg":1.2,"method":"estimate"}],
            "comparisons":{"car_km":1477,"annual_budget_kg":2000,"budget_share":0.0613},
            "lower_carbon":[{"route":"LHR–CDG","rail_km":417,"saving_kg":149.9}],
            "sources":[{"id":"desnz_2026","name":"UK DESNZ 2026","url":"https://www.gov.uk","licence":"OGL v3.0","attribution":"Contains public sector information"}],
            "assumptions":[{"code":"rooms_default","text":"1 room(s)","params":{"rooms":1}},{"code":"ground_default_mix","text":"mix"}],
            "crawler_sources":[{"name":"DESNZ 2027 factors","as_of":"2027-06-10"}]}""",
        )

    private fun show(state: CarbonCardState, onDiet: (CarbonDiet) -> Unit = {}, onRetry: () -> Unit = {}) {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                    CarbonFootprintCard(state, onDiet = onDiet, onRetry = onRetry)
                }
            }
        }
    }

    @Test
    fun `hidden renders nothing`() {
        show(CarbonCardState.Hidden())
        compose.onNodeWithTag("carbon_footprint").assertDoesNotExist()
    }

    @Test
    fun `ready shows the total, breakdown, comparisons, hint and live sources`() {
        show(CarbonCardState.Ready(footprint, CarbonDiet.MIXED))
        compose.onNodeWithText("≈ 245 kg CO₂e").assertIsDisplayed()
        compose.onNodeWithText("123 kg per person").assertIsDisplayed()
        compose.onNodeWithTag("carbon_bar_flights", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("carbon_bar_stays", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("65%").assertExists()
        compose.onNodeWithText("Like driving 1,477 km in an average car").assertExists()
        compose.onNodeWithText("6% of a 2 t yearly budget per person").assertExists()
        compose.onNodeWithTag("carbon_rail_hint").assertExists()
        compose.onNodeWithTag("carbon_live_sources").assertExists()
        compose.onNodeWithText("Source: DESNZ 2027 factors").assertExists()
        compose.onNodeWithTag("carbon_how_panel").assertDoesNotExist()
    }

    @Test
    fun `how we calculate translates lines and assumptions and falls back to the backend text`() {
        show(CarbonCardState.Ready(footprint, CarbonDiet.MIXED))
        compose.onNodeWithText("How we calculate").performClick()
        compose.onNodeWithTag("carbon_how_panel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Flight LHR–CDG (348 km)").assertExists()
        compose.onNodeWithText("Meals (Mixed diet): days 4, travellers 2").assertExists()
        compose.onNodeWithText("Hot-air widget (backend text)").assertExists()
        compose.onNodeWithText("• Rooms: 1 (one per two travellers).").assertExists()
        compose.onNodeWithText("• Local trips: 40% metro, 40% bus, 20% taxi (no transport picked).").assertExists()
        compose.onNodeWithText("UK DESNZ 2026").assertExists()
        compose.onNodeWithText("Contains public sector information").assertExists()
    }

    @Test
    fun `a zero total still shows live sources and the train hint, but no bar or comparisons`() {
        val zero =
            TravelJson.decodeFromString(
                CarbonFootprint.serializer(),
                """{"total_kg":0,"categories":[],"comparisons":{"car_km":0,"budget_share":0},
                "lower_carbon":[{"route":"LHR–CDG","rail_km":417,"saving_kg":149.9}],
                "crawler_sources":[{"name":"DESNZ 2027 factors","as_of":"2027-06-10"}]}""",
            )
        show(CarbonCardState.Ready(zero, CarbonDiet.MIXED))
        compose.onNodeWithTag("carbon_live_sources").assertExists()
        compose.onNodeWithTag("carbon_rail_hint").assertExists()
        compose.onNodeWithTag("carbon_bar_flights", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Like driving", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the bar leaves the remainder empty when shown shares add up to less than one`() {
        val partial =
            TravelJson.decodeFromString(
                CarbonFootprint.serializer(),
                """{"total_kg":100,"categories":[{"key":"flights","kg":60,"share":0.6,"included":true},
                {"key":"brand_new_category","kg":40,"share":0.4,"included":true}]}""",
            )
        show(CarbonCardState.Ready(partial, CarbonDiet.MIXED))
        compose.onNodeWithTag("carbon_bar_flights", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("carbon_bar_rest", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the bar has no remainder when the shares fill it`() {
        show(CarbonCardState.Ready(footprint, CarbonDiet.MIXED))
        compose.onNodeWithTag("carbon_bar_rest", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the meals picker reports the new diet`() {
        var picked: CarbonDiet? = null
        show(CarbonCardState.Ready(footprint, CarbonDiet.MIXED), onDiet = { picked = it })
        compose.onNodeWithText("Vegan").performScrollTo().performClick()
        assertEquals(CarbonDiet.VEGAN, picked)
    }

    @Test
    fun `loading and failure with retry`() {
        var retried = 0
        show(CarbonCardState.Failed(CarbonDiet.MIXED), onRetry = { retried++ })
        compose.onNodeWithText("The footprint isn't available right now.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `loading shows the working copy`() {
        show(CarbonCardState.Loading(CarbonDiet.MIXED))
        compose.onNodeWithText("Working out the footprint…").assertIsDisplayed()
    }

    @Test
    fun `emissions chip shows the TIM figure and its breakdown on tap`() {
        val emissions =
            FlightEmissions(
                kgPerPassenger = 134.0,
                byClass = Json.parseToJsonElement("""{"economy":134,"business":201,"first":"n/a"}""").jsonObject,
            )
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { EmissionsChip(emissions) } }
        compose.onNodeWithText("≈ 134 kg CO₂e").assertIsDisplayed()
        compose.onNodeWithTag("flight_emissions_details").assertDoesNotExist()
        compose.onNodeWithTag("flight_emissions").performClick()
        compose.onNodeWithText("Economy: 134 kg").assertIsDisplayed()
        compose.onNodeWithText("Business: 201 kg").assertIsDisplayed()
        compose.onNodeWithText("First: n/a kg").assertDoesNotExist()
        compose.onNodeWithText("Emissions estimates: Google Travel Impact Model").assertIsDisplayed()
    }

    @Test
    fun `no emissions renders no chip`() {
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { EmissionsChip(null) } }
        compose.onNodeWithTag("flight_emissions").assertDoesNotExist()
    }
}
