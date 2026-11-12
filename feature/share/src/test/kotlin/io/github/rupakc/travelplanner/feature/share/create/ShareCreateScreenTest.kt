package io.github.rupakc.travelplanner.feature.share.create

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.share.CreatedShare
import io.github.rupakc.travelplanner.core.model.share.ShareDates
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ShareCreateScreenTest {
    @get:Rule val compose = createComposeRule()

    private val link = CreatedShare(token = "tok123", kind = "plan", url = "https://example.test/share/tok123")

    private fun show(state: ShareCreateUiState, events: MutableList<ShareCreateEvent> = mutableListOf(), dark: Boolean = false) =
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                ShareCreateScreen(state, { events += it }, {})
            }
        }

    @Test
    fun `a plan shows the options and sends the choices`() {
        val events = mutableListOf<ShareCreateEvent>()
        show(ShareCreateUiState(kind = ShareKind.PLAN), events)
        compose.onNodeWithText("Share this plan").assertIsDisplayed()
        compose.onNodeWithText("Hide dates").assertIsDisplayed()
        compose.onNodeWithText("Month and year only").performClick()
        compose.onNodeWithText("Where I'm travelling from").performClick()
        compose.onNodeWithText("My name").performScrollTo().performClick()
        compose.onNodeWithText("Create link").performScrollTo().performClick()
        assertEquals(
            listOf(
                ShareCreateEvent.SetDates(ShareDates.MONTH),
                ShareCreateEvent.SetShowOrigin(true),
                ShareCreateEvent.SetShowName(true),
                ShareCreateEvent.Submit,
            ),
            events,
        )
    }

    @Test
    fun `savings has the name option only`() {
        show(ShareCreateUiState(kind = ShareKind.SAVINGS), dark = true)
        compose.onNodeWithText("Share your catches").assertIsDisplayed()
        compose.onNodeWithText("My name").assertIsDisplayed()
        compose.onNodeWithText("Hide dates").assertDoesNotExist()
    }

    @Test
    fun `a created link offers share copy and stop sharing with a confirmation`() {
        val events = mutableListOf<ShareCreateEvent>()
        show(ShareCreateUiState(created = link), events)
        compose.onNodeWithText("https://example.test/share/tok123").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Update link").assertIsNotEnabled()
        compose.onNodeWithText("Share…").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Copy link").performScrollTo().performClick()
        compose.onNodeWithText("Stop sharing").performScrollTo().performClick()
        assertEquals(listOf(ShareCreateEvent.Share, ShareCreateEvent.Copy, ShareCreateEvent.AskRevoke), events)
    }

    @Test
    fun `changed options disable sharing until the link is updated`() {
        show(ShareCreateUiState(created = link, options = ShareOptions("exact", false, false), dirty = true))
        compose.onNodeWithText("Update link").assertIsEnabled()
        compose.onNodeWithText("Share…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Options changed. Update the link to share it.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `revoke confirmation and failures use the server text`() {
        val events = mutableListOf<ShareCreateEvent>()
        show(ShareCreateUiState(created = link, confirmRevoke = true, message = ShareCreateMessage.Failed(AppError.RateLimited("Slow down a bit", 5))), events)
        compose.onNodeWithText("Slow down a bit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Yes, stop sharing").performScrollTo().performClick()
        compose.onNodeWithText("Keep").performScrollTo().performClick()
        assertEquals(listOf(ShareCreateEvent.ConfirmRevoke, ShareCreateEvent.CancelRevoke), events)
    }

    @Test
    fun `sharing disabled is translated and an offline message shown`() {
        show(ShareCreateUiState(message = ShareCreateMessage.Failed(AppError.NotFound("raw", "sharing_disabled"))))
        compose.onNodeWithText("Sharing links is not available right now.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `share sheet carries the card and the link, or the link alone`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(context.cacheDir, "share-cards").apply { mkdirs() }
        val card = File(dir, "share-card.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val text = ShareSheet.text("My trip plan on Voyager", "https://example.test/share/tok123")
        assertEquals("My trip plan on Voyager\nhttps://example.test/share/tok123", text)

        val withCard = ShareSheet.intent(context, text, card, "Share via")
        val inner = withCard.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png", inner.type)
        assertEquals(text, inner.getStringExtra(Intent.EXTRA_TEXT))
        val uri = inner.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("${context.packageName}.share.cards", uri.authority)
        assertTrue(inner.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        val linkOnly = ShareSheet.intent(context, text, null, "Share via").getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("text/plain", linkOnly.type)
        assertNull(linkOnly.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
    }
}
