package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.model.play.AnswerResult
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardWeek
import io.github.rupakc.travelplanner.core.model.play.Challenge
import io.github.rupakc.travelplanner.core.model.play.H2hList
import io.github.rupakc.travelplanner.core.model.play.Leaderboard
import io.github.rupakc.travelplanner.core.model.play.PlayHistory
import io.github.rupakc.travelplanner.core.model.play.PlayState
import io.github.rupakc.travelplanner.core.model.play.QuizMode
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.core.model.play.QuizSession

/** Scriptable [PlayRepository]: set the `*Result` of the call under test; calls are recorded in [calls] as `name(args)`. */
class FakePlayRepository : PlayRepository {
    var stateResult: AppResult<PlayState> = AppResult.Success(PlayState())
    var sessionResult: AppResult<QuizSession> = AppResult.Success(QuizSession(sessionId = "s1", mode = "daily"))
    var answerResult: AppResult<AnswerResult> = AppResult.Success(AnswerResult(correct = true, correctChoice = "a", points = 100))
    var historyResult: AppResult<PlayHistory> = AppResult.Success(PlayHistory())
    var h2hListResult: AppResult<H2hList> = AppResult.Success(H2hList())
    var unitResult: AppResult<Unit> = AppResult.Success(Unit)
    var leaderboardResult: AppResult<Leaderboard> = AppResult.Success(Leaderboard())
    var challengeResult: AppResult<Challenge> = AppResult.Success(Challenge(id = "2026-W46:featured"))

    val calls = mutableListOf<String>()

    private fun <T> rec(call: String, result: T): T = result.also { calls += call }

    override suspend fun state() = rec("state", stateResult)

    override suspend fun startQuiz(mode: QuizMode, pack: QuizPack?) = rec("startQuiz(${mode.wire},${pack?.wire})", sessionResult)

    override suspend fun quiz(sessionId: String) = rec("quiz($sessionId)", sessionResult)

    override suspend fun answer(sessionId: String, index: Int, choice: String) = rec("answer($sessionId,$index,$choice)", answerResult)

    override suspend fun history(limit: Int?) = rec("history($limit)", historyResult)

    override suspend fun createH2h(handle: String, pack: QuizPack) = rec("createH2h($handle,${pack.wire})", sessionResult)

    override suspend fun h2hMatches() = rec("h2hMatches", h2hListResult)

    override suspend fun acceptH2h(matchId: String) = rec("acceptH2h($matchId)", sessionResult)

    override suspend fun declineH2h(matchId: String) = rec("declineH2h($matchId)", unitResult)

    override suspend fun leaderboard(scope: BoardScope, week: BoardWeek) = rec("leaderboard(${scope.wire},${week.wire})", leaderboardResult)

    override suspend fun challenge() = rec("challenge", challengeResult)
}
