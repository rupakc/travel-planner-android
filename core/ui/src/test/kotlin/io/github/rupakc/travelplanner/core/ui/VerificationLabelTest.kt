package io.github.rupakc.travelplanner.core.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.Verification
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** R4 ui M6: the level reads in the app language even though the server sends an English `label`. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es")
class VerificationLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `known levels are localized, unknown ones keep the server label`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                Text(
                    verificationLabel(Verification(level = "verified", label = "Verified")) + " | " +
                        verificationLabel(Verification(level = "brand-new", label = "Novel")),
                )
            }
        }
        compose.onNodeWithText("Verificado | Novel").assertExists()
    }

    @Test
    fun `the chip shows the localized level`() {
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { VerificationChip(Verification(level = "partial", label = "Partly verified")) } }
        compose.onNodeWithText("Verificado en parte", substring = true, useUnmergedTree = true).assertExists()
    }
}
