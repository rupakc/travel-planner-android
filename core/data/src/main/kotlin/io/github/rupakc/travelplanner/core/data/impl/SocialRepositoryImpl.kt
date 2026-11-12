package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository
import io.github.rupakc.travelplanner.core.model.social.CommentRequest
import io.github.rupakc.travelplanner.core.model.social.FeedScope
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import io.github.rupakc.travelplanner.core.model.social.ProfileUpdate
import io.github.rupakc.travelplanner.core.model.social.PublishIdeaRequest
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.core.model.social.ReportRequest
import io.github.rupakc.travelplanner.core.model.social.ReportTarget
import io.github.rupakc.travelplanner.core.network.api.SocialApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import retrofit2.Response

@Singleton
class SocialRepositoryImpl
    @Inject
    constructor(
        private val api: SocialApi,
        private val language: AppLanguage,
    ) : SocialRepository {
        override suspend fun ownProfile() = apiCall { api.ownProfile() }

        override suspend fun saveProfile(update: ProfileUpdate) = apiCall { api.saveProfile(update) }

        override suspend fun profile(handle: String) = apiCall { api.profile(handle) }

        override suspend fun checkHandle(handle: String) = apiCall { api.checkHandle(handle) }

        override suspend fun follow(handle: String) = apiCall { api.follow(handle) }

        override suspend fun unfollow(handle: String) = unit { api.unfollow(handle) }

        override suspend fun followRequests() = apiCall { api.followRequests() }

        override suspend fun acceptRequest(handle: String) = unit { api.acceptRequest(handle) }

        override suspend fun declineRequest(handle: String) = unit { api.declineRequest(handle) }

        override suspend fun following(cursor: String?) = apiCall { api.following(cursor) }

        override suspend fun followers(cursor: String?) = apiCall { api.followers(cursor) }

        override suspend fun block(handle: String) = unit { api.block(handle) }

        override suspend fun unblock(handle: String) = unit { api.unblock(handle) }

        override suspend fun blocks() = apiCall { api.blocks() }

        override suspend fun publishIdea(planId: Long, kind: IdeaKind, title: String, caption: String) =
            kind.wire?.let { wire -> apiCall { api.publishIdea(PublishIdeaRequest(planId, wire, title, caption, language.current())) } }
                ?: AppResult.Failure(AppError.Unknown(null))

        override suspend fun feed(scope: FeedScope, cursor: String?, limit: Int?) = apiCall { api.feed(scope.wire, cursor, limit) }

        override suspend fun idea(id: Long) = apiCall { api.idea(id) }

        override suspend fun deleteIdea(id: Long) = unit { api.deleteIdea(id) }

        override suspend fun profileIdeas(handle: String, cursor: String?) = apiCall { api.profileIdeas(handle, cursor) }

        override suspend fun setLiked(id: Long, liked: Boolean) = unit { if (liked) api.like(id) else api.unlike(id) }

        override suspend fun saveIdea(id: Long): AppResult<Long> =
            when (val r = apiCall { api.saveIdea(id) }) {
                is AppResult.Success -> AppResult.Success(r.value.planId)
                is AppResult.Failure -> r
            }

        override suspend fun unsaveIdea(id: Long) = unit { api.unsaveIdea(id) }

        override suspend fun comments(ideaId: Long, cursor: String?) = apiCall { api.comments(ideaId, cursor) }

        override suspend fun comment(ideaId: Long, text: String) = apiCall { api.comment(ideaId, CommentRequest(text)) }

        override suspend fun deleteComment(id: Long) = unit { api.deleteComment(id) }

        override suspend fun setHelpful(commentId: Long, helpful: Boolean) =
            unit { if (helpful) api.markHelpful(commentId) else api.unmarkHelpful(commentId) }

        override suspend fun report(target: ReportTarget, id: String, reason: ReportReason, note: String?) =
            unit { api.report(ReportRequest(target.wire, id, reason.wire, note?.takeIf { it.isNotBlank() })) }

        private suspend fun unit(block: suspend () -> Response<Unit>): AppResult<Unit> =
            apiCall {
                val response = block()
                if (!response.isSuccessful) throw HttpException(response)
            }
    }
