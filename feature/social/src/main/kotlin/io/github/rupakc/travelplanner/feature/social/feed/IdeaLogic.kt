package io.github.rupakc.travelplanner.feature.social.feed

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.social.ContentStatus
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode

/** Server limits: idea title, idea caption and comment text. */
internal const val TITLE_MAX = 80
internal const val CAPTION_MAX = 500
internal const val COMMENT_MAX = 300

/** The idea with its like toggled locally (the count never goes below zero). */
internal fun Idea.withLiked(liked: Boolean): Idea =
    if (liked == this.liked) this else copy(liked = liked, likes = ((likes ?: 0) + if (liked) 1 else -1).coerceAtLeast(0))

/** The idea with its save mark toggled locally. */
internal fun Idea.withSaved(saved: Boolean): Idea =
    if (saved == this.saved) this else copy(saved = saved, saves = ((saves ?: 0) + if (saved) 1 else -1).coerceAtLeast(0))

/** [existing] followed by the [incoming] ideas it does not hold yet (a page that overlaps is not shown twice). */
internal fun mergeIdeas(existing: List<Idea>, incoming: List<Idea>): List<Idea> {
    val known = existing.mapTo(HashSet()) { it.id }
    return existing + incoming.filter { known.add(it.id) }
}

/** Only an idea that is visible to others shows without a notice; `held` (and a hidden or removed own idea) says why. */
internal fun Idea.needsReviewNotice(): Boolean = contentStatus != ContentStatus.PUBLISHED

/** Why a publish was refused, as the screen guides the user. */
internal enum class PublishProblem { ProfileRequired, EmailUnverified, LimitReached, ContentRejected, Restricted, TitleInvalid, TooLarge, PlanMissing }

/** The guiding case of a failed publish, or null for any other failure (show the backend text). */
internal fun AppError.toPublishProblem(): PublishProblem? =
    when (socialErrorCode()) {
        SocialErrorCode.PROFILE_REQUIRED -> PublishProblem.ProfileRequired
        SocialErrorCode.EMAIL_UNVERIFIED -> PublishProblem.EmailUnverified
        SocialErrorCode.LIMIT_REACHED -> PublishProblem.LimitReached
        SocialErrorCode.CONTENT_REJECTED -> PublishProblem.ContentRejected
        SocialErrorCode.RESTRICTED -> PublishProblem.Restricted
        SocialErrorCode.TITLE_INVALID -> PublishProblem.TitleInvalid
        SocialErrorCode.SNAPSHOT_TOO_LARGE -> PublishProblem.TooLarge
        SocialErrorCode.PLAN_NOT_FOUND -> PublishProblem.PlanMissing
        else -> null
    }

/** True when the idea (or comment) is gone for this reader: unknown, hidden, removed or blocked pair all look the same. */
internal fun AppError.isGone(): Boolean =
    when (socialErrorCode()) {
        SocialErrorCode.IDEA_NOT_FOUND, SocialErrorCode.IDEA_UNAVAILABLE, SocialErrorCode.NOT_FOUND, SocialErrorCode.COMMENT_NOT_FOUND -> true
        else -> false
    }
