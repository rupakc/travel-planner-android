package io.github.rupakc.travelplanner.feature.alerts.savings

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.ui.share.LocalShareLauncher
import io.github.rupakc.travelplanner.core.ui.share.ShareLauncher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The savings tally's share entry exists only with Share links 2.0 and only once there is something to share. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SavingsShareEntryTest {
    @get:Rule val compose = createComposeRule()

    private val loaded = SavingsUiState.Loaded(Savings(totalUsd = 245.0, dropsCaught = 4, targetsHit = 1, nextMilestone = 1000, progressPct = 24).view(), false)

    private fun show(state: SavingsUiState, launcher: ShareLauncher?) =
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalShareLauncher provides launcher) { SavingsHeader(state, {}) }
            }
        }

    @Test
    fun `share link opens the savings dialog`() {
        val opened = mutableListOf<Pair<ShareKind, Long?>>()
        show(loaded) { kind, id -> opened += kind to id }
        compose.onNodeWithText("Share link").performClick()
        assertEquals(listOf(ShareKind.SAVINGS to null), opened)
    }

    @Test
    fun `no button without the flag or on an empty tally`() {
        show(loaded, null)
        compose.onNodeWithText("Share link").assertDoesNotExist()
    }

    @Test
    fun `an empty tally has nothing to share`() {
        show(SavingsUiState.Loaded(Savings().view(), false)) { _, _ -> }
        compose.onNodeWithText("Share link").assertDoesNotExist()
    }
}
