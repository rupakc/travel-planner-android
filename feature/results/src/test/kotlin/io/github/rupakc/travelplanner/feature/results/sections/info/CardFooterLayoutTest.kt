package io.github.rupakc.travelplanner.feature.results.sections.info

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.Tip
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.core.model.section.WalkingTour
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.sections.TipsSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.WalkingToursSectionContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Card footers: link at the start, Add to plan at the end with a minimum gap, also when it wraps; Tips keep the button next to the link. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1700dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFooterLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun show(width: Dp, content: @Composable () -> Unit) {
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { Box(Modifier.width(width)) { content() } } }
        compose.waitForIdle()
    }

    private fun bounds(matcher: SemanticsMatcher, unmerged: Boolean = false): Rect =
        compose.onNode(
            matcher,
            useUnmergedTree = unmerged,
        ).fetchSemanticsNode().boundsInRoot

    private val addButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    private fun footer(): Rect = compose.onNodeWithTag(CARD_FOOTER_TAG).fetchSemanticsNode().boundsInRoot

    private fun px(dp: Dp): Float = dp.value * compose.density.density

    private fun tour(provider: String) =
        WalkingToursData(results = listOf(WalkingTour(name = "Asakusa free tour", provider = provider, url = "https://www.guruwalk.com/tokyo/1")))

    @Test
    fun `one line - button at the end with at least the small gap after the link`() {
        show(411.dp) { WalkingToursSectionContent(tour("GuruWalk"), FakeSectionActions()) }
        val link = bounds(hasText("View on GuruWalk ↗"))
        val add = bounds(addButton)
        assertEquals("same line", link.center.y, add.center.y, 2f)
        assertEquals("end aligned", footer().right, add.right, 2f)
        assertTrue("gap ${add.left - link.right}", add.left - link.right >= px(8.dp) - 1f)
    }

    @Test
    fun `wrapped - button on its own line, end aligned`() {
        show(260.dp) { WalkingToursSectionContent(tour("Free Tours by Foot"), FakeSectionActions()) }
        val link = bounds(hasText("View on Free Tours by Foot ↗"))
        val add = bounds(addButton)
        assertTrue("wrapped below", add.top >= link.bottom - 1f)
        assertEquals("end aligned", footer().right, add.right, 2f)
    }

    @Test
    fun `tips keep Add to plan right after the source link`() {
        val data =
            TipsData(
                tips =
                    listOf(
                        Tip(title = "Carry cash", body = "Many small shops take cash only.", severity = "info", sourceUrl = "https://www.japan.travel/en/"),
                    ),
            )
        show(411.dp) { TipsSectionContent(data, FakeSectionActions()) }
        val link = bounds(hasText("Source", substring = true))
        val add = bounds(addButton)
        assertEquals("same line", link.center.y, add.center.y, 2f)
        assertEquals("button follows the link", px(8.dp), add.left - link.right, 2f)
        assertTrue("not pushed to the end", footer().right - add.right > px(8.dp))
    }
}
