package io.github.rupakc.travelplanner.feature.share.myshares

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.share.LegacyPlanShare
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import io.github.rupakc.travelplanner.core.model.share.ShareRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class MySharesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val row =
        ShareRow(
            token = "tok1",
            kind = "plan",
            title = "Lisbon in May",
            url = "https://h.test/share/tok1",
            options = ShareOptions(dates = "month", showOrigin = true),
            createdAt = "2026-11-12T08:00:00Z",
        )

    private fun show(state: MySharesUiState, events: MutableList<MySharesEvent> = mutableListOf(), dark: Boolean = false) =
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { MySharesScreen(state, { events += it }, {}) }
        }

    @Test
    fun `a row shows its title options and actions`() {
        val events = mutableListOf<MySharesEvent>()
        show(MySharesUiState(phase = MySharesPhase.Content, shares = listOf(row)), events)
        compose.onNodeWithText("Lisbon in May").assertIsDisplayed()
        compose.onNodeWithText("Month and year only", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Created 2026-11-12", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Copy link").performClick()
        assertEquals(MySharesEvent.Copy(row.url, "plan"), events.single())
        compose.onNodeWithText("Stop sharing").performClick()
        assertEquals(MySharesEvent.AskRevoke(RevokeTarget.Share(row)), events.last())
    }

    @Test
    fun `the legacy section lists old plan links`() {
        show(MySharesUiState(phase = MySharesPhase.Content, legacy = listOf(LegacyPlanShare(9, "Old trip", "https://h.test/share/old"))), dark = true)
        compose.onNodeWithText("Older plan links").assertIsDisplayed()
        compose.onNodeWithText("Old trip").assertIsDisplayed()
    }

    @Test
    fun `empty state explains where links appear`() {
        show(MySharesUiState(phase = MySharesPhase.Content))
        compose.onNodeWithText("No shared links yet").assertIsDisplayed()
    }

    @Test
    fun `error shows the backend text with retry`() {
        val events = mutableListOf<MySharesEvent>()
        show(MySharesUiState(phase = MySharesPhase.Error, error = AppError.RateLimited("Slow down please", null)), events)
        compose.onNodeWithText("Slow down please").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(MySharesEvent.Retry, events.single())
    }

    @Test
    fun `the confirmation uses stop sharing wording`() {
        val events = mutableListOf<MySharesEvent>()
        show(MySharesUiState(phase = MySharesPhase.Content, shares = listOf(row), confirm = RevokeTarget.Share(row)), events)
        compose.onNodeWithText("Anyone with the link will lose access.").assertIsDisplayed()
        compose.onNodeWithText("Yes, stop sharing").performClick()
        assertEquals(MySharesEvent.ConfirmRevoke, events.single())
    }
}
