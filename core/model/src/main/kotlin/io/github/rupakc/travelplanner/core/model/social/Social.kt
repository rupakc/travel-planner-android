package io.github.rupakc.travelplanner.core.model.social

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Profile `visibility`. A value this app does not know is [UNKNOWN]: treat it as [PRIVATE] (the safe reading). */
enum class ProfileVisibility(val wire: String?) {
    PRIVATE("private"),
    FOLLOWERS("followers"),
    PUBLIC("public"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): ProfileVisibility = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** `PUT /social/follow/{handle}` answer. */
enum class FollowState(val wire: String?) {
    FOLLOWING("following"),
    PENDING("pending"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): FollowState = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** Why a handle is not available (`GET /social/handles/check`). */
enum class HandleReason(val wire: String?) {
    INVALID("invalid"),
    RESERVED("reserved"),
    TAKEN("taken"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): HandleReason = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** Idea `kind`. */
enum class IdeaKind(val wire: String?) {
    PLAN("plan"),
    RECAP("recap"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): IdeaKind = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** Idea or comment `status`: `held` is visible only to its author and admins (show a "waiting for review" notice). */
enum class ContentStatus(val wire: String?) {
    PUBLISHED("published"),
    HELD("held"),
    HIDDEN("hidden"),
    REMOVED("removed"),
    UNKNOWN(null),
    ;

