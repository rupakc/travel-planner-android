package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Where a notification (a push or an inbox row) leads (ruling R8). */
sealed interface NotificationTarget {
    /** The Alerts screen, focused on a watch when [link] names one. */
    data class Alert(val link: AlertLink) : NotificationTarget

    /** The trip's Today tab. */
    data class TripToday(val planId: Long) : NotificationTarget

    /** The ideas feed (`/feed`): a social kind (idea saved, new follower). */
    data object Feed : NotificationTarget

    /** One idea (`/feed/{id}`). */
    data class Idea(val ideaId: Long) : NotificationTarget

    /** One public profile (`/u/{handle}`, lower case). */
    data class Profile(val handle: String) : NotificationTarget

    /** A head-to-head match (`/play/h2h/{id}`): an invite or a result. */
    data class H2h(val matchId: String) : NotificationTarget

    /** The Play tab (`/play`): the daily quiz reminder, or a head-to-head kind without a usable match id. */
    data object Play : NotificationTarget

    /** The notification list: the safe fallback when nothing more specific is known. */
    data object Inbox : NotificationTarget
}

/**
 * The trip-phase kinds of Notifications 2.0. Not every `trip_` prefix: `trip_drop` / `trip_target` are price-watch
 * kinds, and an unknown kind never counts as a trip kind.
 */
val TRIP_PUSH_KINDS: Set<String> = setOf("trip_t30", "trip_t7", "trip_t1", "trip_morning")

fun isTripPushKind(kind: String?): Boolean = kind in TRIP_PUSH_KINDS

/**
 * The category a notification belongs to. An explicit, known `category` wins; an absent one is derived from the kind
 * (older servers sent price alerts only); a present but unknown one is null (a category this app does not know).
 */
fun notificationCategory(category: String?, kind: String?): NotificationCategory? =
    when {
        !category.isNullOrBlank() -> NotificationCategory.from(category)
        kind == "trip_morning" -> NotificationCategory.MORNING_BRIEF
        isTripPushKind(kind) -> NotificationCategory.TRIP_REMINDERS
        kind in SOCIAL_PUSH_KINDS -> NotificationCategory.SOCIAL
        kind in QUIZ_PUSH_KINDS -> NotificationCategory.QUIZ
        else -> NotificationCategory.PRICE_DROPS
    }

private const val MAX_ID_DIGITS = 15
private const val MAX_MATCH_ID = 64
private val H2H_PATH = Regex("^/play/h2h/([A-Za-z0-9_-]{1,$MAX_MATCH_ID})/?$")

/** Social kinds (category `social`); each opens the idea or profile its link names, else the feed. */
val SOCIAL_PUSH_KINDS: Set<String> =
    setOf("social_idea_saved", "social_new_follower", "social_idea_liked", "social_idea_comment")

/** Quiz kinds (category `quiz`): head-to-head invite / result and the daily reminder. */
val QUIZ_PUSH_KINDS: Set<String> = setOf("quiz_h2h_invite", "quiz_h2h_result", "quiz_daily")

/** The match id of a `/play/h2h/{id}` web path and nothing else. */
fun parseH2hPath(path: String?): String? = path?.let { H2H_PATH.matchEntire(it.substringBefore('?')) }?.groupValues?.get(1)

private val IDEA_PATH = Regex("^/feed/(\\d{1,$MAX_ID_DIGITS})/?$")
private val PROFILE_PATH = Regex("^/u/([A-Za-z0-9_]{3,20})/?$")

/** The idea id of a `/feed/{id}` web path and nothing else (allow-list). */
fun parseIdeaPath(path: String?): Long? = path?.let { IDEA_PATH.matchEntire(it.substringBefore('?')) }?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }

/** The (lower-case) handle of a `/u/{handle}` web path and nothing else (allow-list). */
fun parseProfilePath(path: String?): String? = path?.let { PROFILE_PATH.matchEntire(it.substringBefore('?')) }?.groupValues?.get(1)?.lowercase()

private val TRIP_PATH = Regex("^/trips/(\\d{1,$MAX_ID_DIGITS})(/today)?/?$")

/** The trip id of a web trip path (`/trips/12`, `/trips/12/today`) and nothing else (allow-list, never a free-form URL). */
fun parseTripPath(path: String?): Long? = path?.let { TRIP_PATH.matchEntire(it.substringBefore('?')) }?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }

/**
 * Resolves a notification's destination. A `trip_*` phase kind opens its trip's Today tab through [planId] (the id from
 * `plan_id`, a string in pushes), else through a `/trips/{id}` [link]; with neither it opens the list. Everything else
 * keeps the price-alert behaviour (the Alerts screen on its watch), and a notification of another category that names no
 * watch opens the list.
 */
fun resolveNotificationTarget(
    kind: String?,
    link: String?,
    planId: String?,
    watchId: Long?,
    watchKind: String?,
    category: String? = null,
): NotificationTarget {
    if (isTripPushKind(kind)) {
        val id = planId?.trim()?.takeIf { it.length <= MAX_ID_DIGITS }?.toLongOrNull()?.takeIf { it > 0 } ?: parseTripPath(link)
        return id?.let(NotificationTarget::TripToday) ?: NotificationTarget.Inbox
    }
    if (kind in SOCIAL_PUSH_KINDS) return socialTarget(link)
    if (kind in QUIZ_PUSH_KINDS) return quizTarget(kind, link)
    val alert = parseAlertsPath(link) ?: AlertLink(watchKind, watchId)
    val priceLike = notificationCategory(category, kind) == NotificationCategory.PRICE_DROPS
    return if (priceLike || alert.watchId != null || link?.startsWith("/alerts") == true) NotificationTarget.Alert(alert) else NotificationTarget.Inbox
}

/** A quiz row: the match its link names (head-to-head kinds), else the Play tab. */
private fun quizTarget(kind: String?, link: String?): NotificationTarget =
    if (kind == "quiz_daily") NotificationTarget.Play else parseH2hPath(link)?.let(NotificationTarget::H2h) ?: NotificationTarget.Play

/** A social row: the idea or profile its web [link] names (allow-listed), else the feed. */
private fun socialTarget(link: String?): NotificationTarget =
    parseIdeaPath(link)?.let(NotificationTarget::Idea) ?: parseProfilePath(link)?.let(NotificationTarget::Profile) ?: NotificationTarget.Feed

/** [resolveNotificationTarget] for an inbox row. */
fun AppNotification.target(): NotificationTarget {
    val alerts = ((data["links"] as? JsonObject)?.get("alerts") as? JsonPrimitive)?.contentOrNull
    return resolveNotificationTarget(kind, alerts ?: link, planId?.toString(), watchId, watchKind, category)
}
