package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private data class Seen(val kind: UiEventKind, val tid: String, val on: Boolean?)

@RunWith(RobolectricTestRunner::class)
class UiEventsTest {
    @get:Rule val rule = createComposeRule()

    private val seen = mutableListOf<Seen>()
    private val order = mutableListOf<String>()
    private val sink =
        UiEventSink { kind, tid, on ->
            seen += Seen(kind, tid, on)
            order += "sink"
        }

    private fun setContent(sink: UiEventSink = this.sink, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            TravelTheme {
                CompositionLocalProvider(LocalUiEventSink provides sink, content = content)
            }
        }
    }

    @Test fun `button click reports TAP with its tid then calls onClick`() {
        setContent { TpButton("Go", onClick = { order += "click" }, tid = "x.go") }
        rule.onNodeWithText("Go").performClick()
        assertEquals(listOf(Seen(UiEventKind.TAP, "x.go", null)), seen)
        assertEquals(listOf("sink", "click"), order)
    }

    @Test fun `button without tid reports auto button`() {
        setContent { TpButton("Go", onClick = {}) }
        rule.onNodeWithText("Go").performClick()
        assertEquals(listOf(Seen(UiEventKind.TAP, "auto:button", null)), seen)
    }

    @Test fun `loading button reports nothing`() {
        var clicked = false
        setContent { TpButton("Go", onClick = { clicked = true }, loading = true, tid = "x.go") }
        rule.onNodeWithText("Go").performClick()
        assertTrue(seen.isEmpty())
        assertTrue(!clicked)
    }

    @Test fun `throwing sink still calls onClick`() {
        var clicked = false
        setContent(sink = { _, _, _ -> error("boom") }) { TpButton("Go", onClick = { clicked = true }) }
        rule.onNodeWithText("Go").performClick()
        assertTrue(clicked)
    }

    @Test fun `chip reports TOGGLE with on equal to not selected`() {
        setContent {
            Column {
                TpChip("Food", selected = false, onClick = { order += "click" }, tid = "x.food")
                TpChip("Art", selected = true, onClick = {})
            }
        }
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithText("Art").performClick()
        assertEquals(
            listOf(Seen(UiEventKind.TOGGLE, "x.food", true), Seen(UiEventKind.TOGGLE, "auto:chip", false)),
            seen,
        )
        assertEquals(listOf("sink", "click", "sink"), order)
    }
}
