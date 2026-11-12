package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.play.AnswerRequest
import io.github.rupakc.travelplanner.core.model.play.AnswerResult
import io.github.rupakc.travelplanner.core.model.play.Challenge
import io.github.rupakc.travelplanner.core.model.play.CreateH2hRequest
import io.github.rupakc.travelplanner.core.model.play.H2hList
import io.github.rupakc.travelplanner.core.model.play.Leaderboard
import io.github.rupakc.travelplanner.core.model.play.PlayHistory
import io.github.rupakc.travelplanner.core.model.play.PlayState
import io.github.rupakc.travelplanner.core.model.play.QuizSession
import io.github.rupakc.travelplanner.core.model.play.StartQuizRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Quizzes, head-to-head, leaderboards and the weekly challenge (docs/Social-API.md; signed in). Only called when
 * `features.play >= 1` and the game is visible: off, `404 play_disabled`; `hide_game`, `403 game_hidden`. No AI call is behind any route.
 */
interface PlayApi {
    @GET("api/play/state")
    suspend fun state(): PlayState

    /** Starts or resumes a quiz. `409 already_done` for a finished daily or challenge. */
    @POST("api/play/quiz")
    suspend fun startQuiz(
        @Body body: StartQuizRequest,
    ): QuizSession

    @GET("api/play/quiz/{id}")
    suspend fun quiz(
        @Path("id") sessionId: String,
    ): QuizSession

    /** The server checks and times the answer; `410 session_expired` after 20 idle minutes. */
    @POST("api/play/quiz/{id}/answer")
    suspend fun answer(
        @Path("id") sessionId: String,
        @Body body: AnswerRequest,
    ): AnswerResult

    @GET("api/play/history")
    suspend fun history(
        @Query("limit") limit: Int? = null,
    ): PlayHistory

    @POST("api/play/h2h")
    suspend fun createH2h(
        @Body body: CreateH2hRequest,
    ): QuizSession

    @GET("api/play/h2h")
    suspend fun h2hMatches(): H2hList

    @POST("api/play/h2h/{id}/accept")
    suspend fun acceptH2h(
        @Path("id") matchId: String,
    ): QuizSession

    @POST("api/play/h2h/{id}/decline")
    suspend fun declineH2h(
        @Path("id") matchId: String,
    ): Response<Unit>

    @GET("api/play/leaderboard")
    suspend fun leaderboard(
        @Query("scope") scope: String,
        @Query("week") week: String,
    ): Leaderboard

    @GET("api/play/challenge")
    suspend fun challenge(): Challenge
}
