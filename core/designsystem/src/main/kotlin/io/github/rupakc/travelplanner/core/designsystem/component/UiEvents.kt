package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the user did: a tap on a control, a toggle (with its new state), the first edit of a text field in a focus
 * session (CHANGE, never the text), or a keyboard submit (SUBMIT).
 */
enum class UiEventKind { TAP, TOGGLE, CHANGE, SUBMIT }

/**
 * Receives UI interaction events from design-system controls. The app installs a sink that forwards to
 * telemetry; the default drops everything. Implementations must be cheap and non-blocking.
 */
fun interface UiEventSink {
    fun onUiEvent(kind: UiEventKind, tid: String, on: Boolean?)
}

val LocalUiEventSink: ProvidableCompositionLocal<UiEventSink> = staticCompositionLocalOf { UiEventSink { _, _, _ -> } }

/** Reports to [this] sink; a throwing sink never blocks the caller (telemetry never blocks a user path). */
internal fun UiEventSink.report(kind: UiEventKind, tid: String, on: Boolean? = null) {
    runCatching { onUiEvent(kind, tid, on) }
}
