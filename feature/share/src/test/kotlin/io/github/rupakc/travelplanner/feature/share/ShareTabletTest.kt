package io.github.rupakc.travelplanner.feature.share

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** R4 ui M1: on a wide window the shared trip stays a readable column instead of stretching edge to edge. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w1280dp-h800dp")
class ShareTabletTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `trip column is capped on a wide window`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                ShareScreen(ShareUiState(isLoading = false, trip = SharedTrip.from(sharedFixture)), {}, {}, {}, {}, {})
            }
        }
        val bounds = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("trip column is $width wide", width <= 640.dp)
    }
}
