package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import java.util.Currency
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Port of frontend/src/utils/priceWatch.js and components/alerts/alertKinds.js.

/** A typed price target (web `checkPriceTarget`): symbols, commas and spaces allowed. */
fun checkPriceTarget(raw: String?): TargetCheck {
    val text = raw.orEmpty()
    val value = text.replace(Regex("[^\\d.-]"), "").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    if (text.isBlank() || value == null) return TargetCheck(null, TargetError.NOT_A_PRICE)
    return TargetCheck(value, null)
}

/** Latest vs starting price in the watch's currency (cents kept; web `priceWatchChange`). */
fun priceWatchChange(watch: PriceWatch): PriceChange? {
    val base = watch.baselinePrice ?: return null
    val last = watch.lastPrice ?: return null
    val delta = (last - base).roundCents()
    return if (kotlin.math.abs(delta) < HALF_CENT) PriceChange(0.0, Direction.SAME) else PriceChange(delta, directionOf(delta))
}

/** "$" / "€" for a code (the code itself when the platform has no symbol for it). */
fun currencySymbol(currency: String?): String {
    val code = currencyCode(currency)
    return runCatching { Currency.getInstance(code).getSymbol(Locale.US) }.getOrDefault(code)
}

/** The ISO code, defaulting to USD. */
fun currencyCode(currency: String?): String = currency?.trim()?.takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT) ?: "USD"

/** Filter tabs of the Alerts page, in order (web `ALERT_TABS`). [kind] matches [watchKind]. */
enum class AlertTab(val kind: String?) {
    ALL(null),
    FLIGHTS(KIND_FLIGHT),
    HOTELS(PriceWatchKinds.HOTEL),
    ACTIVITIES(PriceWatchKinds.ACTIVITY),
    TRIPS(PriceWatchKinds.TRIP),
    ;

    companion object {
        /** Tab for a deep-link `kind` (null/unknown = fare watch → Flights only when a watch id is given). */
        fun forKind(kind: String?): AlertTab? = entries.firstOrNull { it.kind != null && it.kind == kind }
    }
}

/** Kind of a fare watch, as tabs and deep links know it. */
const val KIND_FLIGHT = "flight"

/** One watched price of either API: fare watches and price watches have separate ids. */
sealed interface AnyWatch {
    val id: Long
    val kind: String
    val status: String?

    /** Sort key: departure date for fares, expiry for price watches (web `sortDate`). */
    val sortDate: String

    /** Unique across both APIs (web `watchDomId`). */
    val key: String

    data class Fare(val watch: FareWatch) : AnyWatch {
        override val id get() = watch.id
        override val kind get() = KIND_FLIGHT
        override val status get() = watch.status
        override val sortDate get() = watch.departureDate.orEmpty()
        override val key get() = "watch-${watch.id}"
    }

    data class Price(val watch: PriceWatch) : AnyWatch {
        override val id get() = watch.id
        override val kind get() = watch.kind ?: PriceWatchKinds.HOTEL
        override val status get() = watch.status
        override val sortDate get() = watch.expiresOn.orEmpty()
        override val key get() = "price-watch-${watch.id}"
    }
}

fun inTab(watch: AnyWatch, tab: AlertTab): Boolean = tab == AlertTab.ALL || watch.kind == tab.kind

fun countByTab(watches: List<AnyWatch>): Map<AlertTab, Int> = AlertTab.entries.associateWith { tab -> watches.count { inTab(it, tab) } }

/** Fares and price watches together, sorted like the web (by trip date / expiry). */
fun mergeWatches(fares: List<FareWatch>, prices: List<PriceWatch>): List<AnyWatch> =
    (fares.map { AnyWatch.Fare(it) } + prices.map { AnyWatch.Price(it) }).sortedBy { it.sortDate }

fun isPast(watch: AnyWatch): Boolean = watch.status == WatchStatuses.EXPIRED

/** The latest look found the subject unavailable (sold out, unlisted). */
fun isUnavailable(watch: PriceWatch): Boolean = watch.lastObservation.string("status") == "unavailable"

/** What one "Check now" found. The UI words each [type] (web `priceCheckMessage` / `checkMessage`). */
data class CheckOutcome(val type: CheckType, val tone: Tone, val price: Double? = null, val notificationTitle: String? = null)

enum class CheckType { ALERT, CHECKED_PRICE, CHECKED_NOTHING, UNAVAILABLE, FAILED, ERRORED, EXPIRED, POSTPONED, DELETED, NOT_WATCHED }

private fun outcome(status: String?, notification: AppNotification?, lastPrice: Double?): CheckOutcome =
    when (status) {
        "checked" -> {
            when {
                notification != null -> CheckOutcome(CheckType.ALERT, Tone.GOOD, notificationTitle = notification.title)
                lastPrice != null -> CheckOutcome(CheckType.CHECKED_PRICE, Tone.NEUTRAL, price = lastPrice)
                else -> CheckOutcome(CheckType.CHECKED_NOTHING, Tone.NEUTRAL)
            }
        }

        "unavailable" -> {
            CheckOutcome(CheckType.UNAVAILABLE, Tone.WARN)
        }

        "failed" -> {
            CheckOutcome(CheckType.FAILED, Tone.WARN)
        }

        "errored" -> {
            CheckOutcome(CheckType.ERRORED, Tone.WARN)
        }

        "expired" -> {
            CheckOutcome(CheckType.EXPIRED, Tone.NEUTRAL)
        }

        "postponed" -> {
            CheckOutcome(CheckType.POSTPONED, Tone.WARN)
        }

        "deleted" -> {
            CheckOutcome(CheckType.DELETED, Tone.NEUTRAL)
        }

        else -> {
            CheckOutcome(CheckType.NOT_WATCHED, Tone.NEUTRAL)
        }
    }

