package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.ALERT_ANY_DROP
import io.github.rupakc.travelplanner.core.model.alerts.ALERT_BELOW_TARGET
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.NoLivePriceError
import io.github.rupakc.travelplanner.core.model.alerts.PriceSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.alerts.TargetCurrencyMismatchError
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// State machines behind the watch dialogs (web WatchDialog.jsx / PriceWatchDialog.jsx /
// HotelDialogBody.jsx). UI-free: core:ui renders the state, the hosting ViewModel supplies
// repository calls as lambdas. One controller per open dialog.

/** A failed action: [message] is the backend's `detail` (shown as-is) or null → the UI's own copy. */
data class DialogError(val message: String?)

/** "Tell me" choice of a watch dialog or edit panel. */
enum class AlertMode(val wire: String) {
    ANY_DROP(ALERT_ANY_DROP),
    BELOW_TARGET(ALERT_BELOW_TARGET),
    ;

    companion object {
        fun of(wire: String?): AlertMode = if (wire == ALERT_BELOW_TARGET) BELOW_TARGET else ANY_DROP
    }
}

/** "Watch this fare" (web `WatchDialog`). [price] / typical range are what the page already shows. */
data class FareWatchDialogState(
    val trip: WatchTrip,
    val price: Double? = null,
    val typicalLow: Double? = null,
    val typicalHigh: Double? = null,
    val mode: AlertMode = AlertMode.ANY_DROP,
    val target: String = "",
    val targetTouched: Boolean = false,
    /** The language [target] was typed in (decides "1.500" vs "1,500"); the pre-filled target is plain digits. */
    val targetLocale: Locale = Locale.ROOT,
    val flexible: Boolean = false,
    val email: Boolean = false,
    val emailAvailable: Boolean = false,
    val saving: Boolean = false,
    val error: DialogError? = null,
    val done: FareWatch? = null,
    /** Airport code → city for the trip's places ("LIS" → "Lisbon"), so the sheet names the cities. */
    val placeNames: Map<String, String> = emptyMap(),
) {
    val targetCheck: TargetCheck get() = if (mode == AlertMode.BELOW_TARGET) checkTarget(target, price, targetLocale) else TargetCheck(null, null)

    /** Shown only once the target was typed in (or submitted). */
    val shownTargetError: TargetError? get() = targetCheck.error.takeIf { targetTouched }
}

class FareWatchDialogController(
    initial: FareWatchDialogState,
    private val create: suspend (io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest) -> AppResult<FareWatch>,
) {
    private val mutable =
        MutableStateFlow(
            initial.copy(target = initial.target.ifEmpty { defaultTarget(initial.typicalLow, initial.price)?.let(::plainNumber).orEmpty() }),
        )
    val state: StateFlow<FareWatchDialogState> = mutable.asStateFlow()

    fun setMode(mode: AlertMode) = mutable.update { it.copy(mode = mode) }

    fun setTarget(text: String, locale: Locale) = mutable.update { it.copy(target = text, targetTouched = true, targetLocale = locale) }

    fun setFlexible(on: Boolean) = mutable.update { it.copy(flexible = on) }

    fun setEmail(on: Boolean) = mutable.update { it.copy(email = on) }

    /** Creates the watch; returns it on success (for the caller's "Watching · $X" chip). */
    suspend fun submit(): FareWatch? {
        val s = mutable.value
        if (s.saving || s.done != null) return null
        if (s.targetCheck.error != null) {
            mutable.update { it.copy(targetTouched = true) }
            return null
        }
        mutable.update { it.copy(saving = true, error = null) }
        val request =
            s.trip.toCreateRequest(
                alertMode = s.mode.wire,
                targetPrice = s.targetCheck.value.takeIf { s.mode == AlertMode.BELOW_TARGET },
                flexible = s.flexible,
                email = s.email && s.emailAvailable,
            )
        return when (val result = create(request)) {
            is AppResult.Success -> result.value.also { w -> mutable.update { it.copy(saving = false, done = w) } }
            is AppResult.Failure -> null.also { mutable.update { it.copy(saving = false, error = DialogError(result.error.message)) } }
        }
    }
}

