package io.github.rupakc.travelplanner.core.domain.telemetry

/** One queued event. A plain class: identity equality, like the web's queue entries (two identical taps are two events). */
class QueuedEvent(
    val event: String,
    val ts: Long,
    val props: Map<String, Any>,
    val screen: String,
    val sessionId: String,
)

/** What happened to a send attempt, and what the flusher should do next. */
sealed interface SendOutcome {
    /** Accepted by the server. */
    data object Sent : SendOutcome

    /** Rejected for good (4xx other than 408/429): discard the batch. */
    data object Dropped : SendOutcome

    /** Transient failure: keep the batch and back off. */
    data object Retry : SendOutcome

    /** Transient failure with a server-specified wait of [ms] milliseconds. */
    data class RetryAfter(val ms: Long) : SendOutcome

    /** Telemetry is off for this server (404): stop sending. */
    data object Disabled : SendOutcome
}

/** Flush cadence from the server config, already clamped. */
data class TelemetrySettings(val flushIntervalMs: Long, val maxBatch: Int) {
    companion object
}

/** Event names; the only ones the server's registry accepts. */
object TelemetryEvents {
    const val APP_OPEN = "app_open"
    const val APP_BACKGROUND = "app_background"
    const val SESSION_START = "session_start"
    const val SCREEN_VIEW = "screen_view"
    const val SCREEN_TIME = "screen_time"
    const val UI_TAP = "ui_tap"
    const val UI_TOGGLE = "ui_toggle"
    const val THEME_CHANGE = "theme_change"
    const val LANGUAGE_CHANGE = "language_change"
    const val CONSENT = "consent"
    const val PERF = "perf"

    // Phase 1 (accounts, game) and Phase 2 (journey): mirror of the server registry (backend telemetry/registry.py).
    const val AUTH_START = "auth_start"
    const val AUTH_RESULT = "auth_result"
    const val SIGNUP_STEP = "signup_step"
    const val EMAIL_VERIFY = "email_verify"
    const val PASSWORD_RESET = "password_reset"
    const val ACCOUNT_ACTION = "account_action"
    const val QUOTA_BLOCK = "quota_block"
    const val GAME_AWARD = "game_award"
    const val BADGE_EARNED = "badge_earned"
    const val LEVEL_UP = "level_up"
    const val GAME_VIEW = "game_view"
    const val TRIP_VIEW = "trip_view"
    const val TRIP_ACTION = "trip_action"
    const val PREP_TOGGLE = "prep_toggle"
    const val PREP_COMPLETE = "prep_complete"
    const val TODAY_ACTION = "today_action"
    const val OFFLINE_PACK = "offline_pack"
    const val OUTBOX_FLUSH = "outbox_flush"
    const val EXPENSE_ACTION = "expense_action"
    const val JOURNAL_ACTION = "journal_action"
    const val PLACE_ACTION = "place_action"
    const val RECAP_ACTION = "recap_action"
    const val COMPLETENESS_VIEW = "completeness_view"
    const val ONBOARDING_STEP = "onboarding_step"
    const val ONBOARDING_RESULT = "onboarding_result"
    const val ALERT_STORY_VIEW = "alert_story_view"
    const val ALERT_ADVICE = "alert_advice"
    const val NOTIF_SETTINGS_VIEW = "notif_settings_view"
    const val NOTIF_CATEGORY_TOGGLE = "notif_category_toggle"
    const val NOTIF_QUIET_HOURS_CHANGE = "notif_quiet_hours_change"
    const val NOTIF_CAP_CHANGE = "notif_cap_change"
    const val NOTIF_OPEN = "notif_open"
    const val ALERT_SAVINGS_SHARE = "alert_savings_share"
    const val SHARE_OPEN = "share_open"
    const val SHARE_CREATE = "share_create"
    const val SHARE_ACTION = "share_action"
    const val SHARE_REVOKE = "share_revoke"
    const val SHARE_VIEW = "share_view"
}

