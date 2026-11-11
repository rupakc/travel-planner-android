package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SocialSignInButtonTest {
    @get:Rule val rule = createComposeRule()

    private val tids = mutableListOf<String>()
    private var clicks = 0

    private fun show() =
        rule.setContent {
            TravelTheme {
                CompositionLocalProvider(LocalUiEventSink provides UiEventSink { _, tid, _ -> tids += tid }) {
                    SocialSignInButton(SocialProvider.GITHUB, "Continue with GitHub", onClick = { clicks++ })
                }
            }
        }

    @Test fun `social button reports auth provider github and shows the label`() {
        show()
        rule.onNodeWithText("Continue with GitHub").performClick()
        assertEquals(listOf("auth.provider.github"), tids)
        assertEquals(1, clicks)
    }

    @Test fun `social button is at least 48 dp tall`() {
        show()
        rule.onNodeWithText("Continue with GitHub").assertHeightIsAtLeast(48.dp)
    }
}