/** What the page already shows about a hotel / activity / trip before the live look. */
data class PricePreview(
    val title: String? = null,
    val subtitle: String? = null,
    /** A live price the page knows (seeds the target); never an AI estimate. */
    val price: Double? = null,
    val currency: String = "USD",
    /** The AI estimate (labelled as such; never seeds the target). */
    val estimate: Double? = null,
    /** Trip: My Plan total, to explain a different watched total. */
    val planTotal: Double? = null,
    /** Trip: parts as the watch will count them. */
    val parts: List<TripPart> = emptyList(),
)

/** Hotel "Not this one?" swap in the done state (web `HotelDialogBody` Tracked). */
data class SwapState(val open: Boolean = false, val busyIndex: Int? = null, val swapped: Boolean = false, val error: SwapError? = null)

enum class SwapError { KEPT_FIRST, LOST_FIRST }

/**
 * "Watch this price" for a hotel, an activity or a whole trip (web `PriceWatchDialog`).
 * [spec] is sent as-is under [kind]; a picked suggestion replaces [usedSpec].
 */
data class PriceWatchDialogState(
    val kind: String,
    val spec: JsonObject,
    val preview: PricePreview = PricePreview(),
    val usedSpec: JsonObject = spec,
    val mode: AlertMode = AlertMode.ANY_DROP,
    val target: String = "",
    val targetTouched: Boolean = false,
    /** The language [target] was typed in (decides "1.500" vs "1,500"); the pre-filled target is plain digits. */
    val targetLocale: Locale = Locale.ROOT,
    val email: Boolean = false,
    val emailAvailable: Boolean = false,
    val saving: Boolean = false,
    val error: DialogError? = null,
    /** 422 `no_live_price`: message, AI estimate and up to 3 live suggestions. */
    val noMatch: NoLivePriceError? = null,
    /** 422 `target_currency_mismatch`: ask the target again in [TargetCurrencyMismatchError.currency]. */
    val currencyAsk: TargetCurrencyMismatchError? = null,
    val retrySpec: JsonObject? = null,
    val done: PriceWatch? = null,
    val viaSuggestion: Boolean = false,
    val swap: SwapState = SwapState(),
) {
    /** The currency the target is typed in. */
    val currency: String get() = currencyAsk?.currency ?: preview.currency

    val targetCheck: TargetCheck get() = if (mode == AlertMode.BELOW_TARGET) checkPriceTarget(target, targetLocale) else TargetCheck(null, null)

    val shownTargetError: TargetError? get() = targetCheck.error.takeIf { targetTouched }

    /** Hotel alternatives offered once after watching (`last_observation.observed.alternatives`). */
    val alternatives: List<PriceSuggestion>
        get() {
            if (swap.swapped || kind != PriceWatchKinds.HOTEL) return emptyList()
            val list = done?.lastObservation.obj("observed")?.get("alternatives") as? JsonArray ?: return emptyList()
            return runCatching { TravelJson.decodeFromJsonElement(ListSerializer(PriceSuggestion.serializer()), list) }.getOrDefault(emptyList())
        }
}

/** Outcome reported to the caller so it marks its own card as watched only when not [viaSuggestion]. */
data class WatchedOutcome(val watch: PriceWatch, val spec: JsonObject, val viaSuggestion: Boolean)

