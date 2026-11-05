package io.github.rupakc.travelplanner.core.ui

import android.app.Application
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** R4 ui M8: TalkBack reads "Gastronomía", not "steaming bowl, Gastronomía"; the emoji stays on screen. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EmojiSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `chips and buttons drop the leading emoji from what TalkBack reads`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                androidx.compose.foundation.layout.Column {
                    TpChip("🍜 Gastronomía", selected = true, onClick = {})
                    TpButton("📞 112", onClick = {})
                    TpChip("Plain", selected = false, onClick = {})
                }
            }
        }
        compose.onNodeWithText("🍜 Gastronomía", useUnmergedTree = true).assertContentDescriptionEquals("Gastronomía")
        compose.onNodeWithText("📞 112", useUnmergedTree = true).assertContentDescriptionEquals("112")
        compose.onNodeWithText("Plain", useUnmergedTree = true).assertContentDescriptionEquals()
    }
}
