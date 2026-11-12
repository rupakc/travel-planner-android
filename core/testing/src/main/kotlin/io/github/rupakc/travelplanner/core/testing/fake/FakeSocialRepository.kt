package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository
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
 * Scriptable [SocialRepository]: set the `*Result` of the call under test (defaults are empty successes). Calls are recorded in
 * [calls] as `name(args)` strings so tests can assert order without a mock library.
 */
class FakeSocialRepository : SocialRepository {
    var ownProfileResult: AppResult<OwnProfile> = AppResult.Success(OwnProfile())
    var saveProfileResult: AppResult<OwnProfile> = AppResult.Success(OwnProfile(handle = "me"))
    var profileResult: AppResult<PublicProfile> = AppResult.Success(PublicProfile(handle = "someone"))
    var checkHandleResult: AppResult<HandleCheck> = AppResult.Success(HandleCheck(available = true))
    var followResult: AppResult<FollowResult> = AppResult.Success(FollowResult("following"))
    var unitResult: AppResult<Unit> = AppResult.Success(Unit)
    var followRequestsResult: AppResult<FollowRequests> = AppResult.Success(FollowRequests())
    var handlePageResult: AppResult<HandlePage> = AppResult.Success(HandlePage())
    var blocksResult: AppResult<BlockList> = AppResult.Success(BlockList())
    var publishResult: AppResult<Idea> = AppResult.Success(Idea(id = 1, kind = "plan", status = "published"))
    var feedResult: AppResult<IdeaPage> = AppResult.Success(IdeaPage())
    var ideaResult: AppResult<Idea> = AppResult.Success(Idea(id = 1))
    var saveIdeaResult: AppResult<Long> = AppResult.Success(1L)
    var commentsResult: AppResult<CommentPage> = AppResult.Success(CommentPage())
    var commentResult: AppResult<Comment> = AppResult.Success(Comment(id = 1))

    val calls = mutableListOf<String>()
    val savedProfiles = mutableListOf<ProfileUpdate>()

    private fun <T> rec(call: String, result: T): T = result.also { calls += call }

    override suspend fun ownProfile() = rec("ownProfile", ownProfileResult)

    override suspend fun saveProfile(update: ProfileUpdate) = rec("saveProfile", saveProfileResult).also { savedProfiles += update }

    override suspend fun profile(handle: String) = rec("profile($handle)", profileResult)

    override suspend fun checkHandle(handle: String) = rec("checkHandle($handle)", checkHandleResult)

    override suspend fun follow(handle: String) = rec("follow($handle)", followResult)

    override suspend fun unfollow(handle: String) = rec("unfollow($handle)", unitResult)

    override suspend fun followRequests() = rec("followRequests", followRequestsResult)

    override suspend fun acceptRequest(handle: String) = rec("acceptRequest($handle)", unitResult)

    override suspend fun declineRequest(handle: String) = rec("declineRequest($handle)", unitResult)

    override suspend fun following(cursor: String?) = rec("following($cursor)", handlePageResult)

    override suspend fun followers(cursor: String?) = rec("followers($cursor)", handlePageResult)

    override suspend fun block(handle: String) = rec("block($handle)", unitResult)

    override suspend fun unblock(handle: String) = rec("unblock($handle)", unitResult)

    override suspend fun blocks() = rec("blocks", blocksResult)

    override suspend fun publishIdea(planId: Long, kind: IdeaKind, title: String, caption: String) =
        rec("publishIdea($planId,${kind.wire},$title)", publishResult)

    override suspend fun feed(scope: FeedScope, cursor: String?, limit: Int?) = rec("feed(${scope.wire},$cursor)", feedResult)

    override suspend fun idea(id: Long) = rec("idea($id)", ideaResult)

    override suspend fun deleteIdea(id: Long) = rec("deleteIdea($id)", unitResult)

    override suspend fun profileIdeas(handle: String, cursor: String?) = rec("profileIdeas($handle,$cursor)", feedResult)

    override suspend fun setLiked(id: Long, liked: Boolean) = rec("setLiked($id,$liked)", unitResult)

    override suspend fun saveIdea(id: Long) = rec("saveIdea($id)", saveIdeaResult)

    override suspend fun unsaveIdea(id: Long) = rec("unsaveIdea($id)", unitResult)

    override suspend fun comments(ideaId: Long, cursor: String?) = rec("comments($ideaId,$cursor)", commentsResult)

    override suspend fun comment(ideaId: Long, text: String) = rec("comment($ideaId)", commentResult)

    override suspend fun deleteComment(id: Long) = rec("deleteComment($id)", unitResult)

    override suspend fun setHelpful(commentId: Long, helpful: Boolean) = rec("setHelpful($commentId,$helpful)", unitResult)

    override suspend fun report(target: ReportTarget, id: String, reason: ReportReason, note: String?) =
        rec("report(${target.wire},$id,${reason.wire})", unitResult)
}
