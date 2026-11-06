package io.github.rupakc.travelplanner.feature.results.sections.info

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.WalkingTour
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.sections.WalkingToursSectionContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Card footer with a long link across card widths 240–420 dp and 100/130/200 % font: the Add to plan button is
 * never squeezed next to the link (one-line label there), sits at the end of its line, keeps the minimum gap,
 * and a label that does wrap (button alone on its line) breaks only between words.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1700dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFooterSweepTest {
    @get:Rule val compose = createComposeRule()

    private var width by mutableStateOf(MIN_WIDTH)
    private var fontScale by mutableStateOf(1f)

    private val data =
        WalkingToursData(
            results = listOf(WalkingTour(name = "Asakusa free tour", provider = "Free Tours by Foot", url = "https://freetoursbyfoot.com/tokyo-tours/")),
        )

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(
            hasText(text),
            useUnmergedTree = true,
        ).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Test
    fun `add button is never squeezed next to the link`() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                TravelTheme(themeMode = ThemeMode.LIGHT) { Box(Modifier.width(width.dp)) { WalkingToursSectionContent(data, FakeSectionActions()) } }
            }
        }
        val cases = SCALES.flatMap { scale -> (MIN_WIDTH..MAX_WIDTH step STEP).map { scale to it } }
        cases.forEach { (scale, w) ->
            fontScale = scale
            width = w
            compose.waitForIdle()
            assertFooter("${w}dp @ ${(scale * 100).toInt()}%")
        }
    }

    private fun assertFooter(case: String) {
        val gap = 8f * compose.density.density
        val footer = compose.onNodeWithTag(CARD_FOOTER_TAG).fetchSemanticsNode().boundsInRoot
        val link = compose.onNode(hasText("View on Free Tours by Foot ↗")).fetchSemanticsNode().boundsInRoot
        val add = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).fetchSemanticsNode().boundsInRoot
        val label = textLayout(LABEL)
        assertEquals("$case: end aligned", footer.right, add.right, 2f)
        if (add.top < link.bottom - 1f) {
            // Same line as the link: full-size button, one-line label, minimum gap.
            assertEquals("$case: label squeezed next to the link", 1, label.lineCount)
            assertTrue("$case: gap ${add.left - link.right}", add.left - link.right >= gap - 1f)
        } else {
            val midWord = (0 until label.lineCount - 1).map { label.getLineEnd(it) }.filterNot { LABEL[it - 1] == ' ' || LABEL[it] == ' ' }
            assertTrue("$case: label broken mid-word at $midWord", midWord.isEmpty())
        }
    }

    private companion object {
        const val LABEL = "Add to plan"
        const val MIN_WIDTH = 240
        const val MAX_WIDTH = 420
        const val STEP = 4
        val SCALES = listOf(1f, 1.3f, 2f)
    }
}
