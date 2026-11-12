package io.github.rupakc.travelplanner.feature.alerts.savings

import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.DigestWatch
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.alerts.SavingsBest
import io.github.rupakc.travelplanner.core.model.alerts.digestData
import io.github.rupakc.travelplanner.core.model.alerts.dropData
import io.github.rupakc.travelplanner.core.model.alerts.gameDelta
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the savings card shows, derived from the server's [Savings] (nothing is recomputed or converted). */
internal data class SavingsView(
    val totalUsd: Double,
    /** Other currencies, amounts above zero only, never converted. */
    val otherCurrencies: List<Pair<String, Double>>,
    val drops: Int,
    val targets: Int,
    val active: Int?,
    val best: SavingsBest?,
    /** 0..100, the server's `progress_pct`. */
    val progressPct: Int,
    /** The next milestone in USD, or null once every milestone is reached. */
    val nextMilestone: Int?,
    val isEmpty: Boolean,
)

internal fun Savings.view(): SavingsView {
    val usd = totalUsd?.takeIf { it > 0 } ?: 0.0
    val others = byCurrency.mapNotNull { (code, v) -> v?.takeIf { it > 0 }?.let { code to it } }.sortedBy { it.first }
    val drops = (dropsCaught ?: 0).coerceAtLeast(0)
    return SavingsView(
        totalUsd = usd,
        otherCurrencies = others,
        drops = drops,
        targets = (targetsHit ?: 0).coerceAtLeast(0),
        active = watchesActive,
        best = best?.takeIf { (it.caught ?: 0.0) > 0 },
        progressPct = (progressPct ?: if (nextMilestone == null) FULL else 0).coerceIn(0, FULL),
        nextMilestone = nextMilestone,
        isEmpty = drops == 0 && usd == 0.0 && others.isEmpty(),
    )
}

/** The drop moment of a drop notification (`data.drop`). */
internal data class DropView(
    /** The drop in [currency] when it is above zero, else null (a target hit can come without one). */
    val amount: Double?,
    /** Whole-number percent, positive. */
    val percent: Int?,
    val to: Double?,
    val currency: String?,
    val targetHit: Boolean,
    /** The notification's own XP total, only when the game is visible (ruling R4). */
    val xp: Int?,
)

/** Null for rows without `data.drop`, for estimates (`observed: false` never counts) and when there is no price. */
internal fun AppNotification.dropView(gameVisible: Boolean): DropView? {
    val drop = dropData() ?: return null
    if (!drop.observed || drop.to == null) return null
    val xp = if (gameVisible) gameDelta()?.xp?.takeIf { it > 0 } else null
    return DropView(
        amount = drop.amount?.takeIf { it > 0 },
        percent = drop.pct?.let { abs(it).roundToInt() }?.takeIf { it > 0 },
        to = drop.to,
        currency = drop.currency,
        targetHit = drop.targetHit,
        xp = xp,
    )
}

/** The weekly digest row set. */
internal data class DigestView(val week: String?, val rows: List<DigestWatch>, val more: Int, val savings: SavingsView?)

internal fun AppNotification.digestView(): DigestView? {
    val digest = digestData() ?: return null
    val rows = digest.watches.filter { !it.title.isNullOrBlank() }
    return DigestView(
        week = digest.week,
        rows = rows.take(DIGEST_ROWS),
        more = (rows.size - DIGEST_ROWS).coerceAtLeast(0),
        savings = digest.savings?.view()?.takeUnless { it.isEmpty },
    )
}

/** The watch kind of a notification as a telemetry token (`flight|hotel|activity|trip`). */
internal fun AppNotification.telemetryKind(): String =
    when (watchKind ?: kind?.substringBefore('_')) {
        "hotel" -> "hotel"
        "activity" -> "activity"
        "trip" -> "trip"
        else -> "flight"
    }

private const val FULL = 100
private const val DIGEST_ROWS = 5