/**
 * Closed enum values of the Phase 1/2 events (server registry): a value outside its set is dropped by the
 * server, so callers pick from these. Props never carry amounts, journal text, place names, titles, emails
 * or usernames.
 */
object TelemetryEnums {
    val AUTH_METHOD = setOf("password", "apple", "google", "facebook", "github")
    val AUTH_MODE = setOf("signin", "signup", "link")
    val AUTH_RESULT = setOf("ok", "error", "cancelled", "coming_soon", "needs_username", "pending")
    val SIGNUP_STEP = setOf("form", "check_email", "username", "done")
    val EMAIL_VERIFY_RESULT = setOf("ok", "expired", "invalid", "used", "resent", "email_changed")
    val PASSWORD_RESET_STEP = setOf("request", "confirm")
    val PASSWORD_RESET_RESULT = setOf("ok", "error", "expired", "invalid", "used")
    val ACCOUNT_ACTION =
        setOf(
            "avatar_upload",
            "avatar_remove",
            "name_change",
            "settings_change",
            "export",
            "delete",
            "link",
            "unlink",
            "signout_all",
            "email_verify_request",
            "change_password",
        )
    val QUOTA_BUCKET = setOf("search", "chat", "compare", "discover", "light_ai", "import")
    val QUOTA_REASON = setOf("daily_quota", "unverified")
    val GAME_VIEW_SURFACE = setOf("account", "nav", "celebration")
    val TRIP_VIEW_SURFACE = setOf("hub", "overview", "prep", "today", "money", "journal", "recap", "offline")
    val TRIP_PHASE = setOf("dreaming", "planning", "ready", "on_the_road", "memories", "none")
    val TRIP_ACTION =
        setOf("create", "rename", "dates", "phase_override", "phase_clear", "delete", "open_planner", "plan_it", "share_status")
    val PREP_ITEM =
        setOf(
            "visa",
            "passport",
            "bookings",
            "insurance",
            "documents",
            "stress_test",
            "sim",
            "money",
            "packing",
            "emergency_card",
            "checkin",
            "offline_pack",
        )
    val TODAY_ACTION = setOf("checkin", "next_up", "directions", "visited", "status_share", "nearby")
    val OFFLINE_PACK_ACTION = setOf("save", "refresh", "open", "remove")
    val OFFLINE_PACK_RESULT = setOf("ok", "error", "unsupported", "quota")
    val EXPENSE_ACTION = setOf("add", "edit", "delete")
    val EXPENSE_CATEGORY = setOf("flights", "lodging", "food", "transport", "activities", "shopping", "fees", "other")
    val JOURNAL_ACTION = setOf("add", "edit", "delete")
    val PLACE_ACTION = setOf("visited", "unvisited", "rate", "unrate")
    val PLACE_KIND = setOf("activity", "place", "food", "hotel", "event", "tour", "nearby", "other")
    val RECAP_ACTION = setOf("view", "complete", "share", "where_next")
    val ALERT_SAVINGS_SHARE_TARGET = setOf("native", "download", "copy")
    val SHARE_KIND = setOf("plan", "recap", "savings")
    val SHARE_DATES = setOf("hidden", "month", "exact")
    val SHARE_TARGET = setOf("native", "copy", "download", "x", "facebook", "whatsapp", "linkedin")
    val COMPLETENESS_SURFACE = setOf("drawer", "trip")
    val NOTIF_CATEGORY = setOf("price_drops", "trip_reminders", "morning_brief", "weather", "nearby", "social", "quiz")
    val NOTIF_SOURCE = setOf("push", "inapp")
    val ONBOARDING_STEP_ACTION = setOf("view", "next", "skip", "done")
    val ONBOARDING_TYPE =
        setOf("budget_backpacker", "family", "luxury", "adventure", "culture_foodie", "business", "relaxation", "accessible")
    val ALERT_KIND = setOf("flight", "hotel", "activity", "trip")
    val ALERT_VERDICT = setOf("great", "good", "typical", "high", "none")
    val ALERT_ADVICE_OUTCOME = setOf("ok", "cached", "error")
}
