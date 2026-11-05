package io.github.rupakc.travelplanner.feature.results.sections.info

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** A re-check result and the edited itinerary it ran against (`StressTestPayloads.recheckFingerprint`). */
@Serializable
data class RecheckResult(val data: StressTestData, val fingerprint: String)

/** A failed re-check: the server's message (null → generic copy) for the plan it ran against. */
@Serializable
data class RecheckFailure(val message: String?, val fingerprint: String)

/**
 * "Re-check my edited plan" state (web ResultsPage `recheck` / `recheckRun`). [base] is the
 * original itinerary's layout fingerprint, so a result from another search is never shown.
 */
@Serializable
data class RecheckState(
    val result: RecheckResult? = null,
    val failure: RecheckFailure? = null,
    val running: Boolean = false,
    val base: String? = null,
) {
    /** Text form for saved state (an in-flight run is not restored as running). */
    fun encode(): String = TravelJson.encodeToString(serializer(), copy(running = false))

    companion object {
        fun decode(text: String): RecheckState = runCatching { TravelJson.decodeFromString(serializer(), text) }.getOrDefault(RecheckState())
    }
}

/**
 * Owns the Trip Health Check re-check outside the section composable so a running
 * `POST /api/stress-test` (up to ~90 s) survives scrolling the card away and rotation.
 * Create it in the Results ViewModel with `viewModelScope` and `searchRepository::stressTest`,
 * expose it as `SectionActions.stressRecheck`, and [clear] it on a new search, a plan load and
 * "clear selections" (web `clearRecheck`).
 */
class StressRecheckController(
    private val scope: CoroutineScope,
    private val recheck: suspend (StressTestPayload) -> AppResult<StressTestData>,
) {
    private val mutableState = MutableStateFlow(RecheckState())
    private var job: Job? = null

    val state: StateFlow<RecheckState> = mutableState.asStateFlow()

    /**
     * Starts a re-check for the edited plan identified by [fingerprint] (ignored while one runs).
     * A result carrying an `error` is shown as a failure, like the web.
     */
    fun start(payload: StressTestPayload, fingerprint: String, base: String?) {
        if (mutableState.value.running) return
        mutableState.update { it.copy(running = true, failure = null, base = base) }
        job =
            scope.launch {
                val outcome = recheck(payload)
                mutableState.update { current ->
                    when (outcome) {
                        is AppResult.Success -> {
                            if (outcome.value.isError) {
                                current.copy(running = false, failure = RecheckFailure(null, fingerprint))
                            } else {
                                RecheckState(result = RecheckResult(outcome.value, fingerprint), base = base)
                            }
                        }

                        is AppResult.Failure -> {
                            current.copy(running = false, failure = RecheckFailure(outcome.error.message, fingerprint))
                        }
                    }
                }
            }
    }

    /** Forgets any result or error and cancels a running re-check. */
    fun clear() {
        job?.cancel()
        job = null
        mutableState.value = RecheckState()
    }

    /** Restores saved state (see [RecheckState.encode]); a running re-check wins. */
    fun restore(saved: RecheckState) {
        if (!mutableState.value.running && mutableState.value == RecheckState()) mutableState.value = saved.copy(running = false)
    }
}
