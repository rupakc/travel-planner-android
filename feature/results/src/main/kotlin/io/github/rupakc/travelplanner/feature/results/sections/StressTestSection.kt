package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.StressTestPayloads
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.feature.results.sections.info.HealthBody
import io.github.rupakc.travelplanner.feature.results.sections.info.RecheckFailure
import io.github.rupakc.travelplanner.feature.results.sections.info.RecheckResult
import io.github.rupakc.travelplanner.feature.results.sections.info.RecheckState
import io.github.rupakc.travelplanner.feature.results.sections.info.healthView
import kotlinx.coroutines.launch

private val RecheckSaver = Saver<RecheckState, String>(save = { it.encode() }, restore = { RecheckState.decode(it) })

/**
 * Trip Health Check: score ring, verdict, findings sorted by severity, and — once the itinerary
 * layout was edited — "Re-check my edited plan" (`POST /api/stress-test` with the edited itinerary,
 * web stressTestPayload) with stale/original notes (web StressTestSection).
 */
@Composable
internal fun StressTestSectionContent(data: StressTestData, actions: SectionActions, modifier: Modifier = Modifier) {
    val selections by actions.selections.collectAsStateWithLifecycle()
    val results by actions.results.collectAsStateWithLifecycle()
    val itinerary = results.sections[SectionId.ITINERARY]?.data as? ItineraryData
    var state by rememberSaveable(stateSaver = RecheckSaver) { mutableStateOf(RecheckState()) }
    val scope = rememberCoroutineScope()

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
            if (state.running || itinerary == null || request == null) return@HealthBody
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
            val forFingerprint = StressTestPayloads.recheckFingerprint(itinerary, selections)
            state = state.copy(running = true, failure = null)
            scope.launch {
                state =
                    when (val r = actions.recheckStressTest(payload)) {
                        is AppResult.Success -> {
                            if (r.value.isError) {
                                state.copy(running = false, failure = RecheckFailure(null, forFingerprint))
                            } else {
                                RecheckState(result = RecheckResult(r.value, forFingerprint))
                            }
                        }

                        is AppResult.Failure -> {
                            state.copy(running = false, failure = RecheckFailure(r.error.message, forFingerprint))
                        }
                    }
            }
        },
        modifier = modifier,
    )
}
