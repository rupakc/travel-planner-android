package io.github.rupakc.travelplanner.feature.results.sections

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.sections.info.FakeSectionActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The TIM "≈ N kg CO₂e" chip on flight cards: per leg on multi-city trips, absent without `emissions`. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1700dp")
class FlightEmissionsChipTest {
    @get:Rule val compose = createComposeRule()

    private fun render(json: String) {
        val data = TravelJson.decodeFromString(FlightsData.serializer(), json)
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { FlightsSectionContent(data, FakeSectionActions()) } }
        compose.waitForIdle()
    }

    @Test
    fun `multi-city leg flights show the chip only when they carry emissions`() {
        render(
            """{"trip_type":"multi_city","results":[],"legs":[
              {"leg_index":0,"label":"London → Paris","from":"LHR","to":"CDG","date":"2026-12-01","results":[
                {"price_usd":120,"leg_index":0,"outbound":{"airline":"Air France","flight_number":"AF1081"},
                 "emissions":{"kg_co2e_per_pax":54,"cabin":"economy","by_class":{"economy":54,"business":81}}}]},
              {"leg_index":1,"label":"Paris → Rome","from":"CDG","to":"FCO","date":"2026-12-05","results":[
                {"price_usd":95,"leg_index":1,"outbound":{"airline":"ITA Airways","flight_number":"AZ317"}}]}
            ]}""",
        )
        compose.onAllNodesWithTag("flight_emissions", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("≈ 54 kg CO₂e", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `round-trip flights without emissions show no chip`() {
        render(
            """{"trip_type":"round_trip","results":[
              {"price_usd":800,"outbound":{"airline":"ANA","flight_number":"NH9"},"return":{"airline":"ANA","flight_number":"NH10"}}]}""",
        )
        compose.onAllNodesWithTag("flight_emissions", useUnmergedTree = true).assertCountEquals(0)
    }
}
