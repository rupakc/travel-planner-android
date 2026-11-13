package io.github.rupakc.travelplanner.feature.alerts.savings

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.alerts.NotificationRow
import java.time.Instant
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SavingsScreensTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-05T12:00:00Z")

    private fun note(kind: String, data: String) =
        AppNotification(id = 1, kind = kind, title = "Title", data = TravelJson.parseToJsonElement(data).jsonObject, createdAt = "2026-11-05T11:00:00Z")

    private fun show(state: SavingsUiState, onEvent: (SavingsEvent) -> Unit = {}) =
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { SavingsHeader(state, onEvent) } }

    @Test
    fun `tally shows the caught total, the milestone and the honest caveat`() {
        val s = Savings(totalUsd = 245.0, dropsCaught = 4, targetsHit = 1, nextMilestone = 1000, progressPct = 24).view()
        show(SavingsUiState.Loaded(s, livePricesPaused = true))
        compose.onNodeWithText("$245").assertIsDisplayed()
        compose.onAllNodesWithText("Drops caught").assertCountEquals(1)
        compose.onNodeWithText("24% of the way to $1,000").assertIsDisplayed()
        compose.onNodeWithText("4 drops caught · 1 target hit").assertIsDisplayed()
        compose.onNodeWithText("Live checks are paused, so these figures come from saved data.").assertIsDisplayed()
    }

    @Test
    fun `empty tally and a hidden one`() {
        show(SavingsUiState.Loaded(Savings().view(), livePricesPaused = false))
        compose.onNodeWithText("Your first drop will show here").assertIsDisplayed()
    }

    @Test
    fun `failed tally offers retry`() {
        val sent = mutableListOf<SavingsEvent>()
        show(SavingsUiState.Failed(null)) { sent += it }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf<SavingsEvent>(SavingsEvent.Retry), sent)
    }

    @Test
    fun `a drop notification shows the target variant and the XP line only when the game is visible`() {
        val n =
            note(
                "fare_target",
                """{"drop":{"from":500,"to":420,"amount":80,"pct":16,"currency":"USD","target_hit":true},"game":{"xp":25}}""",
            )
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { NotificationRow(n, now, gameVisible = true) {} } }
        compose.onNodeWithText("Your target price is here").assertIsDisplayed()
        compose.onNodeWithText("Caught a drop of $80 (16%)").assertIsDisplayed()
        compose.onNodeWithText("+25 XP").assertIsDisplayed()
    }

    @Test
    fun `a digest renders its rows and unknown rows still render as plain notifications`() {
        val digest =
            note(
                "price_digest",
                """{"digest":{"week":"2026-W46","watches":[
                    {"kind":"hotel","watch_id":3,"title":"Lumen Hotel","currency":"EUR","latest":398,"change_7d":-12}]}}""",
            )
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { NotificationRow(digest, now) {} } }
        compose.onNodeWithText("Weekly digest · 2026-W46").assertIsDisplayed()
        compose.onNodeWithText("Lumen Hotel").assertIsDisplayed()
        compose.onNodeWithText("Down €12 this week").assertIsDisplayed()
    }

    @Test
    fun `an unknown kind with odd data does not break the row`() {
        val odd = note("social_like", """{"drop":7,"digest":[]}""")
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { NotificationRow(odd, now) {} } }
        compose.onNodeWithText("Title").assertIsDisplayed()
    }
}
