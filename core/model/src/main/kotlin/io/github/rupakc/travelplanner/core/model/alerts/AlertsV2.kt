package io.github.rupakc.travelplanner.core.model.alerts

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Alerts API 2.0 (docs/Alerts-API.md of the web repo, frozen). Every wire value that the server may extend is kept as a
// String with an enum accessor that falls back to Unknown: the server sends codes and numbers, never English text.

/** `kind` of a watch in the v2 routes. `flight` is a fare watch, the others are price watches. */
enum class AlertKind(val wire: String) {
    FLIGHT("flight"),
    HOTEL("hotel"),
    ACTIVITY("activity"),
    TRIP("trip"),
    ;

    companion object {
        /** The kind for [wire], or null when unknown. */
        fun from(wire: String?): AlertKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** `PriceStory.verdict` (null on the wire means no verdict). */
enum class StoryVerdict(val wire: String?) {
    GREAT("great"),
    GOOD("good"),
    TYPICAL("typical"),
    HIGH("high"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps a wire value; null or anything new is [UNKNOWN]. */
        fun from(wire: String?): StoryVerdict = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** `advice.code` of a story, in the server's rule order. [UNKNOWN] for a code a newer server adds. */
enum class AlertsAdviceCode(val wire: String?) {
    PENDING("pending"),
    CHECKS_PAUSED("checks_paused"),
    BELOW_TARGET("below_target"),
    NEAR_TARGET("near_target"),
    AT_LOWEST("at_lowest"),
    BELOW_TYPICAL("below_typical"),
    WITHIN_TYPICAL("within_typical"),
    ABOVE_TYPICAL("above_typical"),
    STEADY("steady"),
    FEW_CHECKS("few_checks"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps a wire value; null or anything new is [UNKNOWN]. */
        fun from(wire: String?): AlertsAdviceCode = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** `suggestions[].reason`. */
enum class SuggestionReason(val wire: String?) {
    SELECTED_IN_PLAN("selected_in_plan"),
    TRIP_TOTAL("trip_total"),
    DEPARTURE_SOON("departure_soon"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps a wire value; null or anything new is [UNKNOWN]. */
        fun from(wire: String?): SuggestionReason = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** `instant` of [AlertPrefs]. */
enum class InstantMode(val wire: String?) {
    ALL("all"),
    TARGETS_ONLY("targets_only"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps a wire value; null or anything new is [UNKNOWN]. */
        fun from(wire: String?): InstantMode = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** Error codes of the v2 routes (the `error_code` of the envelope). */
enum class AlertsErrorCode(val wire: String?) {
    WATCH_NOT_FOUND("watch_not_found"),
    KIND_INVALID("kind_invalid"),
    ADVICE_UNSUPPORTED("advice_unsupported"),
    ADVICE_FAILED("advice_failed"),
    ADVICE_NO_DATA("advice_no_data"),
    AI_LIMIT("ai_limit"),
    AI_BILLING("ai_billing"),
    LIVE_PRICES_OFF("live_prices_off"),
    WATCH_LIMIT("watch_limit"),
    SPEC_INVALID("spec_invalid"),
    TRIP_NOT_FOUND("trip_not_found"),
    ALERT_PREFS_INVALID("alert_prefs_invalid"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps an `error_code`; null or anything new is [UNKNOWN] (show the backend `detail`). */
        fun from(code: String?): AlertsErrorCode = entries.firstOrNull { it.wire != null && it.wire == code } ?: UNKNOWN
    }
}

/** `Money`. */
@Serializable
data class Money(val amount: LenientDouble = null, val currency: String? = null)

/** One observed price of a story. */
@Serializable
data class PricePoint(val at: String? = null, val price: LenientDouble = null)

/** `typical` band; [source] is `google` or `history`. */
@Serializable
data class TypicalBand(val low: LenientDouble = null, val high: LenientDouble = null, val source: String? = null)

/** Params of an advice code: `gap`, `by` (amounts), `days`, `n` (counts); absent ones are null. */
@Serializable
data class AdviceParams(
    val gap: LenientDouble = null,
    val by: LenientDouble = null,
    val days: LenientInt = null,
    val n: LenientInt = null,
)

/** `advice` of a story: a code plus numbers (no text). */
@Serializable
data class StoryAdvice(val code: String? = null, val params: AdviceParams = AdviceParams()) {
    /** The code as an enum; [AlertsAdviceCode.UNKNOWN] for an absent or new one. */
    val adviceCode: AlertsAdviceCode get() = AlertsAdviceCode.from(code)
}

/** A frozen estimate (labelled "estimate"; never a point, a saving or part of the verdict). */
@Serializable
data class StoryEstimate(val price: LenientDouble = null, val source: String? = null)

/** `GET /alerts/watches/{kind}/{id}/story`. */
@Serializable
data class PriceStory(
    val kind: String? = null,
    @SerialName("watch_id") val watchId: Long? = null,
    val currency: String? = null,
    /** any_drop | below_target */
    val mode: String? = null,
    val target: LenientDouble = null,
    val points: List<PricePoint> = emptyList(),
    val baseline: LenientDouble = null,
    val latest: PricePoint? = null,
    val lowest: PricePoint? = null,
    val typical: TypicalBand? = null,
    val verdict: String? = null,
    val advice: StoryAdvice? = null,
    @SerialName("checked_at") val checkedAt: String? = null,
    @SerialName("checked_days_ago") val checkedDaysAgo: LenientInt = null,
    @SerialName("next_check_at") val nextCheckAt: String? = null,
    @SerialName("pending_first_check") val pendingFirstCheck: LenientBoolean = null,
    val estimate: StoryEstimate? = null,
    val caught: LenientDouble = null,
    @SerialName("live_prices") val livePrices: LenientBoolean = null,
) {
    /** The verdict as an enum; [StoryVerdict.UNKNOWN] when absent or new. */
    val verdictKind: StoryVerdict get() = StoryVerdict.from(verdict)

    /** The advice code as an enum; [AlertsAdviceCode.UNKNOWN] when absent or new. */
    val adviceCode: AlertsAdviceCode get() = advice?.adviceCode ?: AlertsAdviceCode.UNKNOWN
}

/** `best` of [Savings]. */
@Serializable
data class SavingsBest(
    val kind: String? = null,
    @SerialName("watch_id") val watchId: Long? = null,
    val title: String? = null,
    val caught: LenientDouble = null,
    val currency: String? = null,
)

/** `GET /alerts/summary`: "caught a drop of $X", never "saved"; nothing is converted between currencies. */
@Serializable
data class Savings(
    @SerialName("total_usd") val totalUsd: LenientDouble = null,
    @SerialName("by_currency") val byCurrency: Map<String, LenientDouble> = emptyMap(),
    @SerialName("drops_caught") val dropsCaught: LenientInt = null,
    @SerialName("targets_hit") val targetsHit: LenientInt = null,
    @SerialName("watches_active") val watchesActive: LenientInt = null,
    val best: SavingsBest? = null,
    val milestones: List<Int> = emptyList(),
    @SerialName("next_milestone") val nextMilestone: LenientInt = null,
    @SerialName("progress_pct") val progressPct: LenientInt = null,
    val since: String? = null,
)

/** `quiet_hours` of [AlertPrefs]; [start] and [end] are `HH:MM`. */
@Serializable
data class QuietHours(val enabled: Boolean = false, val start: String? = null, val end: String? = null)

/** `daily_cap` of [AlertPrefs], 1..10 per category. */
@Serializable
data class DailyCap(
    val flight: LenientInt = null,
    val hotel: LenientInt = null,
    val activity: LenientInt = null,
    val trip: LenientInt = null,
)

/** `GET|PUT /alerts/preferences`. [timezone] and [quietHoursActive] are read-only. */
@Serializable
data class AlertPrefs(
    @SerialName("digest_weekly") val digestWeekly: Boolean = false,
    @SerialName("digest_push") val digestPush: Boolean = false,
    /** ISO weekday, 1 = Monday. */
    @SerialName("digest_day") val digestDay: LenientInt = null,
    /** all | targets_only */
    val instant: String? = null,
    @SerialName("quiet_hours") val quietHours: QuietHours? = null,
    @SerialName("daily_cap") val dailyCap: DailyCap? = null,
    @SerialName("group_window_min") val groupWindowMin: LenientInt = null,
    val timezone: String? = null,
    @SerialName("quiet_hours_active") val quietHoursActive: Boolean = false,
) {
    /** [instant] as an enum. */
    val instantMode: InstantMode get() = InstantMode.from(instant)
}

/** `PUT /alerts/preferences` body: any subset of the writable keys; null fields are omitted. */
@Serializable
data class AlertPrefsUpdate(
    @SerialName("digest_weekly") val digestWeekly: Boolean? = null,
    @SerialName("digest_push") val digestPush: Boolean? = null,
    @SerialName("digest_day") val digestDay: Int? = null,
    val instant: String? = null,
    @SerialName("quiet_hours") val quietHours: QuietHours? = null,
    @SerialName("daily_cap") val dailyCap: DailyCap? = null,
    @SerialName("group_window_min") val groupWindowMin: Int? = null,
)

/** `POST /alerts/quick-watch` body. [surface] is `results | chat | trip | suggest`. */
@Serializable
data class QuickWatchRequest(
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val spec: JsonObject,
    @SerialName("plan_id") val planId: Long? = null,
    val surface: String,
)

/**
 * `201`/`200` of quick-watch. [watch] is a fare watch (`flight`) or a price watch row, kept raw; use [fareWatch] or
 * [priceWatch]. [existing] is true when an active match was returned instead of a new watch.
 */
@Serializable
data class QuickWatchResponse(
    val kind: String? = null,
    val watch: JsonObject = EmptyJsonObject,
    val existing: LenientBoolean = null,
) {
    /** The watch id, when present. */
    val watchId: Long? get() = (watch["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()

    /** The watch as a [FareWatch] (kind `flight`), or null when it does not decode. */
    fun fareWatch(): FareWatch? = runCatching { TravelJson.decodeFromJsonElement(FareWatch.serializer(), watch) }.getOrNull()

    /** The watch as a [PriceWatch] (other kinds), or null when it does not decode. */
    fun priceWatch(): PriceWatch? = runCatching { TravelJson.decodeFromJsonElement(PriceWatch.serializer(), watch) }.getOrNull()
}

/** One of at most five suggestions; [spec] is the raw watch spec to send back to quick-watch. */
@Serializable
data class AlertSuggestion(
    val kind: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val spec: JsonObject = EmptyJsonObject,
    val reason: String? = null,
    @SerialName("already_watching") val alreadyWatching: Boolean = false,
) {
    /** [reason] as an enum. */
    val reasonKind: SuggestionReason get() = SuggestionReason.from(reason)
}

/** `GET /alerts/suggestions`. */
@Serializable
data class SuggestionsResponse(
    val suggestions: List<AlertSuggestion> = emptyList(),
    @SerialName("live_prices") val livePrices: Boolean = false,
)

/** `POST /alerts/watches/flight/{id}/advice`. */
@Serializable
data class AdviceTip(
    val tip: String? = null,
    val source: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    val cached: Boolean = false,
)

/** `data.drop` of a drop notification. */
@Serializable
data class DropData(
    val from: LenientDouble = null,
    val to: LenientDouble = null,
    val amount: LenientDouble = null,
    val pct: LenientDouble = null,
    val currency: String? = null,
    @SerialName("target_hit") val targetHit: Boolean = false,
    val observed: Boolean = true,
)

/** One row of a digest. */
@Serializable
data class DigestWatch(
    val kind: String? = null,
    @SerialName("watch_id") val watchId: Long? = null,
    val title: String? = null,
    val latest: LenientDouble = null,
    @SerialName("change_7d") val change7d: LenientDouble = null,
    val lowest: LenientDouble = null,
    @SerialName("checked_at") val checkedAt: String? = null,
)

/** `data.digest` of a `price_digest` notification. */
@Serializable
data class DigestData(val week: String? = null, val watches: List<DigestWatch> = emptyList(), val savings: Savings? = null)
