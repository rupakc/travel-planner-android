package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.StressTestPayloads
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.feature.results.sections.info.HealthBody
import io.github.rupakc.travelplanner.feature.results.sections.info.StressRecheckController
import io.github.rupakc.travelplanner.feature.results.sections.info.healthView

/**
 * Trip Health Check: score ring, verdict, findings sorted by severity, and — once the itinerary
 * layout was edited — "Re-check my edited plan" (`POST /api/stress-test` with the edited itinerary,
 * web stressTestPayload) with stale/original notes (web StressTestSection). The re-check lives in
 * [SectionActions.stressRecheck] so it survives scrolling and rotation.
 */
@Composable
internal fun StressTestSectionContent(data: StressTestData, actions: SectionActions, modifier: Modifier = Modifier) {
    val selections by actions.selections.collectAsStateWithLifecycle()
    val results by actions.results.collectAsStateWithLifecycle()
    val itinerary = results.sections[SectionId.ITINERARY]?.data as? ItineraryData
    val scope = rememberCoroutineScope()
    val controller = actions.stressRecheck ?: remember(actions) { StressRecheckController(scope) { actions.recheckStressTest(it) } }
    val state by controller.state.collectAsStateWithLifecycle()

    // A result from another search (different original itinerary) is forgotten, like the web's clearRecheck.
    val base = remember(itinerary) { itinerary?.let(ItineraryLayouts::fingerprint) }
    LaunchedEffect(base, state.base) { if (state.base != null && state.base != base) controller.clear() }

    val layoutMoved = itinerary != null && ItineraryLayouts.layoutChangeCount(itinerary, selections.itineraryLayout) > 0
    val needsFingerprint = state.result != null || state.failure != null
    val fingerprint =
        remember(itinerary, selections.itineraryLayout, selections.itineraryEdits, needsFingerprint) {
            if (itinerary != null && needsFingerprint) StressTestPayloads.recheckFingerprint(itinerary, selections) else null
        }

    HealthBody(
        view = healthView(data, layoutMoved, state, fingerprint),
        rechecking = state.running,
        onRecheck = {
            val request = results.request
            if (itinerary == null || request == null) return@HealthBody
            val sections = results.sections
            val payload =
                StressTestPayloads.build(
                    request = request,
                    itinerary = itinerary,
                    selections = selections,
                    flights = sections[SectionId.FLIGHTS]?.raw,
                    visa = sections[SectionId.VISA]?.raw,
                    weather = sections[SectionId.WEATHER]?.raw,
                )
            controller.start(payload, StressTestPayloads.recheckFingerprint(itinerary, selections), base)
        },
        modifier = modifier,
    )
}