fun checkOutcome(res: FareWatch): CheckOutcome = outcome(res.checkStatus, res.notification, res.lastPrice)

fun checkOutcome(res: PriceWatch): CheckOutcome = outcome(res.checkStatus, res.notification, res.lastPrice)

/** Where an alert should open: the Alerts screen focused on one watch (web `notificationLink`). */
data class AlertLink(val kind: String?, val watchId: Long?)

/** Reads `data.links.alerts` ("/alerts?kind=hotel&watch=9"), else `watch_id` / `watch_kind`. */
fun notificationLink(notification: AppNotification): AlertLink {
    val links = notification.data["links"] as? JsonObject
    val web = (links?.get("alerts") as? JsonPrimitive)?.contentOrNull
    return web?.let(::parseAlertsPath) ?: AlertLink(notification.watchKind, notification.watchId)
}

/** Parses a web alerts path (`/alerts?watch=7`, `/alerts?kind=trip&watch=3`). Null for other paths. */
fun parseAlertsPath(path: String?): AlertLink? {
    if (path == null || !path.startsWith("/alerts")) return null
    val query = path.substringAfter('?', "")
    val params =
        query.split('&').mapNotNull {
            val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k to v
        }.toMap()
    return AlertLink(params["kind"]?.takeIf(String::isNotBlank), params["watch"]?.toLongOrNull())
}

/** Icon and action wording for a notification kind (web `ALERT_KINDS` / `alertKindFor`). */
enum class AlertIcon { TREND_DOWN, TARGET, SPARKLE, CALENDAR, HOTEL, TICKET, LUGGAGE, ENDED, WARNING }

enum class AlertAction { SEE_FARE, SEE_DATES, SEE_HOTEL, SEE_ACTIVITY, SEE_TRIP, CHECK_TRIP, VIEW, RETRY }

data class AlertKindStyle(val icon: AlertIcon, val tone: Tone, val action: AlertAction)

private val ALERT_KINDS: Map<String, AlertKindStyle> =
    mapOf(
        "fare_drop" to AlertKindStyle(AlertIcon.TREND_DOWN, Tone.GOOD, AlertAction.SEE_FARE),
        "fare_target" to AlertKindStyle(AlertIcon.TARGET, Tone.GOOD, AlertAction.SEE_FARE),
        "fare_low_level" to AlertKindStyle(AlertIcon.SPARKLE, Tone.INFO, AlertAction.SEE_FARE),
        "fare_cheaper_date" to AlertKindStyle(AlertIcon.CALENDAR, Tone.INFO, AlertAction.SEE_DATES),
        "hotel_drop" to AlertKindStyle(AlertIcon.HOTEL, Tone.GOOD, AlertAction.SEE_HOTEL),
        "hotel_target" to AlertKindStyle(AlertIcon.HOTEL, Tone.GOOD, AlertAction.SEE_HOTEL),
        "activity_drop" to AlertKindStyle(AlertIcon.TICKET, Tone.GOOD, AlertAction.SEE_ACTIVITY),
        "activity_target" to AlertKindStyle(AlertIcon.TICKET, Tone.GOOD, AlertAction.SEE_ACTIVITY),
        "trip_drop" to AlertKindStyle(AlertIcon.LUGGAGE, Tone.GOOD, AlertAction.SEE_TRIP),
        "trip_target" to AlertKindStyle(AlertIcon.LUGGAGE, Tone.GOOD, AlertAction.SEE_TRIP),
        "watch_expired" to AlertKindStyle(AlertIcon.ENDED, Tone.NEUTRAL, AlertAction.VIEW),
        "watch_error" to AlertKindStyle(AlertIcon.WARNING, Tone.WARN, AlertAction.RETRY),
    )

fun alertKindFor(notification: AppNotification): AlertKindStyle {
    val base = ALERT_KINDS[notification.kind] ?: ALERT_KINDS.getValue("watch_expired")
    val shared = notification.kind == "watch_expired" || notification.kind == "watch_error"
    if (!shared) return base
    val expired = notification.kind == "watch_expired"
    return when (notification.watchKind) {
        PriceWatchKinds.HOTEL -> if (expired) base.copy(action = AlertAction.SEE_HOTEL) else base
        PriceWatchKinds.ACTIVITY -> if (expired) base.copy(icon = AlertIcon.TICKET) else base
        PriceWatchKinds.TRIP -> base.copy(action = if (expired) AlertAction.SEE_TRIP else AlertAction.CHECK_TRIP)
        else -> base
    }
}

/** Keeps a price delta such as "(−$70)" on one line when a title wraps (word joiner after the minus). */
fun keepDeltaTogether(text: String?): String? = text?.replace(Regex("([−-])(?=[A-Z]{0,3}[$€£¥₹])"), "$1⁠")

// --- small JSON readers for the loosely-typed watch objects -------------------------------

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

internal fun JsonObject?.string(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject?.double(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

internal fun JsonObject?.int(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

internal fun JsonObject?.bool(key: String): Boolean? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.let {
        when (it) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

private const val HALF_CENT = 0.005