class PriceWatchDialogController(
    initial: PriceWatchDialogState,
    private val create: suspend (CreatePriceWatchRequest) -> AppResult<PriceWatch>,
    private val delete: suspend (Long) -> AppResult<Unit>,
) {
    private val mutable =
        MutableStateFlow(initial.copy(target = initial.target.ifEmpty { defaultTarget(null, initial.preview.price)?.let(::plainNumber).orEmpty() }))
    val state: StateFlow<PriceWatchDialogState> = mutable.asStateFlow()

    fun setMode(mode: AlertMode) = mutable.update { it.copy(mode = mode) }

    fun setTarget(text: String, locale: Locale) = mutable.update { it.copy(target = text, targetTouched = true, targetLocale = locale) }

    fun setEmail(on: Boolean) = mutable.update { it.copy(email = on) }

    fun toggleAlternatives() = mutable.update { it.copy(swap = it.swap.copy(open = !it.swap.open)) }

    /** "Start watching": the original spec, or the one that hit a currency mismatch. */
    suspend fun submit(): WatchedOutcome? {
        val s = mutable.value
        if (s.noMatch != null) return null
        return createWith(s.retrySpec ?: s.spec)
    }

    /** "Watch this instead" on a no-live-price suggestion. */
    suspend fun pickSuggestion(suggestion: PriceSuggestion): WatchedOutcome? = suggestion.spec?.let { createWith(it) }

    private suspend fun createWith(watchSpec: JsonObject): WatchedOutcome? {
        val s = mutable.value
        if (s.saving || s.done != null) return null
        if (s.targetCheck.error != null) {
            mutable.update { it.copy(targetTouched = true) }
            return null
        }
        mutable.update { it.copy(saving = true, error = null) }
        val below = s.mode == AlertMode.BELOW_TARGET
        val request =
            CreatePriceWatchRequest(
                kind = s.kind,
                spec = watchSpec,
                alertMode = s.mode.wire,
                targetPrice = s.targetCheck.value.takeIf { below },
                targetCurrency = s.currency.takeIf { below },
                emailEnabled = s.email && s.emailAvailable,
            )
        // A cancelled request (the sheet's scope went away) must not leave the sheet "saving" forever.
        val result =
            try {
                create(request)
            } catch (e: CancellationException) {
                mutable.update { it.copy(saving = false) }
                throw e
            }
        return when (result) {
            is AppResult.Success -> {
                val via = watchSpec != s.spec
                mutable.update { it.copy(saving = false, noMatch = null, usedSpec = watchSpec, done = result.value, viaSuggestion = via) }
                WatchedOutcome(result.value, watchSpec, via)
            }

            is AppResult.Failure -> {
                val error = result.error
                val noLive = error.noLivePrice()
                val mismatch = error.currencyMismatch()
                mutable.update {
                    when {
                        noLive != null -> {
                            it.copy(saving = false, noMatch = noLive)
                        }

                        mismatch != null -> {
                            it.copy(
                                saving = false,
                                currencyAsk = mismatch,
                                retrySpec = watchSpec,
                                usedSpec = watchSpec,
                                noMatch = null,
                                target = "",
                                targetTouched = false,
                            )
                        }

                        else -> {
                            it.copy(saving = false, error = DialogError(error.message))
                        }
                    }
                }
                null
            }
        }
    }

    /**
     * "Watch this instead" on a hotel alternative after watching: create the replacement first (the
     * first watch stays if it fails); at the active-watch limit (409) delete first and put the first
     * watch back if the new one then fails. Returns the new watch, if any.
     */
    @Suppress("ReturnCount") // early exits mirror the web flow step by step
    suspend fun swapTo(index: Int): WatchedOutcome? {
        val s = mutable.value
        val current = s.done ?: return null
        val alt = s.alternatives.getOrNull(index)?.spec ?: return null
        if (s.swap.busyIndex != null) return null
        mutable.update { it.copy(swap = it.swap.copy(busyIndex = index, error = null)) }
        val settings = { spec: JsonObject ->
            CreatePriceWatchRequest(
                kind = s.kind,
                spec = spec,
                alertMode = current.alertMode ?: ALERT_ANY_DROP,
                targetPrice = current.targetPrice,
                emailEnabled = current.emailEnabled == true,
            )
        }
        var next = create(settings(alt))
        if (next is AppResult.Failure && next.error.isConflict()) {
            delete(current.id)
            next = create(settings(alt))
            if (next is AppResult.Failure) {
                val firstSpec = specOfHotelWatch(current)
                val back = (create(settings(firstSpec)) as? AppResult.Success)?.value
                mutable.update {
                    it.copy(
                        done = back ?: it.done,
                        usedSpec = if (back != null) firstSpec else it.usedSpec,
                        swap = it.swap.copy(busyIndex = null, error = if (back != null) SwapError.KEPT_FIRST else SwapError.LOST_FIRST),
                    )
                }
                return back?.let { WatchedOutcome(it, firstSpec, viaSuggestion = true) }
            }
        } else if (next is AppResult.Success) {
            delete(current.id)
        }
        return when (next) {
            is AppResult.Success -> {
                mutable.update {
                    it.copy(done = next.value, usedSpec = alt, viaSuggestion = true, swap = SwapState(open = false, swapped = true))
                }
                WatchedOutcome(next.value, alt, viaSuggestion = true)
            }

            is AppResult.Failure -> {
                mutable.update { it.copy(swap = it.swap.copy(busyIndex = null, error = SwapError.KEPT_FIRST)) }
                null
            }
        }
    }
}

