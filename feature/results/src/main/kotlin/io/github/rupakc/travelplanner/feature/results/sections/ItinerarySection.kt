package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.feature.results.sections.info.ItineraryBody
import io.github.rupakc.travelplanner.feature.results.sections.info.ItineraryHost

/**
 * Itinerary: days with Morning/Afternoon/Evening slots, derived times, travel between stops, notes,
 * and editing (Edit, Note, Move with swap, Remove/Restore, Undo, Reset, Plan) persisted through
 * My Plan selections exactly as the web stores them (web ItinerarySection).
 */
@Composable
internal fun ItinerarySectionContent(data: ItineraryData, actions: SectionActions, modifier: Modifier = Modifier) {
    val selections by actions.selections.collectAsStateWithLifecycle()
    ItineraryBody(
        data = data,
        host =
            ItineraryHost(
                selections = selections,
                locked = actions.itineraryEditingLocked,
                isInPlan = actions::isSelected,
                onTogglePlan = actions::onToggle,
                onUpdateSelections = actions::onUpdateSelections,
            ),
        modifier = modifier,
    )
}
