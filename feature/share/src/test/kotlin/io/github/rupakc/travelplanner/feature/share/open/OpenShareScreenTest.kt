package io.github.rupakc.travelplanner.feature.share.open

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.share.ShareCardSnapshot
import io.github.rupakc.travelplanner.core.model.share.ShareCardStat
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.feature.share.ShareScreen
import io.github.rupakc.travelplanner.feature.share.ShareUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OpenShareScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: ShareUiState, onOpenLink: (String) -> Unit = {}, onPlanOwn: () -> Unit = {}) {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { ShareScreen(state, {}, {}, onPlanOwn, onOpenLink, {}) }
        }
    }

    @Test
    fun `savings card shows headline, count chips and the sharer only when named`() {
        val card = ShareCardSnapshot("Caught drops worth \$412", listOf(ShareCardStat("drops_caught", 3), ShareCardStat("future_key", 9)))
        show(ShareUiState(isLoading = false, kind = ShareKind.SAVINGS, card = card, ownerName = "Ana"))
        compose.onNodeWithText("Caught drops worth \$412").assertIsDisplayed()
        compose.onNodeWithText("3 drops caught").assertIsDisplayed()
        compose.onNodeWithText("Shared by Ana").assertIsDisplayed()
    }

    @Test
    fun `recap card without a name shows no shared-by line`() {
        val card = ShareCardSnapshot("My trip to Japan: recap", listOf(ShareCardStat("places", 1)), destination = "Japan", period = "2027-04")
        show(ShareUiState(isLoading = false, kind = ShareKind.RECAP, card = card))
        compose.onNodeWithText("1 place").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTextCount("Shared by") == 0)
    }

    @Test
    fun `unknown kind offers the plain link in the browser`() {
        var opened: String? = null
        show(
            ShareUiState(isLoading = false, kind = ShareKind.UNKNOWN, unknownKind = true, plainLink = "https://h.example/share/tok"),
            onOpenLink = { opened = it },
        )
        compose.onNodeWithText("This link opens on the web").assertIsDisplayed()
        compose.onNodeWithText("Open in browser").performClick()
        assertEquals("https://h.example/share/tok", opened)
    }

    @Test
    fun `unknown kind without a web host offers no browser button`() {
        show(ShareUiState(isLoading = false, kind = ShareKind.UNKNOWN, unknownKind = true, plainLink = null))
        compose.onNodeWithText("This link opens on the web").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Open in browser"))
    }

    @Test
    fun `revoked link shows the not available state and offline shows retry`() {
        show(ShareUiState(isLoading = false, error = AppError.NotFound("Shared plan not found")))
        compose.onNodeWithText("This shared trip isn't available").assertIsDisplayed()
    }

    @Test
    fun `paste screen opens a pasted link and rejects junk`() {
        var token: String? = null
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { PasteShareLinkScreen(onBack = {}, onOpen = { token = it }) } }
        compose.onNodeWithText("Open").assertIsNotEnabled()
        val field = hasSetTextAction()
        compose.onNode(field).performTextInput("hello")
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("That doesn't look like a share link.", useUnmergedTree = true).assertExists()
        assertNull(token)
        compose.onNode(field).performTextClearance()
        compose.onNode(field).performTextInput("https://x.example/share/AbCdEf123")
        compose.onNodeWithText("Open").performClick()
        assertEquals("AbCdEf123", token)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().size
}
