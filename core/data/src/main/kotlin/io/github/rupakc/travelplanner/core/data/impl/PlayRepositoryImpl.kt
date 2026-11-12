package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.model.play.AnswerRequest
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardWeek
import io.github.rupakc.travelplanner.core.model.play.CreateH2hRequest
import io.github.rupakc.travelplanner.core.model.play.QuizMode
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.core.model.play.StartQuizRequest
import io.github.rupakc.travelplanner.core.network.api.PlayApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class PlayRepositoryImpl
    @Inject
    constructor(
        private val api: PlayApi,
        private val language: AppLanguage,
    ) : PlayRepository {
        override suspend fun state() = apiCall { api.state() }

        override suspend fun startQuiz(mode: QuizMode, pack: QuizPack?) =
            mode.wire?.let { wire ->
                // `h2h` sessions come from the head-to-head routes; the server answers 422 invalid_mode, so it is sent as-is.
                val body = StartQuizRequest(wire, pack?.wire.takeIf { mode == QuizMode.PACK }, language.current())
                apiCall { api.startQuiz(body) }
            } ?: AppResult.Failure(io.github.rupakc.travelplanner.core.common.AppError.Unknown(null))

        override suspend fun quiz(sessionId: String) = apiCall { api.quiz(sessionId) }

        override suspend fun answer(sessionId: String, index: Int, choice: String) = apiCall { api.answer(sessionId, AnswerRequest(index, choice)) }

        override suspend fun history(limit: Int?) = apiCall { api.history(limit) }

        override suspend fun createH2h(handle: String, pack: QuizPack) =
            apiCall { api.createH2h(CreateH2hRequest(handle.trim().removePrefix("@").lowercase(), pack.wire)) }

        override suspend fun h2hMatches() = apiCall { api.h2hMatches() }

        override suspend fun acceptH2h(matchId: String) = apiCall { api.acceptH2h(matchId) }

        override suspend fun declineH2h(matchId: String): AppResult<Unit> =
            apiCall {
                val response = api.declineH2h(matchId)
                if (!response.isSuccessful) throw HttpException(response)
            }

        override suspend fun leaderboard(scope: BoardScope, week: BoardWeek) = apiCall { api.leaderboard(scope.wire, week.wire) }

        override suspend fun challenge() = apiCall { api.challenge() }
    }
