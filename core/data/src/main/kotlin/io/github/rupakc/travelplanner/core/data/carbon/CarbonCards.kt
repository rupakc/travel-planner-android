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
 * While `features.carbon` reads off for the current body, ask again after these waits. A real "off" is cached by
 * the flags repository, so these re-reads only reach the network after a failed `/api/config`.
 */
val CARBON_AVAILABILITY_RETRY_MS: List<Long> = listOf(5_000L, 15_000L, 30_000L, 60_000L)

/**
 * The footprint card's state for a stream of request [bodies] (from `CarbonRequests`; null = nothing to show yet).
 * The remembered diet is written into each body. Rules, as on the web:
 * - Starts with [CarbonCardState.Hidden]; with `features.carbon` off it stays so and no request is ever sent.
 *   The flag is read when the first body arrives. While it reads off (also when `/api/config` failed, which the
 *   flags repository does not cache) it is read again after each of [availabilityRetryMillis] and on every new body,
 *   so a cold start offline or a timed-out config shows the card once the network is back.
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
    availabilityRetryMillis: List<Long> = CARBON_AVAILABILITY_RETRY_MS,
): Flow<CarbonCardState> =
    flow {
        // Hidden first, so a screen combining this never waits for `/api/config`.
        emit(CarbonCardState.Hidden())
        var last: CarbonCardState = CarbonCardState.Hidden()
        var gone = false
        var enabled = false
        emitAll(
            combine(bodies, diet, retries) { body, d, attempt -> Triple(body?.withDiet(d), d, attempt) }
                .distinctUntilChanged()
                .transformLatest { (body, d, _) ->
                    if (gone || body == null) {
                        emit(CarbonCardState.Hidden(d))
                        return@transformLatest
                    }
                    if (!enabled) enabled = awaitAvailable(availabilityRetryMillis)
                    if (!enabled) {
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

/** True once `features.carbon` reads on, asking now and after each of [waits]; false when it still reads off. */
private suspend fun CarbonRepository.awaitAvailable(waits: List<Long>): Boolean {
    if (available()) return true
    for (wait in waits) {
        delay(wait)
        if (available()) return true
    }
    return false
}

private fun JsonObject.withDiet(diet: CarbonDiet): JsonObject = JsonObject(this + ("diet" to JsonPrimitive(diet.wire)))
