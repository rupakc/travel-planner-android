package io.github.rupakc.travelplanner.feature.social.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SocialGateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a disabled gate shows nothing and sends the user away once`() {
        var blocked = 0
        compose.setContent { SocialGate(enabled = false, onBlocked = { blocked++ }) { Text("Feed") } }
        compose.waitForIdle()
        compose.onNodeWithText("Feed").assertDoesNotExist()
        assertEquals(1, blocked)
    }

    @Test
    fun `an enabled gate shows the screen`() {
        var blocked = 0
        compose.setContent { SocialGate(enabled = true, onBlocked = { blocked++ }) { Text("Feed") } }
        compose.onNodeWithText("Feed").assertExists()
        assertEquals(0, blocked)
    }
}
