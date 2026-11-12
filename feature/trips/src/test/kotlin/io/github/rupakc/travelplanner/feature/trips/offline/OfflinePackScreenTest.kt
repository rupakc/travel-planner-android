package io.github.rupakc.travelplanner.feature.trips.offline

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The viewer is stateless and reads only the pack: everything below renders with no network at all. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class OfflinePackScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<OfflinePackEvent>()

    private val pack =
        TravelJson.decodeFromString(
            OfflinePack.serializer(),
            """
            {"version":1,"trip_id":12,"generated_at":"2027-03-30T10:00:00Z","title":"Lisbon",
             "emergency":{"source":"static","as_of":null,"cities":[{"city":"Lisbon","emergency_numbers":{"general":"112","police":"112"}}]},
             "phrases":{"language":"pt","language_name":"Português","items":[{"id":"help","text":"Socorro!","romanized":null}]},
             "address_card":{"hotel_name":"Hotel Avenida","area":"Baixa","city":"Lisbon","take_me_to":{"text":"Leve-me a:","romanized":null}},
             "currency":{"local":"EUR","home":"USD","rate":0.92,"as_of":"2027-03-30T10:00:00Z","source":"snapshot"},
             "checkins":["2027-04-01"],
             "selections":{"itinerary":{"days":[{"day_number":1,"date":"2027-04-01","slots":[
               {"day_number":1,"time_of_day":"morning","activity":"Alfama walk","date":"2027-04-01"}]}]}}}
            """.trimIndent(),
        )

    /** Lazy rows below the fold are not composed until scrolled to. */
    private fun reveal(text: String, substring: Boolean = false) =
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring)).let { compose.onNodeWithText(text, substring = substring) }

    private fun show(state: OfflinePackUiState) =
        compose.setContent { TravelTheme(ThemeMode.LIGHT) { OfflinePackScreen(state, { events += it }) } }

    @Test
    fun `the whole pack renders from the saved copy`() {
        show(OfflinePackUiState(loading = false, view = PackView.of(pack), amount = "100"))
        compose.onNodeWithText("Emergency numbers").assertIsDisplayed()
        compose.onNodeWithText("From our reference list").assertIsDisplayed()
        compose.onAllNodesWithText("112").onFirst().assertIsDisplayed()
        reveal("Leve-me a:").assertIsDisplayed()
        reveal("Hotel Avenida").assertIsDisplayed()
        reveal("Socorro!").assertIsDisplayed()
        reveal("92 EUR").assertIsDisplayed()
        reveal("Alfama walk", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the rate is dated and called approximate`() {
        show(OfflinePackUiState(loading = false, view = PackView.of(pack)))
        reveal("approximate", substring = true).assertIsDisplayed()
    }

    @Test
    fun `show to a local opens the big text and reports the phrase`() {
        show(OfflinePackUiState(loading = false, view = PackView.of(pack)))
        reveal("Show to a local").performClick()
        assertEquals(listOf<OfflinePackEvent>(OfflinePackEvent.ShowPhrase(pack.phrases!!.items.single())), events)
    }

    @Test
    fun `the full screen phrase closes`() {
        show(OfflinePackUiState(loading = false, view = PackView.of(pack), shownPhrase = pack.phrases!!.items.single()))
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf<OfflinePackEvent>(OfflinePackEvent.ClosePhrase), events)
    }

    @Test
    fun `no pack offers save and a failure is announced`() {
        show(OfflinePackUiState(loading = false, failure = PackFailure(PackFailure.Kind.QUOTA)))
        compose.onNodeWithText("Not enough storage on this device to save the pack.").assertIsDisplayed()
        compose.onNodeWithText("Save for offline").performClick()
        assertEquals(listOf<OfflinePackEvent>(OfflinePackEvent.Save), events)
    }

    @Test
    fun `unsupported hides the save button`() {
        show(OfflinePackUiState(loading = false, failure = PackFailure(PackFailure.Kind.UNSUPPORTED)))
        compose.onNodeWithText("Offline packs aren't available on this server.").assertIsDisplayed()
        compose.onAllNodesWithText("Save for offline").assertCountEquals(0)
    }
}
