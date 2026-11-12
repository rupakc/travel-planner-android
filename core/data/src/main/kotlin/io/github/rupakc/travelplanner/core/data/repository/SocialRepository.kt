package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.social.BlockList
import io.github.rupakc.travelplanner.core.model.social.Comment
import io.github.rupakc.travelplanner.core.model.social.CommentPage
import io.github.rupakc.travelplanner.core.model.social.FeedScope
import io.github.rupakc.travelplanner.core.model.social.FollowRequests
import io.github.rupakc.travelplanner.core.model.social.FollowResult
import io.github.rupakc.travelplanner.core.model.social.HandleCheck
import io.github.rupakc.travelplanner.core.model.social.HandlePage
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import io.github.rupakc.travelplanner.core.model.social.IdeaPage
import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileUpdate
import io.github.rupakc.travelplanner.core.model.social.PublicProfile
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.core.model.social.ReportTarget

/**
 * Profiles, follows, blocks, ideas, comments and reports. Callers must have checked `JourneyFlags.social`. Failures carry the
 * server's `error_code`; read it with `AppError.socialErrorCode()`. Nothing is cached: social data is per request.
 */
interface SocialRepository {
    suspend fun ownProfile(): AppResult<OwnProfile>

    /** `PUT /social/profile`; only the fields set in [update] change. `409 handle_taken`, `422 handle_*|content_rejected`. */
    suspend fun saveProfile(update: ProfileUpdate): AppResult<OwnProfile>

    /** `404 profile_not_found` for private, unknown or blocked profiles. */
    suspend fun profile(handle: String): AppResult<PublicProfile>

    suspend fun checkHandle(handle: String): AppResult<HandleCheck>

    suspend fun follow(handle: String): AppResult<FollowResult>

    suspend fun unfollow(handle: String): AppResult<Unit>

    suspend fun followRequests(): AppResult<FollowRequests>

    suspend fun acceptRequest(handle: String): AppResult<Unit>

    suspend fun declineRequest(handle: String): AppResult<Unit>

    suspend fun following(cursor: String? = null): AppResult<HandlePage>

    suspend fun followers(cursor: String? = null): AppResult<HandlePage>

    suspend fun block(handle: String): AppResult<Unit>

    suspend fun unblock(handle: String): AppResult<Unit>

    suspend fun blocks(): AppResult<BlockList>

    /**
     * Publishes (or updates) the idea of a plan. The page language is the UI language. `403 profile_required|email_unverified|restricted`,
     * `422 limit_reached|content_rejected|title_invalid|snapshot_too_large`; the answer's `status` is `held` when it awaits review.
     */
    suspend fun publishIdea(planId: Long, kind: IdeaKind, title: String, caption: String): AppResult<Idea>

    suspend fun feed(scope: FeedScope, cursor: String? = null, limit: Int? = null): AppResult<IdeaPage>

    suspend fun idea(id: Long): AppResult<Idea>

    suspend fun deleteIdea(id: Long): AppResult<Unit>

    suspend fun profileIdeas(handle: String, cursor: String? = null): AppResult<IdeaPage>

    /** Idempotent; no XP. */
    suspend fun setLiked(id: Long, liked: Boolean): AppResult<Unit>

    /** Remix: the idea becomes a new plan of the caller; the result is that plan's id. */
    suspend fun saveIdea(id: Long): AppResult<Long>

    suspend fun unsaveIdea(id: Long): AppResult<Unit>

    suspend fun comments(ideaId: Long, cursor: String? = null): AppResult<CommentPage>

    suspend fun comment(ideaId: Long, text: String): AppResult<Comment>

    suspend fun deleteComment(id: Long): AppResult<Unit>

    /** Idea author only. */
    suspend fun setHelpful(commentId: Long, helpful: Boolean): AppResult<Unit>

    /** [id] is the idea or comment id as text, or the handle for a profile. 204 on success; one report per target. */
    suspend fun report(target: ReportTarget, id: String, reason: ReportReason, note: String? = null): AppResult<Unit>
}
