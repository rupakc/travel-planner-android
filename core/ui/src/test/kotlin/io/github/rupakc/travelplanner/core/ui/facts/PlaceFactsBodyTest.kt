package io.github.rupakc.travelplanner.core.ui.facts

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.intl.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PickCitation
import io.github.rupakc.travelplanner.core.model.plan.PlaceFactPick
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlaceFactsBodyTest {
    @get:Rule val compose = createComposeRule()

    private val data =
        TravelJson.decodeFromString(
            PlaceFactsData.serializer(),
            """{"places":[
              {"key":"city:2950159","scope":"city","name":"Berlin","groups":{
                "historical":[{"id":"c:1","fact_type":"historical","text":"Berlin was founded in 1237.","origin":"crawler","lang":"en",
                  "citation":{"name":"Wikidata","url":"https://www.wikidata.org/wiki/Q64","as_of":"2026-11-20","attribution":"Wikidata (CC0 1.0)",
                    "licence_url":"https://creativecommons.org/publicdomain/zero/1.0/","conflict":false}}],
                "fun":[{"id":"a:2","fact_type":"fun","text":"Berlin has more bridges than Venice.","origin":"ai","citation":null}],
                "popular":[],"unbelievable":[],"little_known":[]}},
              {"key":"country:DE","scope":"country","name":"Germany","groups":{
                "popular":[{"id":"c:3","fact_type":"popular","text":"The official name is “Bundesrepublik Deutschland”.","origin":"crawler",
                  "lang":"en","value_lang":"de","machine_translated":true,"citation":{"name":"Wikidata","conflict":true}}]}},
              {"key":"region:x","scope":"region","name":"Empty","groups":{"fun":[{"text":"  ","origin":"ai"}]}}]}""",
        )

    private var selections by mutableStateOf(PlanSelections())
    private val opened = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PlaceFactsBody(
                        data,
                        isSelected = { PlanSelectionRules.isSelected(selections, it) },
                        onToggle = { selections = PlanSelectionRules.toggle(selections, it) },
                        onOpenLink = { opened += it },
                    )
                }
            }
        }
    }

    @Test
    fun `place and group chips, sourced fact with its source line`() {
        show()
        compose.onNodeWithText("Berlin · City").assertIsDisplayed()
        compose.onNodeWithText("Germany · Country").assertIsDisplayed()
        compose.onNodeWithText("Empty · Region").assertDoesNotExist()
        compose.onNodeWithText("Historical (1)").assertIsDisplayed()
        compose.onNodeWithText("Fun (1)").assertIsDisplayed()
        compose.onNodeWithText("Popular (1)").assertDoesNotExist()
        compose.onNodeWithText("Berlin was founded in 1237.").assertIsDisplayed()
        compose.onNodeWithText("Source: Wikidata").performClick()
        compose.onNodeWithText("as of ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Wikidata (CC0 1.0)").performClick()
        assertEquals(listOf("https://www.wikidata.org/wiki/Q64", "https://creativecommons.org/publicdomain/zero/1.0/"), opened)
        compose.onNodeWithTag("fact_ai_label").assertDoesNotExist()
    }

    @Test
    fun `AI facts carry the label and note but no source`() {
        show()
        compose.onNodeWithText("Fun (1)").performClick()
        compose.onNodeWithText("Berlin has more bridges than Venice.").assertIsDisplayed()
        compose.onNodeWithText("AI-suggested, not verified").assertIsDisplayed()
        compose.onNodeWithText("AI-suggested facts come from the model's general knowledge", substring = true).assertIsDisplayed()
        compose.onAllNodesWithTag("fact_source_line").assertCountEquals(0)
    }

    @Test
    fun `machine translation, disagreeing sources and the quoted value language`() {
        show()
        compose.onNodeWithText("Germany · Country").performClick()
        compose.onNodeWithText("Machine-translated from the source language").assertIsDisplayed()
        compose.onNodeWithText("Sources disagree on this — check the official site.").assertIsDisplayed()
        val text = factText("The official name is “Bundesrepublik Deutschland”.", "en", "de")
        val spans = text.spanStyles.map { text.text.substring(it.start, it.end) to it.item.localeList }
        assertEquals(LocaleList("en"), spans.first { it.first == text.text }.second)
        assertEquals(LocaleList("de"), spans.first { it.first == "Bundesrepublik Deutschland" }.second)
        assertEquals(emptyList<Any>(), factText("Plain", "und", "und").spanStyles)
    }

    @Test
    fun `add to plan stores the web selection shape and toggles off`() {
        show()
        compose.onNodeWithText("Add to plan").performClick()
        val pick = selections.placeFacts.single()
        assertEquals("c:1", pick.id)
        assertEquals("Berlin", pick.place)
        assertEquals("Wikidata", pick.citation?.name)
        compose.onNodeWithText("In your plan").performClick()
        assertEquals(emptyList<PlaceFactPick>(), selections.placeFacts)
        compose.onNodeWithText("Fun (1)").performClick()
        compose.onNodeWithText("Add to plan").performClick()
        assertNull(selections.placeFacts.single().citation)
        assertEquals(true, PlanSelectionRules.isSelected(selections, PlanItem.PlaceFactItem(PlaceFactPick(id = "a:2"))))
    }

    @Test
    fun `nothing renders without facts`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { PlaceFactsBody(PlaceFactsData(), { false }, {}, {}) }
        }
        compose.onNodeWithTag("place_facts").assertDoesNotExist()
    }

    @Test
    fun `a selected fact shows its source line or the AI label`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                Column {
                    val citation = PickCitation(name = "Wikidata", attribution = "CC0")
                    SelectedPlaceFact(
                        PlaceFactPick(
                            id = "c:1",
                            text = "Founded 1237.",
                            place = "Berlin",
                            origin = "crawler",
                            citation = citation,
                        ),
                        onOpenLink = {
                        },
                    )
                    SelectedPlaceFact(PlaceFactPick(id = "a:2", text = "AI fact", place = "Berlin", origin = "ai"), onOpenLink = {})
                }
            }
        }
        compose.onNodeWithText("Berlin: Founded 1237.").assertIsDisplayed()
        compose.onNodeWithText("Source: Wikidata").assertIsDisplayed()
        compose.onNodeWithText("CC0").assertIsDisplayed()
        compose.onNodeWithText("Berlin: AI fact").assertIsDisplayed()
        compose.onAllNodesWithTag("fact_ai_label").assertCountEquals(1)
    }
}
