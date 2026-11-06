package io.github.rupakc.travelplanner.core.model.alerts

import io.github.rupakc.travelplanner.core.model.grounding.GroundingSource
import io.github.rupakc.travelplanner.core.model.grounding.GroundingSourceListSerializer
import io.github.rupakc.travelplanner.core.model.grounding.TolerantListSerializer
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import io.github.rupakc.travelplanner.core.model.json.LenientStringList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /api/live-check` body (auth, light_ai): "Search the web now" for one of the caller's
 * watches. [kind] is `flight` (a fare watch) or the price watch's `hotel` / `activity` / `trip`.
 * The server builds the query from the stored watch; the client never sends free text.
 */
@Serializable
data class LiveCheckRequest(
    val kind: String,
    @SerialName("watch_id") val watchId: Long,
)

/**
 * A user-initiated, Google Search–grounded answer. Shown once and kept in ViewModel memory only:
 * never written to Room/DataStore and never mixed into the watch's price history or alerts.
 * [summary] is shown unmodified; each quote cites [sources] by index. Lenient: missing lists are empty.
 */
@Serializable
data class LiveCheckResult(
    val kind: LenientString = null,
    @SerialName("watch_id") val watchId: LenientString = null,
    @SerialName("checked_at") val checkedAt: LenientString = null,
    val summary: LenientString = null,
    @Serializable(with = LiveQuoteListSerializer::class) val quotes: List<LiveQuote> = emptyList(),
    @Serializable(with = GroundingSourceListSerializer::class) val sources: List<GroundingSource> = emptyList(),
    @SerialName("searched_for") val searchedFor: LenientStringList = emptyList(),
    val disclaimer: LenientString = null,
)

/** A price copied from the grounded answer; [sourceIndex] points into [LiveCheckResult.sources]. */
@Serializable
data class LiveQuote(
    val label: LenientString = null,
    val amount: LenientDouble = null,
    val currency: LenientString = null,
    @SerialName("source_index") val sourceIndex: LenientInt = null,
)

object LiveQuoteListSerializer : TolerantListSerializer<LiveQuote>(LiveQuote.serializer())
