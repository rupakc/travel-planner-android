package io.github.rupakc.travelplanner.feature.alerts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTab
import io.github.rupakc.travelplanner.core.domain.alerts.CheckType
import io.github.rupakc.travelplanner.core.domain.alerts.TimeAgo
import io.github.rupakc.travelplanner.core.domain.alerts.TimeUntil
import io.github.rupakc.travelplanner.core.domain.alerts.TripChange
import io.github.rupakc.travelplanner.core.domain.alerts.TripGroup
import io.github.rupakc.travelplanner.core.domain.alerts.WatchStatusKey
import io.github.rupakc.travelplanner.core.domain.alerts.formatUsd
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import kotlin.math.abs

// Words for the domain's typed values (web copy; strings.xml has en + es).

@Composable
internal fun tabLabel(tab: AlertTab): String =
    stringResource(
        when (tab) {
            AlertTab.ALL -> R.string.alerts_tab_all
            AlertTab.FLIGHTS -> R.string.alerts_tab_flights
            AlertTab.HOTELS -> R.string.alerts_tab_hotels
            AlertTab.ACTIVITIES -> R.string.alerts_tab_activities
            AlertTab.TRIPS -> R.string.alerts_tab_trips
        },
    )

@Composable
internal fun tabEmpty(tab: AlertTab): String =
    stringResource(
        when (tab) {
            AlertTab.ALL -> R.string.alerts_tab_empty_all
            AlertTab.FLIGHTS -> R.string.alerts_tab_empty_flights
            AlertTab.HOTELS -> R.string.alerts_tab_empty_hotels
            AlertTab.ACTIVITIES -> R.string.alerts_tab_empty_activities
            AlertTab.TRIPS -> R.string.alerts_tab_empty_trips
        },
    )

/** "Showing 1 hotel watch" (web `filterStatus`) for the polite live region. */
@Composable
internal fun filterStatus(tab: AlertTab, n: Int): String =
    pluralStringResource(
        when (tab) {
            AlertTab.ALL -> R.plurals.alerts_showing_all
            AlertTab.FLIGHTS -> R.plurals.alerts_showing_flights
            AlertTab.HOTELS -> R.plurals.alerts_showing_hotels
            AlertTab.ACTIVITIES -> R.plurals.alerts_showing_activities
            AlertTab.TRIPS -> R.plurals.alerts_showing_trips
        },
        n,
        n,
    )

@Composable
internal fun statusLabel(key: WatchStatusKey): String =
    stringResource(
        when (key) {
            WatchStatusKey.PENDING -> R.string.alerts_status_pending
            WatchStatusKey.WATCHING -> R.string.alerts_status_watching
            WatchStatusKey.DROPPED -> R.string.alerts_status_dropped
            WatchStatusKey.PAUSED -> R.string.alerts_status_paused
            WatchStatusKey.EXPIRED -> R.string.alerts_status_expired
            WatchStatusKey.PROBLEM -> R.string.alerts_status_problem
        },
    )

@Composable
internal fun timeAgoText(t: TimeAgo): String =
    when (t) {
        TimeAgo.JustNow -> stringResource(R.string.alerts_time_just_now)
        is TimeAgo.Minutes -> pluralStringResource(R.plurals.alerts_time_minutes_ago, t.count, t.count)
        is TimeAgo.Hours -> pluralStringResource(R.plurals.alerts_time_hours_ago, t.count, t.count)
        TimeAgo.Yesterday -> stringResource(R.string.alerts_time_yesterday)
        is TimeAgo.Days -> pluralStringResource(R.plurals.alerts_time_days_ago, t.count, t.count)
    }

@Composable
internal fun timeUntilText(t: TimeUntil): String =
    when (t) {
        TimeUntil.Soon -> stringResource(R.string.alerts_time_soon)
        is TimeUntil.Minutes -> pluralStringResource(R.plurals.alerts_time_in_minutes, t.count, t.count)
        is TimeUntil.Hours -> pluralStringResource(R.plurals.alerts_time_in_hours, t.count, t.count)
        is TimeUntil.Days -> pluralStringResource(R.plurals.alerts_time_in_days, t.count, t.count)
    }

