package io.github.rupakc.travelplanner.core.ui.social

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class IdeaEntryButtonTest {
    @get:Rule val compose = createComposeRule()

    private fun show(launcher: IdeaLauncher?, planId: Long?) =
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalIdeaLauncher provides launcher) { IdeaEntryButton(planId, IdeaKind.RECAP) }
            }
        }

    @Test
    fun `opens the publish screen for its plan and kind`() {
        val opened = mutableListOf<Pair<Long, IdeaKind>>()
        show({ plan, kind -> opened += plan to kind }, 8)
        compose.onNodeWithText("Share as an idea").performClick()
        assertEquals(listOf(8L to IdeaKind.RECAP), opened)
    }

    @Test
    fun `draws nothing without a launcher or a plan`() {
        show(null, 8)
        compose.onNodeWithText("Share as an idea").assertDoesNotExist()
    }

    @Test
    fun `draws nothing for an unsaved plan`() {
        show({ _, _ -> }, null)
        compose.onNodeWithText("Share as an idea").assertDoesNotExist()
    }
}
