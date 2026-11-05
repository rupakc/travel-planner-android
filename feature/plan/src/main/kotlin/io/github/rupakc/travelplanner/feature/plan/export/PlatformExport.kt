package io.github.rupakc.travelplanner.feature.plan.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import io.github.rupakc.travelplanner.core.domain.export.PlanCalendarEvent
import io.github.rupakc.travelplanner.core.domain.export.PlanShareText
import io.github.rupakc.travelplanner.core.domain.export.PlanTextLabels
import io.github.rupakc.travelplanner.feature.plan.R
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [ExportRequest]s on the platform: share sheet for links/text, an `.ics` file through a
 * FileProvider, and CalendarContract inserts. Returns false when no app can handle the intent.
 */
internal object PlatformExport {
    /** Must match the provider in this module's manifest. */
    fun authority(context: Context): String = "${context.packageName}.plan.exports"

    private const val EXPORT_DIR = "plan-exports"

    suspend fun run(context: Context, request: ExportRequest): Boolean =
        when (request) {
            is ExportRequest.Ics -> shareIcs(context, request)
            is ExportRequest.Text -> shareText(context, request.input.planName, buildText(context, request.input))
            is ExportRequest.Link -> shareText(context, request.planName, linkText(context, request))
            is ExportRequest.CalendarInsert -> start(context, calendarInsert(request.event))
        }

    /** "Name\nURL", plus a note when the link opens only in the app. */
    fun linkText(context: Context, link: ExportRequest.Link): String =
        listOfNotNull(link.planName, link.url, context.getString(R.string.plan_link_app_only).takeIf { link.appOnly }).joinToString("\n")

    private suspend fun shareIcs(context: Context, ics: ExportRequest.Ics): Boolean {
        val uri =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
                val file = File(dir, ics.fileName)
                file.writeText(ics.content, Charsets.UTF_8)
                FileProvider.getUriForFile(context, authority(context), file)
            }
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/calendar")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, Intent.createChooser(send, context.getString(R.string.plan_share_chooser)))
    }

    private fun shareText(context: Context, subject: String, text: String): Boolean {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text)
        return start(context, Intent.createChooser(send, context.getString(R.string.plan_share_chooser)))
    }

    /**
     * Calendar insert for one event. Floating plan times become the phone's local time; all-day
     * events use UTC midnights as CalendarContract requires.
     */
    fun calendarInsert(event: PlanCalendarEvent, zone: ZoneId = ZoneId.systemDefault()): Intent {
        val eventZone = if (event.allDay) ZoneOffset.UTC else zone
        val begin = event.start.atZone(eventZone).toInstant().toEpochMilli()
        val end = (event.end ?: event.start.plusHours(1)).atZone(eventZone).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            .putExtra(CalendarContract.Events.TITLE, event.summary.orEmpty())
            .apply {
                event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                event.description?.takeIf(String::isNotEmpty)?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            }
    }

    private fun start(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    /** The localised plain-text plan (labels from this module's strings). */
    fun buildText(context: Context, input: ShareTextInput): String {
        val r = context.resources
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val dates =
            input.startDate?.let { start ->
                val end = input.endDate
                if (end == null || end == start) date.format(start) else r.getString(R.string.plan_dates_range, date.format(start), date.format(end))
            }
        val route = listOfNotNull(input.destination, input.origin?.let { r.getString(R.string.plan_from, it) }).joinToString(" ")
        val travellers = input.travellers?.let { r.getQuantityString(R.plurals.plan_travellers, it, it) }
        val labels =
            PlanTextLabels(
                flights = r.getString(R.string.plan_cat_flight_legs),
                hotel = r.getString(R.string.plan_cat_hotel),
                activities = r.getString(R.string.plan_cat_activities),
                places = r.getString(R.string.plan_cat_places),
                walkingTours = r.getString(R.string.plan_cat_walking_tours),
                events = r.getString(R.string.plan_cat_events),
                sim = r.getString(R.string.plan_cat_sim),
                gettingAround = r.getString(R.string.plan_cat_getting_around),
                tips = r.getString(R.string.plan_cat_tips),
                itinerary = r.getString(R.string.plan_cat_itinerary),
                day = { r.getString(R.string.plan_day, it) },
                estimatedCost = { r.getString(R.string.plan_text_estimated_cost, it) },
                perNight = { r.getString(R.string.plan_per_night, it) },
                estimate = r.getString(R.string.plan_text_estimate),
            )
        return PlanShareText.build(
            planName = input.planName.ifBlank { r.getString(R.string.plan_trip_unnamed) },
            summaryLines = listOfNotNull(route.ifBlank { null }, dates, travellers),
            selections = input.selections,
            itineraryDays = input.itineraryDays,
            totalCostUsd = input.totalCostUsd,
            labels = labels,
        )
    }
}
