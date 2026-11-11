package io.github.rupakc.travelplanner.telemetry

import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventKind
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventSink
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents

/** Forwards design-system UI events to telemetry: TAP → `ui_tap`, TOGGLE → `ui_toggle`, on the current route. */
class TelemetryUiBridge(private val telemetry: Telemetry) : UiEventSink {
    override fun onUiEvent(kind: UiEventKind, tid: String, on: Boolean?) {
        val route = telemetry.currentRoute()
        when (kind) {
            UiEventKind.TAP -> telemetry.emit(TelemetryEvents.UI_TAP, mapOf("tid" to tid, "route" to route))
            UiEventKind.TOGGLE -> telemetry.emit(TelemetryEvents.UI_TOGGLE, mapOf("tid" to tid, "route" to route, "on" to (on == true)))
        }
    }
}
