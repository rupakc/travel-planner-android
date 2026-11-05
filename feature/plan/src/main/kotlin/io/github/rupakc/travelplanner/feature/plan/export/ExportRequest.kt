package io.github.rupakc.travelplanner.feature.plan.export

import io.github.rupakc.travelplanner.core.domain.export.PlanCalendarEvent
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject

/** Facts for the localised "Share as text" header (formatted on the UI side with string resources). */
internal data class ShareTextInput(
    val planName: String,
    val origin: String?,
    val destination: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val travellers: Int?,
    val selections: PlanSelections,
    val itineraryDays: List<JsonObject>,
    val totalCostUsd: Double,
)

/** Something a plan screen asks the platform to do (share sheet, calendar, file). Emitted once. */
internal sealed interface ExportRequest {
    /** Share an `.ics` file through the share sheet (FileProvider). */
    data class Ics(val fileName: String, val content: String) : ExportRequest

    /** Share the plan as plain text. */
    data class Text(val input: ShareTextInput) : ExportRequest

    /** Share a public link. */
    data class Link(val planName: String, val url: String) : ExportRequest

    /** "Add to calendar" for one event (CalendarContract insert intent). */
    data class CalendarInsert(val event: PlanCalendarEvent) : ExportRequest
}
