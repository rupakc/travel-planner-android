package io.github.rupakc.travelplanner

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.alerts.NotificationBell
import io.github.rupakc.travelplanner.navigation.TopLevelDestination
import io.github.rupakc.travelplanner.navigation.TravelAppScaffold
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * E2E finding O2: ATF measured the middle top-bar action 48 x 32 dp. Every top-bar action must offer a 48 x 48 dp
 * target, none may overlap another, and nothing inside an action (the bell's badge) may reach outside it.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp-xxhdpi")
class TopBarTouchTargetTest {
    @get:Rule val compose = createComposeRule()

    private fun allNodes(): List<SemanticsNode> {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        return walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    @Test
    fun `top bar actions are 48 dp targets that do not overlap, with a badge on the bell`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                TravelAppScaffold(
                    currentTopLevel = TopLevelDestination.HOME,
                    showNavigation = true,
                    onNavigate = {},
                    onOpenSettings = {},
                    bell = { NotificationBell(unread = 12, onOpen = {}) },
                ) { Text("body") }
            }
        }
        val min = with(compose.density) { 48.dp.toPx() } - 0.5f
        val actions =
            listOf("Search everything", "Notifications, 12 unread", "Settings").map { label ->
                compose.onNode(hasContentDescription(label) and hasClickAction()).fetchSemanticsNode()
            }
        actions.forEach { node ->
            val r = node.touchBoundsInRoot
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)
            assertTrue("$label touch target ${r.width} x ${r.height} px", r.width >= min && r.height >= min)
            assertTrue("$label bounds ${node.boundsInRoot} smaller than 48 dp", node.boundsInRoot.width >= min && node.boundsInRoot.height >= min)
        }
        actions.zipWithNext().forEach { (a, b) ->
            assertTrue("actions overlap: ${a.boundsInRoot} / ${b.boundsInRoot}", !a.boundsInRoot.overlapsStrictly(b.boundsInRoot))
        }
        // Nothing drawn inside an action's subtree may reach outside it (it would cover a neighbour for accessibility).
        val actionIds = actions.map { it.id }.toSet()
        allNodes().filter { n -> generateSequence(n.parent) { it.parent }.any { it.id in actionIds } }.forEach { n ->
            val owner = generateSequence(n.parent) { it.parent }.first { it.id in actionIds }
            val o = owner.boundsInRoot
            val b = n.boundsInRoot
            assertTrue(
                "node $b reaches outside its action $o",
                b.left >= o.left - 0.5f && b.top >= o.top - 0.5f && b.right <= o.right + 0.5f && b.bottom <= o.bottom + 0.5f,
            )
        }
    }

    private fun Rect.overlapsStrictly(other: Rect): Boolean =
        left < other.right - 0.5f && other.left < right - 0.5f && top < other.bottom - 0.5f && other.top < bottom - 0.5f
}
