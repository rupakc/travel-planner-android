package io.github.rupakc.travelplanner.core.data.carbon

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.CarbonRepository
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonCardState
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Web `CarbonFootprint`: a body change waits this long before the request (typing, quick diet taps). */
const val CARBON_DEBOUNCE_MS = 350L

/**
 * The footprint card's state for a stream of request [bodies] (from `CarbonRequests`; null = nothing to show yet).
 * The remembered diet is written into each body. Rules, as on the web:
 * - Starts with [CarbonCardState.Hidden]; with `features.carbon` off it stays so and no request is ever sent.
 * - A new body or diet shows Loading (or keeps the old numbers, refreshing) and asks again after [debounceMillis];
 *   an older answer never overwrites a newer request.
 * - A 404 hides the card for good (an older backend, or the flag turned off); any other failure is
 *   [CarbonCardState.Failed] until the next body, or until [retries] emits a new value ("Try again").
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun CarbonRepository.cardStates(
    bodies: Flow<JsonObject?>,
    retries: Flow<Int> = flowOf(0),
    debounceMillis: Long = CARBON_DEBOUNCE_MS,
): Flow<CarbonCardState> =
    flow {
        // Hidden first, so a screen combining this never waits for `/api/config`.
        emit(CarbonCardState.Hidden())
        if (!available()) return@flow
        var last: CarbonCardState = CarbonCardState.Hidden()
        var gone = false
        emitAll(
            combine(bodies, diet, retries) { body, d, attempt -> Triple(body?.withDiet(d), d, attempt) }
                .distinctUntilChanged()
                .transformLatest { (body, d, _) ->
                    if (gone || body == null) {
                        emit(CarbonCardState.Hidden(d))
                        return@transformLatest
                    }
                    val previous = last
                    emit(if (previous is CarbonCardState.Ready) previous.copy(diet = d, refreshing = true) else CarbonCardState.Loading(d))
                    delay(debounceMillis)
                    val next =
                        when (val result = footprint(body)) {
                            is AppResult.Success -> {
                                CarbonCardState.Ready(result.value, d)
                            }

                            is AppResult.Failure -> {
                                if (result.error is AppError.NotFound) {
                                    gone = true
                                    CarbonCardState.Hidden(d)
                                } else {
                                    CarbonCardState.Failed(d)
                                }
                            }
                        }
                    emit(next)
                }.onEach { last = it },
        )
    }.distinctUntilChanged()

private fun JsonObject.withDiet(diet: CarbonDiet): JsonObject = JsonObject(this + ("diet" to JsonPrimitive(diet.wire)))
