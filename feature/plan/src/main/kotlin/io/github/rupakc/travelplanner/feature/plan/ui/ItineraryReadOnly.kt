package io.github.rupakc.travelplanner.feature.plan.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule
import io.github.rupakc.travelplanner.core.ui.itinerary.ItineraryDayCard
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.model.PlanItineraryUi

/**
 * "Full Itinerary (N days)" heading + one read-only card per day, as lazy items. Uses the shared core/ui
 * itinerary composables (the same ones Results and the shared-trip viewer render).
 */
internal fun LazyListScope.itineraryItems(itinerary: PlanItineraryUi) {
    val days = itinerary.days
    if (days.isEmpty()) return
    item(key = "itinerary-heading", contentType = "itinerary-heading") {
        val count = pluralStringResource(R.plurals.plan_days, days.size, days.size)
        SectionTitle(stringResource(R.string.plan_full_itinerary, count), Modifier.padding(top = TravelTheme.spacing.lg))
    }
    days.forEachIndexed { index, day ->
        item(key = "day-$index-${day.day.dayNumber}", contentType = "itinerary-day") {
            ItineraryDayCard(
                day = day,
                modifier = Modifier.padding(top = TravelTheme.spacing.sm),
                edits = itinerary.edits,
                notes = itinerary.notes,
                showTransit = ItinerarySchedule.listedInScheduleOrder(day.day),
            )
        }
    }
}
