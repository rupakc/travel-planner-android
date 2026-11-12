package io.github.rupakc.travelplanner.core.ui.share

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ShareEntryButtonTest {
    @get:Rule val compose = createComposeRule()

    private fun show(launcher: ShareLauncher?, kind: ShareKind, planId: Long?) =
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalShareLauncher provides launcher) { ShareEntryButton(kind, planId) }
            }
        }

    @Test
    fun `opens the dialog for its kind and plan`() {
        val opened = mutableListOf<Pair<ShareKind, Long?>>()
        show({ kind, plan -> opened += kind to plan }, ShareKind.RECAP, 8)
        compose.onNodeWithText("Share link").performClick()
        assertEquals(listOf(ShareKind.RECAP to 8L), opened)
    }

    @Test
    fun `savings needs no plan`() {
        val opened = mutableListOf<Pair<ShareKind, Long?>>()
        show({ kind, plan -> opened += kind to plan }, ShareKind.SAVINGS, null)
        compose.onNodeWithText("Share link").performClick()
        assertEquals(listOf(ShareKind.SAVINGS to null), opened)
    }

    @Test
    fun `nothing without the flag or without a plan`() {
        show(null, ShareKind.PLAN, 1)
        compose.onNodeWithText("Share link").assertDoesNotExist()
    }

    @Test
    fun `a plan kind without a plan id draws nothing`() {
        show({ _, _ -> }, ShareKind.PLAN, null)
        compose.onNodeWithText("Share link").assertDoesNotExist()
    }
}
