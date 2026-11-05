package io.github.rupakc.travelplanner.feature.results.sections.commerce

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.PricingAdvisorData
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.feature.results.BudgetUi
import io.github.rupakc.travelplanner.feature.results.FareCalendarUi
import io.github.rupakc.travelplanner.feature.results.ResultsEvent
import io.github.rupakc.travelplanner.feature.results.WatchesUi

/**
 * What the commerce sections (flights, hotels, activities) need beyond their own
 * data: the search, the pricing advisor, budget-planner fit, weather for the
 * activity chips, watch state and the screen's event sink. Provided once by the
 * results screen so `<Name>SectionContent(data, actions, modifier)` keeps its signature.
 */
@Immutable
data class CommerceContext(
    val request: TravelSearchRequest? = null,
    val pricingAdvisor: PricingAdvisorData? = null,
    /** Days from today to departure (pricing advisor "Now" marker). */
    val daysUntilDeparture: Long = 0,
    val budget: BudgetUi? = null,
    val weather: WeatherData? = null,
    val fareCalendar: FareCalendarUi = FareCalendarUi(),
    val watches: WatchesUi = WatchesUi(),
    val onEvent: (ResultsEvent) -> Unit = {},
)

val LocalCommerceContext = compositionLocalOf { CommerceContext() }