    companion object {
        /** A missing status is [PUBLISHED] (older rows); a new value is [UNKNOWN]. */
        fun from(wire: String?): ContentStatus = if (wire == null) PUBLISHED else entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/** Feed scope. */
enum class FeedScope(val wire: String) {
    FOR_YOU("for_you"),
    FOLLOWING("following"),
    NEW("new"),
}

/** `POST /social/reports` `target`. */
enum class ReportTarget(val wire: String) {
    IDEA("idea"),
    COMMENT("comment"),
    PROFILE("profile"),
}

/** `POST /social/reports` `reason`. */
enum class ReportReason(val wire: String) {
    SPAM("spam"),
    ABUSE("abuse"),
    SEXUAL("sexual"),
    VIOLENCE("violence"),
    MISINFORMATION("misinformation"),
    OTHER("other"),
}

/** `GET /social/profile`: the caller's own profile. `handle == null` means no profile yet (the other fields are the defaults). */
@Serializable
data class OwnProfile(
    val handle: String? = null,
    val visibility: String = ProfileVisibility.PRIVATE.wire.orEmpty(),
    val bio: String? = null,
    @SerialName("show_avatar") val showAvatar: Boolean = false,
    @SerialName("show_home_country") val showHomeCountry: Boolean = false,
    @SerialName("show_countries") val showCountries: Boolean = false,
    @SerialName("show_level") val showLevel: Boolean = false,
    @SerialName("show_badges") val showBadges: Boolean = false,
    @SerialName("leaderboard_opt_in") val leaderboardOptIn: Boolean = false,
    @SerialName("home_country") val homeCountry: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val hasProfile: Boolean get() = !handle.isNullOrEmpty()
    val visibilityMode: ProfileVisibility get() = ProfileVisibility.from(visibility)
}

/** `PUT /social/profile` body: only the fields set are sent (null is omitted). [handle] is required the first time. */
@Serializable
data class ProfileUpdate(
    val handle: String? = null,
    val visibility: String? = null,
    val bio: String? = null,
    @SerialName("show_avatar") val showAvatar: Boolean? = null,
    @SerialName("show_home_country") val showHomeCountry: Boolean? = null,
    @SerialName("show_countries") val showCountries: Boolean? = null,
    @SerialName("show_level") val showLevel: Boolean? = null,
    @SerialName("show_badges") val showBadges: Boolean? = null,
    @SerialName("leaderboard_opt_in") val leaderboardOptIn: Boolean? = null,
    @SerialName("home_country") val homeCountry: String? = null,
)

/** A badge on a public profile. [tier] may be a number or a word. */
@Serializable
data class ProfileBadge(val id: String, val tier: LenientString = null)

/** The caller's relation to a profile. `blocked` is always false in practice (a blocked pair gets `404`). */
@Serializable
data class ProfileRelation(
    val following: Boolean = false,
    val pending: Boolean = false,
    @SerialName("follows_you") val followsYou: Boolean = false,
    val blocked: Boolean = false,
)

/**
 * `GET /social/profile/{handle}`. Every optional field is null when the owner did not opt in (null, not empty: show nothing).
 * A `followers` profile shows only [handle] and the counts to non-followers.
 */
@Serializable
data class PublicProfile(
    val handle: String,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("home_country") val homeCountry: String? = null,
    val countries: List<String>? = null,
    val level: LenientInt = null,
    val badges: List<ProfileBadge>? = null,
    @SerialName("ideas_count") val ideasCount: LenientInt = null,
    @SerialName("followers_count") val followersCount: LenientInt = null,
    @SerialName("following_count") val followingCount: LenientInt = null,
    val you: ProfileRelation = ProfileRelation(),
)

/** `GET /social/handles/check`. */
@Serializable
data class HandleCheck(val available: Boolean = false, val reason: String? = null) {
    val reasonCode: HandleReason? get() = reason?.let(HandleReason::from)
}

/** `PUT /social/follow/{handle}` answer. */
@Serializable
data class FollowResult(val state: String? = null) {
    val followState: FollowState get() = FollowState.from(state)
}

/** One pending follow request. */
@Serializable
data class FollowRequest(
    val handle: String,
    @SerialName("created_at") val createdAt: String? = null,
)

/** `GET /social/follow-requests`. */
@Serializable
data class FollowRequests(val requests: List<FollowRequest> = emptyList())

/** A user as the follow and block lists show them: the handle only. */
@Serializable
data class HandleRef(val handle: String)

/** `GET /social/following|followers` (cursor paged; the cursor is opaque). */
@Serializable
data class HandlePage(val users: List<HandleRef> = emptyList(), val next: String? = null)

/** `GET /social/blocks`. */
@Serializable
data class BlockList(val users: List<HandleRef> = emptyList())

/** An idea's author: the public handle and, when opted in, the avatar. */
@Serializable
data class IdeaAuthor(
    val handle: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** The names the snapshot lists under one section key. */
@Serializable
data class SnapshotItem(val name: String = "")

/**
 * What an idea shows of its plan: counts, destinations, days and selected item names. No dates, origin, prices or names of
 * people. [stats] is kept raw (its keys are the share-card stats and may grow).
 */
@Serializable
data class IdeaSnapshot(
    val v: LenientInt = null,
    val kind: String? = null,
    val destinations: List<String> = emptyList(),
    val days: LenientInt = null,
    val interests: List<String> = emptyList(),
    val stats: JsonObject = JsonObject(emptyMap()),
    val items: Map<String, List<SnapshotItem>> = emptyMap(),
)

/** An idea of the feed. Text fields are plain text: render them with `Text` only. */
@Serializable
data class Idea(
    val id: Long,
    val kind: String? = null,
    val title: String = "",
    val caption: String = "",
    val author: IdeaAuthor = IdeaAuthor(""),
    val snapshot: IdeaSnapshot = IdeaSnapshot(),
    val likes: LenientInt = null,
    val saves: LenientInt = null,
    val comments: LenientInt = null,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val mine: Boolean = false,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val ideaKind: IdeaKind get() = IdeaKind.from(kind)
    val contentStatus: ContentStatus get() = ContentStatus.from(status)
}

/** `GET /social/feed`, `GET /social/profile/{handle}/ideas`. */
@Serializable
data class IdeaPage(val ideas: List<Idea> = emptyList(), val next: String? = null)

/** `POST /social/ideas` body. Idempotent per (author, plan, kind): it updates the idea. */
@Serializable
data class PublishIdeaRequest(
    @SerialName("plan_id") val planId: Long,
    val kind: String,
    val title: String,
    val caption: String = "",
    val locale: String? = null,
)

/** `POST /social/ideas/{id}/save` answer: the id of the new plan (a remix). */
@Serializable
data class SaveIdeaResult(
    @SerialName("plan_id") val planId: Long,
)

/** A comment. */
@Serializable
data class Comment(
    val id: Long,
    val author: HandleRef = HandleRef(""),
    val text: String = "",
    val helpful: Boolean = false,
    val mine: Boolean = false,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val contentStatus: ContentStatus get() = ContentStatus.from(status)
}

/** `GET /social/ideas/{id}/comments` (oldest first). */
@Serializable
data class CommentPage(val comments: List<Comment> = emptyList(), val next: String? = null)

/** `POST /social/ideas/{id}/comments` body (text <= 300). */
@Serializable
data class CommentRequest(val text: String)

/** `POST /social/reports` body. [target] and [reason] are enum wires; [id] is the idea or comment id, or the handle for a profile. */
@Serializable
data class ReportRequest(val target: String, val id: String, val reason: String, val note: String? = null)

/**
 * Machine codes of the social and play routes (the envelope's `error_code`). Unknown or missing is [UNKNOWN]: show the backend
 * `detail`. Codes are additive, so never branch exhaustively without an else.
 */
enum class SocialErrorCode(val wire: String?) {
    HANDLE_TAKEN("handle_taken"),
    HANDLE_INVALID("handle_invalid"),
    HANDLE_RESERVED("handle_reserved"),
    HANDLE_RATE_LIMITED("handle_rate_limited"),
    COUNTRY_INVALID("country_invalid"),
    CONTENT_REJECTED("content_rejected"),
    PROFILE_REQUIRED("profile_required"),
    EMAIL_UNVERIFIED("email_unverified"),
    RESTRICTED("restricted"),
    LIMIT_REACHED("limit_reached"),
    CANNOT_FOLLOW_SELF("cannot_follow_self"),
    CANNOT_BLOCK_SELF("cannot_block_self"),
    CANNOT_CHALLENGE_SELF("cannot_challenge_self"),
    PROFILE_NOT_FOUND("profile_not_found"),
    REQUEST_NOT_FOUND("request_not_found"),
    IDEA_NOT_FOUND("idea_not_found"),
    COMMENT_NOT_FOUND("comment_not_found"),
    PLAN_NOT_FOUND("plan_not_found"),
    NOT_FOUND("not_found"),
    IDEA_UNAVAILABLE("idea_unavailable"),
    TITLE_INVALID("title_invalid"),
    TEXT_INVALID("text_invalid"),
    SNAPSHOT_TOO_LARGE("snapshot_too_large"),
    INVALID_CURSOR("invalid_cursor"),
    SOCIAL_DISABLED("social_disabled"),
    PLAY_DISABLED("play_disabled"),
    GAME_HIDDEN("game_hidden"),
    ALREADY_DONE("already_done"),
    SESSION_NOT_FOUND("session_not_found"),
    SESSION_EXPIRED("session_expired"),
    MATCH_NOT_FOUND("match_not_found"),
    MATCH_EXPIRED("match_expired"),
    INVALID_STATE("invalid_state"),
    INVALID_PACK("invalid_pack"),
    INVALID_MODE("invalid_mode"),
    INVALID_INDEX("invalid_index"),
    INVALID_CHOICE("invalid_choice"),
    OUT_OF_ORDER("out_of_order"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps an `error_code`; null or anything new is [UNKNOWN]. */
        fun from(code: String?): SocialErrorCode = entries.firstOrNull { it.wire != null && it.wire == code } ?: UNKNOWN
    }
}

/** The social or play machine code of this failure ([SocialErrorCode.UNKNOWN] when it is none of ours). */
fun AppError.socialErrorCode(): SocialErrorCode = SocialErrorCode.from(rawErrorCode())

/** The `retry_after` seconds of a `422 handle_rate_limited`, or null. */
fun AppError.retryAfterSeconds(): Long? =
    when (this) {
        is AppError.Client -> extras["retry_after"]?.toDoubleOrNull()?.toLong()
        is AppError.RateLimited -> retryAfterSeconds
        else -> null
    }

private fun AppError.rawErrorCode(): String? =
    when (this) {
        is AppError.NotFound -> code
        is AppError.Client -> code
        is AppError.Server -> code
        is AppError.Forbidden -> code
        is AppError.RateLimited -> code
        is AppError.Unauthorized -> code
        else -> null
    }