/** What a "Check now" found (web `checkMessage` for fares, `priceCheckMessage` for prices). */
@Composable
internal fun messageText(message: CardMessage): String =
    when (message) {
        is CardMessage.Checked -> {
            checkedText(message)
        }

        is CardMessage.Failed -> {
            message.detail ?: stringResource(
                when (message.action) {
                    CardAction.CHECK -> R.string.alerts_failed_check
                    CardAction.SAVE, CardAction.STATUS -> R.string.alerts_failed_save
                    CardAction.DELETE -> R.string.alerts_failed_delete
                },
            )
        }

        is CardMessage.Paused -> {
            stringResource(if (message.fare) R.string.alerts_paused_fare else R.string.alerts_paused_price)
        }

        CardMessage.Resumed -> {
            stringResource(R.string.alerts_resumed)
        }

        CardMessage.Saved -> {
            stringResource(R.string.alerts_saved)
        }
    }

@Composable
internal fun groupLabel(group: TripGroup): String =
    stringResource(
        when (group) {
            TripGroup.FLIGHTS -> R.string.alerts_group_flights
            TripGroup.STAY -> R.string.alerts_group_stay
            TripGroup.ACTIVITIES -> R.string.alerts_group_activities
            TripGroup.OTHER -> R.string.alerts_group_other
        },
    )

/** "Last check: flights −$70, hotel −$42." + parts it couldn't price (web `whatChanged`). */
@Composable
internal fun tripChangeText(change: TripChange): String {
    val moves =
        change.moves.map { m ->
            val word =
                stringResource(
                    when (m.group) {
                        TripGroup.FLIGHTS -> R.string.alerts_move_flights
                        TripGroup.STAY -> R.string.alerts_move_hotel
                        TripGroup.ACTIVITIES -> R.string.alerts_move_activities
                        TripGroup.OTHER -> R.string.alerts_move_other
                    },
                )
            "$word ${if (m.delta < 0) "−" else "+"}${formatPrice(abs(m.delta))}"
        }
    var text = if (moves.isEmpty()) stringResource(R.string.alerts_trip_no_move) else stringResource(R.string.alerts_trip_moves, moves.joinToString(", "))
    if (change.missing.isNotEmpty()) text += " " + stringResource(R.string.alerts_trip_missing, change.missing.joinToString(", "))
    return text
}

/** Fare / price wording per check result (web `checkMessage` / `priceCheckMessage`). */
private val CHECK_TEXT: Map<CheckType, Pair<Int, Int>> =
    mapOf(
        CheckType.CHECKED_NOTHING to (R.string.alerts_check_no_fares to R.string.alerts_check_done),
        CheckType.UNAVAILABLE to (R.string.alerts_check_unavailable to R.string.alerts_check_unavailable),
        CheckType.FAILED to (R.string.alerts_check_failed_fare to R.string.alerts_check_failed_price),
        CheckType.ERRORED to (R.string.alerts_check_errored_fare to R.string.alerts_check_errored_price),
        CheckType.EXPIRED to (R.string.alerts_check_expired_fare to R.string.alerts_check_expired_price),
        CheckType.POSTPONED to (R.string.alerts_check_postponed_fare to R.string.alerts_check_postponed_price),
        CheckType.DELETED to (R.string.alerts_check_deleted to R.string.alerts_check_deleted),
        CheckType.NOT_WATCHED to (R.string.alerts_check_not_watched_fare to R.string.alerts_check_not_watched_price),
    )

@Composable
private fun checkedText(message: CardMessage.Checked): String {
    val o = message.outcome
    return when (o.type) {
        CheckType.ALERT -> {
            o.notificationTitle.orEmpty()
        }

        CheckType.CHECKED_PRICE -> {
            val price = if (message.fare) formatUsd(o.price ?: 0.0) else formatPrice(o.price, message.currency)
            stringResource(R.string.alerts_check_price, price)
        }

        else -> {
            CHECK_TEXT.getValue(o.type).let { (fare, price) -> stringResource(if (message.fare) fare else price) }
        }
    }
}
