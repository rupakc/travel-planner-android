package io.github.rupakc.travelplanner.telemetry

import io.github.rupakc.travelplanner.core.designsystem.component.UiEventKind
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryUiBridgeTest {
    private val telemetry = FakeTelemetry().apply { screenView("/results") }
    private val bridge = TelemetryUiBridge(telemetry)

    @Test
    fun `tap maps to ui_tap with the current route`() {
        bridge.onUiEvent(UiEventKind.TAP, "nav.home", null)
        assertEquals(listOf(TelemetryEvents.UI_TAP to mapOf("tid" to "nav.home", "route" to "/results")), telemetry.events)
    }

    @Test
    fun `toggle carries on`() {
        bridge.onUiEvent(UiEventKind.TOGGLE, "results.filter", true)
        bridge.onUiEvent(UiEventKind.TOGGLE, "results.filter", false)
        assertEquals(
            listOf(
                TelemetryEvents.UI_TOGGLE to mapOf("tid" to "results.filter", "route" to "/results", "on" to true),
                TelemetryEvents.UI_TOGGLE to mapOf("tid" to "results.filter", "route" to "/results", "on" to false),
            ),
            telemetry.events,
        )
    }
}