/** The hotel spec that watches [watch]'s property again (same stay). */
internal fun specOfHotelWatch(watch: PriceWatch): JsonObject {
    val s = watch.subject
    val keys = listOf("city", "check_in", "check_out", "adults", "rooms", "property_token")
    return JsonObject(
        buildMap {
            put("name", JsonPrimitive(s.string("requested_name") ?: watch.title))
            keys.forEach { k -> s[k]?.let { put(k, it) } }
        },
    )
}

/** 422 `no_live_price` as a typed body (extras hold raw JSON text, see AppError.Client). */
fun AppError.noLivePrice(): NoLivePriceError? {
    if (this !is AppError.Client || code != CODE_NO_LIVE_PRICE) return null

    fun parse(key: String) = extras[key]?.let { runCatching { TravelJson.parseToJsonElement(it) }.getOrNull() }
    val suggestions =
        parse("suggestions")?.let { runCatching { TravelJson.decodeFromJsonElement(ListSerializer(PriceSuggestion.serializer()), it) }.getOrNull() }
    return NoLivePriceError(
        detail = message,
        code = code,
        message = extras["message"] ?: message,
        suggestions = suggestions.orEmpty(),
        estimate = parse("estimate") as? JsonObject,
    )
}

/** 422 `target_currency_mismatch` as a typed body. */
fun AppError.currencyMismatch(): TargetCurrencyMismatchError? {
    if (this !is AppError.Client || code != CODE_CURRENCY_MISMATCH) return null
    return TargetCurrencyMismatchError(
        detail = message,
        code = code,
        message = extras["message"] ?: message,
        currency = extras["currency"],
        price = extras["price"]?.toDoubleOrNull(),
        unitPrice = extras["unit_price"]?.toDoubleOrNull(),
    )
}

/** The AI estimate of a no-live-price answer (`{price, currency?, label?}`), always labelled as an estimate. */
data class EstimateInfo(val price: Double, val currency: String, val label: String?)

fun NoLivePriceError.estimateInfo(): EstimateInfo? {
    val e = estimate ?: return null
    val price = e.double("price") ?: return null
    return EstimateInfo(price, e.string("currency") ?: "USD", (e["label"] as? JsonPrimitive)?.contentOrNull)
}

private fun AppError.isConflict(): Boolean = this is AppError.Client && status == HTTP_CONFLICT

/** "450" / "9.5": a target as typed text. */
internal fun plainNumber(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()

const val CODE_NO_LIVE_PRICE = "no_live_price"
const val CODE_CURRENCY_MISMATCH = "target_currency_mismatch"
private const val HTTP_CONFLICT = 409
