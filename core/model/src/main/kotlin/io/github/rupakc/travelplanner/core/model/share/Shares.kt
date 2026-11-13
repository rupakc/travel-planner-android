package io.github.rupakc.travelplanner.core.model.share

import io.github.rupakc.travelplanner.core.common.AppError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a Phase 4 share link covers (`kind` of `POST /shares`). A kind this app does not know is [UNKNOWN]: a plain link. */
enum class ShareKind(val wire: String?) {
    PLAN("plan"),
    RECAP("recap"),
    SAVINGS("savings"),

    /** A finished quiz session (`session_id`): pack and score out of 5, never time or answers. */
    QUIZ("quiz"),

    /** "Day N in Place" of an active trip (`plan_id`). */
    STATUS("status"),
    UNKNOWN(null),
    ;

    /** True when the share needs a `plan_id`. */
    val needsPlan: Boolean get() = this == PLAN || this == RECAP || this == STATUS

    /** True when the share needs a quiz `session_id`. */
    val needsSession: Boolean get() = this == QUIZ

    companion object {
        /** Maps a wire `kind`; null or anything new is [UNKNOWN]. */
        fun from(wire: String?): ShareKind = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** `options.dates`: how much of the trip dates the public page shows. [HIDDEN] is the private default. */
enum class ShareDates(val wire: String) {
    HIDDEN("hidden"),
    MONTH("month"),
    EXACT("exact"),
    ;

    companion object {
        /** Anything other than `month` or `exact` is the private default, like the server. */
        fun from(wire: String?): ShareDates = entries.firstOrNull { it.wire == wire } ?: HIDDEN
    }
}

/** The three share options in wire form; the defaults are the private ones. [dates] is `hidden | month | exact`. */
@Serializable
data class ShareOptions(
    val dates: String = ShareDates.HIDDEN.wire,
    @SerialName("show_origin") val showOrigin: Boolean = false,
    @SerialName("show_name") val showName: Boolean = false,
) {
    /** [dates] as an enum. */
    val datesMode: ShareDates get() = ShareDates.from(dates)

    companion object {
        /** Nothing optional revealed. */
        val PRIVATE = ShareOptions()

        fun of(dates: ShareDates, showOrigin: Boolean, showName: Boolean) = ShareOptions(dates.wire, showOrigin, showName)
    }
}

/** `POST /shares` body. [planId] is required for `plan` and `recap`, ignored for `savings`. */
@Serializable
data class CreateShareRequest(
    val kind: String,
    @SerialName("plan_id") val planId: Long? = null,
    val options: ShareOptions = ShareOptions.PRIVATE,
    val locale: String? = null,
    /** Only for `quiz`: the caller's finished session. Omitted from the body otherwise. */
    @SerialName("session_id") val sessionId: String? = null,
)

/** The two card sizes: `link` 1200x630 (the preview), `story` 1080x1920 (chat apps). Either may be absent on a future server. */
@Serializable
data class ShareCardUrls(val link: String? = null, val story: String? = null)

/** Answer of `POST /shares`: absolute [url] and [cardUrl]s. Same token on every call for the same (kind, plan). */
@Serializable
data class CreatedShare(
    val token: String,
    val kind: String? = null,
    val url: String,
    @SerialName("card_url") val cardUrl: ShareCardUrls = ShareCardUrls(),
    val options: ShareOptions = ShareOptions.PRIVATE,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("plan_id") val planId: Long? = null,
) {
    val shareKind: ShareKind get() = ShareKind.from(kind)
}

/** One row of `GET /shares`; [title] is the plan name (`Price alerts` for savings). */
@Serializable
data class ShareRow(
    val token: String,
    val kind: String? = null,
    val title: String? = null,
    val url: String,
    val options: ShareOptions = ShareOptions.PRIVATE,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("plan_id") val planId: Long? = null,
) {
    val shareKind: ShareKind get() = ShareKind.from(kind)
}

/** A plan that still has an old-style share token (revoked with `DELETE /plans/{id}/share`). */
@Serializable
data class LegacyPlanShare(
    @SerialName("plan_id") val planId: Long,
    val name: String? = null,
    val url: String,
)

/** `GET /shares`: newest first, at most 100, plus the legacy plan shares (absent on an older backend). */
@Serializable
data class SharesList(
    val shares: List<ShareRow> = emptyList(),
    @SerialName("legacy_plan_shares") val legacyPlanShares: List<LegacyPlanShare> = emptyList(),
)

/** Card sizes of `GET /share/{token}/card.png?size=`. */
enum class ShareCardSize(val wire: String) { LINK("link"), STORY("story") }

/** Error codes of the share routes (the `error_code` of the envelope). */
enum class ShareErrorCode(val wire: String?) {
    SHARING_DISABLED("sharing_disabled"),
    PLAN_NOT_FOUND("plan_not_found"),
    INVALID_KIND("invalid_kind"),
    SHARE_NOT_FOUND("share_not_found"),
    CARD_BUSY("card_busy"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps an `error_code`; null or anything new is [UNKNOWN] (show the backend `detail`). */
        fun from(code: String?): ShareErrorCode = entries.firstOrNull { it.wire != null && it.wire == code } ?: UNKNOWN
    }
}

/** The share machine code of this failure ([ShareErrorCode.UNKNOWN] when it is none of ours). */
fun AppError.shareErrorCode(): ShareErrorCode =
    ShareErrorCode.from(
        when (this) {
            is AppError.NotFound -> code
            is AppError.Client -> code
            is AppError.Server -> code
            is AppError.Forbidden -> code
            is AppError.RateLimited -> code
            else -> null
        },
    )
