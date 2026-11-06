package io.github.rupakc.travelplanner.feature.results.sections.info

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
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
 * The walking-tour card's "Add to plan" button at compact widths, 200 % font scale, light/dark and en/es:
 * shown in full, ≥ 48 dp, label on whole words, switch semantics with the selected state.
 * Regression for the card's link + add row, where a long "View on {provider} ↗" squeezed the button.
 */
@RunWith(AndroidJUnit4::class)
// A tall xxhdpi device so ForcedSize only narrows the content (a larger forced size would shrink the density).
@Config(application = Application::class, qualifiers = "w411dp-h1700dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkingTourAddButtonTest {
    @get:Rule val compose = createComposeRule()

    private data class Setup(val width: Int, val fontScale: Float, val dark: Boolean, val locale: String)

    private var setup by mutableStateOf<Setup?>(null)
    private var actions by mutableStateOf(FakeSectionActions())

    private val tour =
        WalkingTour(
            name = "Shibuya & Harajuku Free Walking Tour",
            provider = "Free Tours by Foot",
            url = "https://freetoursbyfoot.com/tokyo-tours/",
            rating = 4.7,
            reviewCount = 980,
            duration = "2h",
        )

    private fun show(s: Setup) {
        val first = setup == null
        setup = s
        actions = FakeSectionActions()
        if (first) {
            compose.setContent {
                val current = setup ?: return@setContent
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.ForcedSize(DpSize(current.width.dp, SCREEN_HEIGHT.dp)) then
                        DeviceConfigurationOverride.FontScale(current.fontScale) then
                        DeviceConfigurationOverride.Locales(LocaleList(current.locale)),
                ) {
                    TravelTheme(themeMode = if (current.dark) ThemeMode.DARK else ThemeMode.LIGHT) { InResultsCard { Content() } }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Content() = WalkingToursSectionContent(WalkingToursData(results = listOf(tour)), actions)

    /** The insets the results list puts around a section body: list gutter, section accent bar and body padding. */
    @Composable
    private fun InResultsCard(content: @Composable () -> Unit) {
        val s = TravelTheme.spacing
        Box(Modifier.padding(horizontal = s.gutter).padding(start = s.accentBar).padding(s.lg)) { content() }
    }

    private fun addButton(): SemanticsNodeInteraction =
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch).and(hasContentDescription(tour.name!!, substring = true)),
        )

    private fun assertRenderedProperly(s: Setup, label: String) {
        val button = addButton()
        button.assertIsDisplayed().assertHeightIsAtLeast(MIN_TOUCH.dp).assertWidthIsAtLeast(MIN_TOUCH.dp)

        // Nothing of the button is cut off by its parent or by the screen edge.
        val node = button.fetchSemanticsNode()
        val clipped = node.boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("$s: button width clipped", node.size.width.toFloat(), clipped.width, 1f)
        assertEquals("$s: button height clipped", node.size.height.toFloat(), clipped.height, 1f)
        assertTrue("$s: button past the screen edge", clipped.right <= root.right + 1f)

        // The label is laid out in full on whole words (not one letter per line, not ellipsised).
        val text = compose.onNode(hasText(label), useUnmergedTree = true)
        text.assertIsDisplayed()
        val layout = textLayout(text)
        val textNode = text.fetchSemanticsNode()
        assertEquals("$s: label width clipped", textNode.size.width.toFloat(), textNode.boundsInRoot.width, 1f)
        assertEquals("$s: label height clipped", textNode.size.height.toFloat(), textNode.boundsInRoot.height, 1f)
        assertTrue("$s: label ellipsised", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
        assertTrue("$s: label wraps onto ${layout.lineCount} lines", layout.lineCount <= MAX_LABEL_LINES)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("$s: label broken mid-word at $end", label[end - 1] == ' ' || label[end] == ' ')
        }
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Test
    fun `add button is whole and readable at compact widths, 200 percent font, light and dark, en and es`() {
        val setups =
            listOf(COMPACT_MIN_WIDTH, PHONE_WIDTH).flatMap { width ->
                listOf(1f, 2f).flatMap { scale -> listOf(false, true).flatMap { dark -> LABELS.keys.map { Setup(width, scale, dark, it) } } }
            }
        setups.forEach { s ->
            val (add, added) = LABELS.getValue(s.locale)
            show(s)
            assertRenderedProperly(s, add)
            addButton().assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)).performClick()
            compose.waitForIdle()
            assertEquals(listOf(tour.url), actions.value.walkingTours.map { it.url })
            addButton().assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            assertRenderedProperly(s, added)
        }
    }

    private companion object {
        const val COMPACT_MIN_WIDTH = 320
        const val PHONE_WIDTH = 360
        const val SCREEN_HEIGHT = 1600
        const val MIN_TOUCH = 48
        const val MAX_LABEL_LINES = 2
        val LABELS = mapOf("en" to ("Add to plan" to "In your plan"), "es" to ("Añadir al plan" to "En tu plan"))
    }
}
