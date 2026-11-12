package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.social.BlockList
import io.github.rupakc.travelplanner.core.model.social.CommentPage
import io.github.rupakc.travelplanner.core.model.social.CommentRequest
import io.github.rupakc.travelplanner.core.model.social.FollowRequests
import io.github.rupakc.travelplanner.core.model.social.FollowResult
import io.github.rupakc.travelplanner.core.model.social.HandleCheck
import io.github.rupakc.travelplanner.core.model.social.HandlePage
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.IdeaPage
import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileUpdate
import io.github.rupakc.travelplanner.core.model.social.PublicProfile
import io.github.rupakc.travelplanner.core.model.social.PublishIdeaRequest
import io.github.rupakc.travelplanner.core.model.social.ReportRequest
import io.github.rupakc.travelplanner.core.model.social.SaveIdeaResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Profiles, follows, blocks, the ideas feed, comments and reports (docs/Social-API.md; signed in). Only called when
 * `features.social >= 1`; off, the server answers `404 social_disabled`. The admin moderation routes are not here (no admin UI).
 */
interface SocialApi {
    @GET("api/social/profile")
    suspend fun ownProfile(): OwnProfile

    @PUT("api/social/profile")
    suspend fun saveProfile(
        @Body body: ProfileUpdate,
    ): OwnProfile

    @GET("api/social/profile/{handle}")
    suspend fun profile(
        @Path("handle") handle: String,
    ): PublicProfile

    @GET("api/social/handles/check")
    suspend fun checkHandle(
        @Query("handle") handle: String,
    ): HandleCheck

    @PUT("api/social/follow/{handle}")
    suspend fun follow(
        @Path("handle") handle: String,
    ): FollowResult

    @DELETE("api/social/follow/{handle}")
    suspend fun unfollow(
        @Path("handle") handle: String,
    ): Response<Unit>

    @GET("api/social/follow-requests")
    suspend fun followRequests(): FollowRequests

    @POST("api/social/follow-requests/{handle}/accept")
    suspend fun acceptRequest(
        @Path("handle") handle: String,
    ): Response<Unit>

    @POST("api/social/follow-requests/{handle}/decline")
    suspend fun declineRequest(
        @Path("handle") handle: String,
    ): Response<Unit>

    @GET("api/social/following")
    suspend fun following(
        @Query("cursor") cursor: String? = null,
    ): HandlePage

    @GET("api/social/followers")
    suspend fun followers(
        @Query("cursor") cursor: String? = null,
    ): HandlePage

    @PUT("api/social/block/{handle}")
    suspend fun block(
        @Path("handle") handle: String,
    ): Response<Unit>

    @DELETE("api/social/block/{handle}")
    suspend fun unblock(
        @Path("handle") handle: String,
    ): Response<Unit>

    @GET("api/social/blocks")
    suspend fun blocks(): BlockList

    @POST("api/social/ideas")
    suspend fun publishIdea(
        @Body body: PublishIdeaRequest,
    ): Idea

    @GET("api/social/feed")
    suspend fun feed(
        @Query("scope") scope: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): IdeaPage

    @GET("api/social/ideas/{id}")
    suspend fun idea(
        @Path("id") id: Long,
    ): Idea

    @DELETE("api/social/ideas/{id}")
    suspend fun deleteIdea(
        @Path("id") id: Long,
    ): Response<Unit>

    @GET("api/social/profile/{handle}/ideas")
    suspend fun profileIdeas(
        @Path("handle") handle: String,
        @Query("cursor") cursor: String? = null,
    ): IdeaPage

    @PUT("api/social/ideas/{id}/like")
    suspend fun like(
        @Path("id") id: Long,
    ): Response<Unit>

    @DELETE("api/social/ideas/{id}/like")
    suspend fun unlike(
        @Path("id") id: Long,
    ): Response<Unit>

    /** Remix: copies the idea into the caller's own plans. */
    @POST("api/social/ideas/{id}/save")
    suspend fun saveIdea(
        @Path("id") id: Long,
    ): SaveIdeaResult

    @DELETE("api/social/ideas/{id}/save")
    suspend fun unsaveIdea(
        @Path("id") id: Long,
    ): Response<Unit>

    @GET("api/social/ideas/{id}/comments")
    suspend fun comments(
        @Path("id") id: Long,
        @Query("cursor") cursor: String? = null,
    ): CommentPage

    @POST("api/social/ideas/{id}/comments")
    suspend fun comment(
        @Path("id") id: Long,
        @Body body: CommentRequest,
    ): io.github.rupakc.travelplanner.core.model.social.Comment

    @DELETE("api/social/comments/{id}")
    suspend fun deleteComment(
        @Path("id") id: Long,
    ): Response<Unit>

    @PUT("api/social/comments/{id}/helpful")
    suspend fun markHelpful(
        @Path("id") id: Long,
    ): Response<Unit>

    @DELETE("api/social/comments/{id}/helpful")
    suspend fun unmarkHelpful(
        @Path("id") id: Long,
    ): Response<Unit>

    @POST("api/social/reports")
    suspend fun report(
        @Body body: ReportRequest,
    ): Response<Unit>
}
